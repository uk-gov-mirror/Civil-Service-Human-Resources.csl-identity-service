package uk.gov.cabinetoffice.csl.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.cabinetoffice.csl.domain.EmailUpdate;
import uk.gov.cabinetoffice.csl.domain.Identity;
import uk.gov.cabinetoffice.csl.dto.AgencyToken;
import uk.gov.cabinetoffice.csl.exception.ResourceNotFoundException;
import uk.gov.cabinetoffice.csl.factory.EmailUpdateFactory;
import uk.gov.cabinetoffice.csl.repository.EmailUpdateRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static java.time.LocalDateTime.now;
import static java.time.temporal.ChronoUnit.MILLIS;
import static uk.gov.cabinetoffice.csl.domain.EmailUpdateStatus.*;

@Slf4j
@Service
@Transactional
public class EmailUpdateService {

    private final EmailUpdateRepository emailUpdateRepository;
    private final EmailUpdateFactory emailUpdateFactory;
    private final IdentityService identityService;
    private final CsrsService csrsService;
    private final CSLService cslService;
    private final Clock clock;
    private final NotifyService notifyService;
    private final int validityInSeconds;
    private final long durationAfterEmailUpdateAllowedInSeconds;

    @Value("${govNotify.template.emailUpdate}")
    private String updateEmailTemplateId;

    @Value("${emailUpdate.urlFormat}")
    private String inviteUrlFormat;

    public EmailUpdateService(EmailUpdateRepository emailUpdateRepository, EmailUpdateFactory emailUpdateFactory,
                              IdentityService identityService, CsrsService csrsService, CSLService cslService, Clock clock,
                              @Qualifier("notifyServiceImpl") NotifyService notifyService,
                              @Value("${emailUpdate.validityInSeconds}") int validityInSeconds,
                              @Value("${emailUpdate.durationAfterEmailUpdateAllowedInSeconds}")
                              long durationAfterEmailUpdateAllowedInSeconds) {
        this.emailUpdateRepository = emailUpdateRepository;
        this.emailUpdateFactory = emailUpdateFactory;
        this.identityService = identityService;
        this.csrsService = csrsService;
        this.cslService = cslService;
        this.clock = clock;
        this.notifyService = notifyService;
        this.validityInSeconds = validityInSeconds;
        this.durationAfterEmailUpdateAllowedInSeconds = durationAfterEmailUpdateAllowedInSeconds;
    }

    public boolean isEmailUpdateExpired(EmailUpdate emailUpdate) {
        if (emailUpdate.getEmailUpdateStatus().equals(EXPIRED) ||
                emailUpdate.getEmailUpdateStatus().equals(UPDATED)) {
            return true;
        }

        if (emailUpdate.getEmailUpdateStatus().equals(PENDING)) {
            long diffInMs = MILLIS.between(emailUpdate.getRequestedAt(), LocalDateTime.now(clock));
            if (diffInMs >= validityInSeconds * 1000L) {
                emailUpdate.setEmailUpdateStatus(EXPIRED);
                emailUpdateRepository.save(emailUpdate);
                return true;
            }
        }
        return false;
    }

    public boolean saveEmailUpdateAndNotify(Identity identity, String newEmail) {
        EmailUpdate emailUpdate = null;

        List<EmailUpdate> pendingEmailUpdates = emailUpdateRepository
                .findByNewEmailIgnoreCaseAndPreviousEmailIgnoreCaseAndEmailUpdateStatus(
                        newEmail, identity.getEmail(), PENDING);

        if (pendingEmailUpdates != null && pendingEmailUpdates.size() > 1) {
            pendingEmailUpdates.forEach(r -> r.setEmailUpdateStatus(EXPIRED));
            emailUpdateRepository.saveAll(pendingEmailUpdates);
        }

        if (pendingEmailUpdates != null && pendingEmailUpdates.size() == 1) {
            emailUpdate = pendingEmailUpdates.get(0);
            if (isEmailUpdateExpired(emailUpdate)) {
                emailUpdate = emailUpdateFactory.create(identity, newEmail);
            } else {
                long diffInMs = MILLIS.between(emailUpdate.getRequestedAt(), LocalDateTime.now(clock));
                if (diffInMs < durationAfterEmailUpdateAllowedInSeconds * 1000L) {
                    return false;
                } else {
                    emailUpdate.setRequestedAt(now(clock));
                }
            }
        }

        if (emailUpdate == null) {
            emailUpdate = emailUpdateFactory.create(identity, newEmail);
        }

        emailUpdateRepository.save(emailUpdate);
        notifyService.notify(newEmail, emailUpdate.getCode(), updateEmailTemplateId, inviteUrlFormat, validityInSeconds);
        return true;
    }

    public boolean isEmailUpdateRequestExistsForCode(String code) {
        return emailUpdateRepository.existsByCode(code);
    }

    public EmailUpdate getEmailUpdateRequestForCode(String code) {
        return emailUpdateRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Email update entry not found in database"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateEmailAddress(EmailUpdate emailUpdate) {
        updateEmailAddress(emailUpdate, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateEmailAddress(EmailUpdate emailUpdate, AgencyToken agencyToken) {
        Identity emailUpdateIdentity = emailUpdate.getIdentity();
        Identity existingIdentity = identityService.getIdentityForEmail(emailUpdateIdentity.getEmail());
        String existingEmail = existingIdentity.getEmail();

        String newEmail = emailUpdate.getNewEmail();

        log.debug("Updating email address for: oldEmail = {}, newEmail = {}", existingEmail, newEmail);
        identityService.updateEmailAddress(existingIdentity, newEmail, agencyToken);
        csrsService.removeOrganisationalUnitFromCivilServant(emailUpdate.getIdentity().getUid());
        log.debug("Updated email address for: oldEmail = {}, newEmail = {}", existingEmail, newEmail);

        emailUpdate.setUpdatedAt(now(clock));
        emailUpdate.setEmailUpdateStatus(UPDATED);
        log.debug("Saving the emailUpdate in DB: {}", emailUpdate);
        emailUpdateRepository.save(emailUpdate);
        log.debug("emailUpdate is saved in DB: {}", emailUpdate);

        String uid = existingIdentity.getUid();
        log.info("Updating Email {} in reporting database for user {}", newEmail, uid);
        cslService.updateEmail(uid, newEmail);
        log.info("Email {} updated in reporting database for user {}", newEmail, uid);

        log.info("Email address {} has been updated to {} successfully", existingEmail, newEmail);
    }
}

package uk.gov.cabinetoffice.csl.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.cabinetoffice.csl.domain.Invite;
import uk.gov.cabinetoffice.csl.domain.InviteStatus;
import uk.gov.cabinetoffice.csl.factory.InviteFactory;
import uk.gov.cabinetoffice.csl.repository.InviteRepository;

import java.time.Clock;
import java.util.Optional;

import static java.time.LocalDateTime.now;
import static java.time.temporal.ChronoUnit.SECONDS;
import static uk.gov.cabinetoffice.csl.domain.InviteStatus.ACCEPTED;
import static uk.gov.cabinetoffice.csl.domain.InviteStatus.EXPIRED;

@Slf4j
@Service
@Transactional
public class InviteService {

    private final String govNotifyInviteTemplateId;
    private final int validityInSeconds;
    private final String signupUrlFormat;
    private final NotifyService notifyService;
    private final InviteRepository inviteRepository;
    private final InviteFactory inviteFactory;
    private final Clock clock;

    public InviteService(
            @Value("${govNotify.template.invite}") String govNotifyInviteTemplateId,
            @Value("${invite.validityInSeconds}") int validityInSeconds,
            @Value("${invite.url}") String signupUrlFormat,
            @Qualifier("notifyServiceImpl") NotifyService notifyService,
            @Qualifier("inviteRepository") InviteRepository inviteRepository,
            InviteFactory inviteFactory,
            Clock clock) {
        this.govNotifyInviteTemplateId = govNotifyInviteTemplateId;
        this.validityInSeconds = validityInSeconds;
        this.signupUrlFormat = signupUrlFormat;
        this.notifyService = notifyService;
        this.inviteRepository = inviteRepository;
        this.inviteFactory = inviteFactory;
        this.clock = clock;
    }

    public void sendSelfSignupInvite(String email, boolean isAuthorisedInvite) {
        Invite invite = inviteFactory.createSelfSignUpInvite(email);
        invite.setAuthorisedInvite(isAuthorisedInvite);
        inviteRepository.save(invite);
        notifyService.notify(invite.getForEmail(), invite.getCode(), govNotifyInviteTemplateId, signupUrlFormat, validityInSeconds);
    }

    public void authoriseAndSaveInvite(Invite invite) {
        invite.setAuthorisedInvite(true);
        inviteRepository.save(invite);
    }

    public void updateInviteStatus(String code, InviteStatus newStatus) {
        Invite invite = inviteRepository.findByCode(code);
        invite.setStatus(newStatus);
        if (InviteStatus.ACCEPTED.equals(newStatus)) {
            invite.setAcceptedAt(now(clock));
        }
        inviteRepository.save(invite);
    }

    @ReadOnlyProperty
    public Invite getInviteForCode(String code) {
        return inviteRepository.findByCode(code);
    }

    public Invite getValidInviteForCode(String code) {
        if (isInviteCodeValid(code)) {
            return inviteRepository.findByCode(code);
        }
        return null;
    }

    @ReadOnlyProperty
    public Optional<Invite> getInviteForEmailAndStatus(String email, InviteStatus status) {
        return inviteRepository.findByForEmailIgnoreCaseAndStatus(email, status);
    }

    public boolean isInviteCodeExpired(String code) {
        Invite invite = inviteRepository.findByCode(code);
        if (invite == null) {
            log.info("Invite not found for code: {}", code);
            return true;
        }
        return isInviteExpired(invite);
    }

    public boolean isInviteExpired(Invite invite) {
        long diffInSeconds = SECONDS.between(invite.getInvitedAt(), now(clock));
        return invite.getStatus().equals(EXPIRED)
                || diffInSeconds > validityInSeconds;
    }

    public boolean isInviteCodeValid(String code) {
        return !isInviteCodeExpired(code) && !isInviteCodeUsed(code);
    }

    public boolean isInviteCodeUsed(String code) {
        Invite invite = inviteRepository.findByCode(code);
        if (invite != null) {
            return invite.getStatus().equals(ACCEPTED);
        }
        return false;
    }

    public boolean isInviteCodeExists(String code) {
        return inviteRepository.existsByCode(code);
    }

    public boolean isEmailInvited(String email) {
        return inviteRepository.existsByForEmailIgnoreCaseAndInviterIdIsNotNull(email);
    }
}

package uk.gov.cabinetoffice.csl.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.cabinetoffice.csl.domain.Reset;
import uk.gov.cabinetoffice.csl.repository.ResetRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static java.time.LocalDateTime.now;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.apache.commons.lang3.RandomStringUtils.random;
import static uk.gov.cabinetoffice.csl.domain.ResetStatus.*;

@Service
@Transactional
public class ResetService {

    @Value("${govNotify.template.reset}")
    private String govNotifyResetTemplateId;

    @Value("${govNotify.template.resetSuccessful}")
    private String govNotifySuccessfulResetTemplateId;

    @Value("${reset.url}")
    private String resetUrlFormat;

    private final int validityInSeconds;

    private final Clock clock;

    private final ResetRepository resetRepository;

    private final NotifyService notifyService;

    @Autowired
    public ResetService(ResetRepository resetRepository,
                        @Qualifier("notifyServiceImpl") NotifyService notifyService,
                        Clock clock,
                        @Value("${reset.validityInSeconds}") int validityInSeconds) {
        this.resetRepository = resetRepository;
        this.notifyService = notifyService;
        this.clock = clock;
        this.validityInSeconds = validityInSeconds;
    }

    public boolean isResetComplete(Reset reset) {
        return reset.getResetStatus().equals(RESET);
    }

    public boolean isResetExpired(Reset reset) {
        if (reset.getResetStatus().equals(EXPIRED) || isResetComplete(reset)) {
            return true;
        }

        if (reset.getResetStatus().equals(PENDING)) {
            long diffInMs = MILLIS.between(reset.getRequestedAt(), LocalDateTime.now(clock));
            if (diffInMs > validityInSeconds * 1000L) {
                reset.setResetStatus(EXPIRED);
                resetRepository.save(reset);
                return true;
            }
        }
        return false;
    }

    public Reset getResetForCode(String code) {
        return resetRepository.findByCode(code);
    }

    public Reset getPendingResetForEmail(String email) {
        Reset reset = null;
        List<Reset> existingPendingResets =
                resetRepository.findByEmailIgnoreCaseAndResetStatus(email, PENDING);

        if (existingPendingResets != null && existingPendingResets.size() > 1) {
            existingPendingResets.forEach(r -> r.setResetStatus(EXPIRED));
            resetRepository.saveAll(existingPendingResets);
            return null;
        }

        if (existingPendingResets != null && existingPendingResets.size() == 1) {
            reset = existingPendingResets.get(0);
            if (isResetExpired(reset)) {
                return null;
            }
        }
        return reset;
    }

    public void createPendingResetRequestAndAndNotifyUser(String email) {
        Reset reset = new Reset(random(40, true, true), email, PENDING, now(clock));
        resetRepository.save(reset);
        notifyService.notify(reset.getEmail(), reset.getCode(), govNotifyResetTemplateId, resetUrlFormat, validityInSeconds);
    }

    public void updatePendingResetRequestAndAndNotifyUser(Reset pendingReset) {
        pendingReset.setRequestedAt(now(clock));
        resetRepository.save(pendingReset);
        notifyService.notify(pendingReset.getEmail(), pendingReset.getCode(), govNotifyResetTemplateId, resetUrlFormat);
    }

    public void notifyUserForSuccessfulReset(Reset reset) {
        reset.setResetAt(now(clock));
        reset.setResetStatus(RESET);
        resetRepository.save(reset);
        notifyService.notify(reset.getEmail(), reset.getCode(), govNotifySuccessfulResetTemplateId, resetUrlFormat);
    }
}

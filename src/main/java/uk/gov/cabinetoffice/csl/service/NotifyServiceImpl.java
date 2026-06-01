package uk.gov.cabinetoffice.csl.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.cabinetoffice.csl.exception.NotificationException;
import uk.gov.cabinetoffice.csl.util.Utils;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@Transactional
public class NotifyServiceImpl implements NotifyService {

    private static final String EMAIL_PERMISSION = "email";
    private static final String ACTIVATION_URL_PERMISSION = "activationUrl";
    private static final String VALIDITY_IN_SECONDS = "linkValidity";

    private final NotificationClient notificationClient;
    private final Utils utils;

    public NotifyServiceImpl(NotificationClient notificationClient, Utils utils) {
        this.notificationClient = notificationClient;
        this.utils = utils;
    }

    private Map<String, String> getGenericPersonalisation(String email, String activationUrl) {
        HashMap<String, String> personalisation = new HashMap<>();
        personalisation.put(EMAIL_PERMISSION, email);
        personalisation.put(ACTIVATION_URL_PERMISSION, activationUrl);
        return personalisation;
    }

    @Override
    public void notify(String email, String code, String templateId, String actionUrl, long validityInSeconds) throws NotificationException {
        String activationUrl = String.format(actionUrl, code);
        Map<String, String> personalisation = getGenericPersonalisation(email, activationUrl);
        personalisation.put(VALIDITY_IN_SECONDS, utils.convertSecondsIntoDaysHoursMinutesSeconds(validityInSeconds));
        notifyWithPersonalisation(email, templateId, personalisation);
    }

    @Override
    public void notify(String email, String code, String templateId, String actionUrl) throws NotificationException {
        String activationUrl = String.format(actionUrl, code);
        Map<String, String> personalisation = getGenericPersonalisation(email, activationUrl);
        notifyWithPersonalisation(email, templateId, personalisation);
    }

    @Override
    public void notify(String email, String templateId) {
        try {
            SendEmailResponse response =
                    notificationClient.sendEmail(templateId, email, Collections.emptyMap(), null);
            log.info("Update password notification sent to: {}", response.getBody());
        } catch (NotificationClientException e) {
            throw new NotificationException(e);
        }
    }

    @Override
    public void notifyWithPersonalisation(String email, String templateId, Map<String, String> personalisation) {
        try {
            SendEmailResponse response =
                    notificationClient.sendEmail(templateId, email, personalisation, null);
            log.info("Email notification sent to {}, {}", email, response.getBody());
        } catch (NotificationClientException e) {
            throw new NotificationException(e);
        }
    }
}

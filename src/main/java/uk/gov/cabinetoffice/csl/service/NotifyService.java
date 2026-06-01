package uk.gov.cabinetoffice.csl.service;

import uk.gov.cabinetoffice.csl.exception.NotificationException;

import java.util.Map;

public interface NotifyService {

    void notify(String email, String code, String templateId, String actionUrl, long validityInSeconds) throws NotificationException;

    void notify(String email, String code, String templateId, String actionUrl) throws NotificationException;

    void notify(String email, String templateId);

    void notifyWithPersonalisation(String email, String templateId, Map<String, String> personalisation);
}

package uk.gov.cabinetoffice.csl.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.cabinetoffice.csl.exception.NotificationException;
import uk.gov.cabinetoffice.csl.util.Utils;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public class NotifyServiceTest {

    private NotificationClient notificationClient;

    private NotifyService notifyService;

    private Utils utils;

    @BeforeEach
    public void setUp() {
        utils = mock(Utils.class);
        notificationClient = mock(NotificationClient.class);
        notifyService = new NotifyServiceImpl(notificationClient, utils);
        ReflectionTestUtils.setField(notifyService, "notificationClient", notificationClient);
    }

    @Test
    public void shouldSendNotificationWithEmailAddressAndTemplateId() throws NotificationClientException {
        String email = "learner@domain.com";
        String templateId = "template-id";
        String body = "response-body";

        SendEmailResponse response = mock(SendEmailResponse.class);
        when(response.getBody()).thenReturn(body);

        when(notificationClient.sendEmail(templateId, email, Collections.emptyMap(), null)).thenReturn(response);

        notifyService.notify(email, templateId);

        verify(notificationClient).sendEmail(templateId, email, Collections.emptyMap(), null);
    }

    @Test
    public void shouldThrowNotificationException() throws NotificationClientException {
        String email = "learner@domain.com";
        String templateId = "template-id";

        NotificationClientException exception = mock(NotificationClientException.class);

        doThrow(exception).when(notificationClient).sendEmail(templateId, email, Collections.emptyMap(), null);

        try {
            notifyService.notify(email, templateId);
            fail("Expected NotificationException");
        } catch (NotificationException e) {
            assertEquals(exception, e.getCause());
        }
    }
}

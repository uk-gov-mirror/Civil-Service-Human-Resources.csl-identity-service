package uk.gov.cabinetoffice.csl.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.cabinetoffice.csl.domain.EmailUpdate;
import uk.gov.cabinetoffice.csl.domain.Identity;
import uk.gov.cabinetoffice.csl.domain.Role;
import uk.gov.cabinetoffice.csl.dto.AgencyToken;
import uk.gov.cabinetoffice.csl.repository.EmailUpdateRepository;

import java.time.Clock;
import java.util.*;

import static java.time.LocalDateTime.now;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static uk.gov.cabinetoffice.csl.domain.EmailUpdateStatus.*;

@SpringBootTest
@ActiveProfiles("no-redis")
@Transactional
public class EmailUpdateServiceTest {

    private static final String EMAIL = "test@example.com";
    private static final String NEW_EMAIL_ADDRESS = "new@newexample.com";
    private static final String CODE = "abc123";
    private static final String UID = "uid123";
    private static final String PASSWORD = "password";
    private static final Set<Role> ROLES = new HashSet<>();
    private static final Identity IDENTITY = new Identity(UID, EMAIL, PASSWORD, true, false, ROLES,
            now(), false, 0);

    @MockBean
    private EmailUpdateRepository emailUpdateRepository;

    @MockBean
    private IdentityService identityService;

    @MockBean
    private CsrsService csrsService;

    @MockBean
    private CSLService cslService;

    @MockBean
    private NotifyService notifyService;

    @Captor
    private ArgumentCaptor<Identity> identityArgumentCaptor;

    @Autowired
    private EmailUpdateService emailUpdateService;

    @Autowired
    private Clock clock;

    @Test
    public void givenAPendingEmailUpdate_thenIsEmailUpdateExpiredShouldReturnFalse() {
        assertFalse(emailUpdateService.isEmailUpdateExpired(createPendingEmailUpdate()));
    }

    @Test
    public void givenAExpiredEmailUpdate_thenIsEmailUpdateExpiredShouldReturnTrue() {
        EmailUpdate emailUpdate = createPendingEmailUpdate();
        emailUpdate.setEmailUpdateStatus(EXPIRED);
        assertTrue(emailUpdateService.isEmailUpdateExpired(emailUpdate));
    }

    @Test
    public void givenAUpdatedEmailUpdate_thenIsEmailUpdateExpiredShouldReturnTrue() {
        EmailUpdate emailUpdate = createPendingEmailUpdate();
        emailUpdate.setEmailUpdateStatus(UPDATED);
        assertTrue(emailUpdateService.isEmailUpdateExpired(emailUpdate));
    }

    @Test
    public void givenAPendingEmailUpdateOlderThanValidityDuration_thenIsEmailUpdateExpiredShouldReturnTrue() {
        EmailUpdate emailUpdate = createPendingEmailUpdate();
        emailUpdate.setRequestedAt(emailUpdate.getRequestedAt().minusSeconds(86400));
        assertTrue(emailUpdateService.isEmailUpdateExpired(emailUpdate));
    }

    @Test
    public void giveMultiplePendingEmailUpdateExist_thenSaveEmailUpdateAndNotifyShouldReturnTrue() {
        List<EmailUpdate> pendingEmailUpdates = new ArrayList<>();
        pendingEmailUpdates.add(createPendingEmailUpdate());
        pendingEmailUpdates.add(createPendingEmailUpdate());
        when(emailUpdateRepository.findByNewEmailIgnoreCaseAndPreviousEmailIgnoreCaseAndEmailUpdateStatus(
                NEW_EMAIL_ADDRESS, IDENTITY.getEmail(), PENDING)).thenReturn(pendingEmailUpdates);
        when(emailUpdateRepository.saveAll(pendingEmailUpdates)).thenReturn(pendingEmailUpdates);
        when(emailUpdateRepository.save(any())).thenReturn(createPendingEmailUpdate());
        doNothing().when(notifyService).notifyWithPersonalisation(eq(NEW_EMAIL_ADDRESS), any(), any());

        assertTrue(emailUpdateService.saveEmailUpdateAndNotify(IDENTITY, NEW_EMAIL_ADDRESS));
        verify(emailUpdateRepository, times(1))
                .findByNewEmailIgnoreCaseAndPreviousEmailIgnoreCaseAndEmailUpdateStatus(
                        NEW_EMAIL_ADDRESS, IDENTITY.getEmail(), PENDING);
        verify(emailUpdateRepository, times(1)).saveAll(pendingEmailUpdates);
        verify(emailUpdateRepository, times(1)).save(any());
        verify(notifyService, times(1))
                .notify(eq(NEW_EMAIL_ADDRESS), any(), any(), any(), eq(86400L));
    }

    @Test
    public void giveOneExpiredEmailUpdateExists_thenSaveEmailUpdateAndNotifyShouldReturnTrue() {
        EmailUpdate emailUpdate = createPendingEmailUpdate();
        emailUpdate.setRequestedAt(emailUpdate.getRequestedAt().minusSeconds(86400));
        List<EmailUpdate> pendingEmailUpdates = new ArrayList<>();
        pendingEmailUpdates.add(emailUpdate);
        when(emailUpdateRepository.findByNewEmailIgnoreCaseAndPreviousEmailIgnoreCaseAndEmailUpdateStatus(
                NEW_EMAIL_ADDRESS, IDENTITY.getEmail(), PENDING)).thenReturn(pendingEmailUpdates);
        when(emailUpdateRepository.save(any())).thenReturn(createPendingEmailUpdate());
        doNothing().when(notifyService).notifyWithPersonalisation(eq(NEW_EMAIL_ADDRESS), any(), any());

        assertTrue(emailUpdateService.saveEmailUpdateAndNotify(IDENTITY, NEW_EMAIL_ADDRESS));
        verify(emailUpdateRepository, times(1))
                .findByNewEmailIgnoreCaseAndPreviousEmailIgnoreCaseAndEmailUpdateStatus(
                        NEW_EMAIL_ADDRESS, IDENTITY.getEmail(), PENDING);
        verify(emailUpdateRepository, times(2)).save(any());
        verify(notifyService, times(1))
                .notify(eq(NEW_EMAIL_ADDRESS), any(), any(), any(), eq(86400L));
    }

    @Test
    public void givenAPendingEmailUpdateOlderThanValidityDurationExists_thenSaveEmailUpdateAndNotifyShouldReturnTrue() {
        EmailUpdate emailUpdate = createPendingEmailUpdate();
        emailUpdate.setRequestedAt(emailUpdate.getRequestedAt().minusSeconds(3600));
        List<EmailUpdate> pendingEmailUpdates = new ArrayList<>();
        pendingEmailUpdates.add(emailUpdate);
        when(emailUpdateRepository.findByNewEmailIgnoreCaseAndPreviousEmailIgnoreCaseAndEmailUpdateStatus(
                NEW_EMAIL_ADDRESS, IDENTITY.getEmail(), PENDING)).thenReturn(pendingEmailUpdates);
        when(emailUpdateRepository.save(any())).thenReturn(createPendingEmailUpdate());
        doNothing().when(notifyService).notifyWithPersonalisation(eq(NEW_EMAIL_ADDRESS), any(), any());

        assertTrue(emailUpdateService.saveEmailUpdateAndNotify(IDENTITY, NEW_EMAIL_ADDRESS));
        verify(emailUpdateRepository, times(1))
                .findByNewEmailIgnoreCaseAndPreviousEmailIgnoreCaseAndEmailUpdateStatus(
                        NEW_EMAIL_ADDRESS, IDENTITY.getEmail(), PENDING);
        verify(emailUpdateRepository, times(1)).save(any());
        verify(notifyService, times(1))
                .notify(eq(NEW_EMAIL_ADDRESS), any(), any(), any(), eq(86400L));
    }

    @Test
    public void giveOnePendingEmailUpdateExists_thenSaveEmailUpdateAndNotifyShouldReturnFalse() {
        List<EmailUpdate> pendingEmailUpdates = new ArrayList<>();
        pendingEmailUpdates.add(createPendingEmailUpdate());
        when(emailUpdateRepository.findByNewEmailIgnoreCaseAndPreviousEmailIgnoreCaseAndEmailUpdateStatus(
                NEW_EMAIL_ADDRESS, IDENTITY.getEmail(), PENDING)).thenReturn(pendingEmailUpdates);
        when(emailUpdateRepository.save(any())).thenReturn(createPendingEmailUpdate());
        doNothing().when(notifyService).notifyWithPersonalisation(eq(NEW_EMAIL_ADDRESS), any(), any());

        assertFalse(emailUpdateService.saveEmailUpdateAndNotify(IDENTITY, NEW_EMAIL_ADDRESS));
        verify(emailUpdateRepository, times(1))
                .findByNewEmailIgnoreCaseAndPreviousEmailIgnoreCaseAndEmailUpdateStatus(
                        NEW_EMAIL_ADDRESS, IDENTITY.getEmail(), PENDING);
        verify(notifyService, never())
                .notifyWithPersonalisation(eq(NEW_EMAIL_ADDRESS), any(), any());
    }

    @Test
    public void givenAValidCodeForIdentity_whenVerifyCode_thenReturnsTrue() {
        when(emailUpdateRepository.existsByCode(anyString())).thenReturn(true);
        assertTrue(emailUpdateService.isEmailUpdateRequestExistsForCode("co"));
        verify(emailUpdateRepository, times(1)).existsByCode(eq("co"));
    }

    @Test
    public void givenAInvalidCodeForIdentity_whenVerifyCode_thenReturnsFalse() {
        when(emailUpdateRepository.existsByCode(anyString())).thenReturn(false);
        assertFalse(emailUpdateService.isEmailUpdateRequestExistsForCode("co"));
        verify(emailUpdateRepository, times(1)).existsByCode(eq("co"));
    }

    @Test
    public void givenAValidIdentity_whenNewDomainAllowListedAndNotAgency_shouldReturnSuccessfully() {
        when(identityService.getIdentityForEmail(EMAIL)).thenReturn(IDENTITY);
        doNothing().when(identityService).updateEmailAddress(eq(IDENTITY), eq(NEW_EMAIL_ADDRESS), isNull());
        doNothing().when(cslService).updateEmail(eq(IDENTITY.getUid()), eq(NEW_EMAIL_ADDRESS));

        emailUpdateService.updateEmailAddress(createPendingEmailUpdate());

        verify(csrsService, times(1)).removeOrganisationalUnitFromCivilServant(any());
        verify(identityService, times(1)).updateEmailAddress(identityArgumentCaptor.capture(),
                eq(NEW_EMAIL_ADDRESS), isNull());
        verify(cslService, times(1)).updateEmail(IDENTITY.getUid(), NEW_EMAIL_ADDRESS);

        Identity identityArgumentCaptorValue = identityArgumentCaptor.getValue();
        assertThat(identityArgumentCaptorValue.getEmail(), equalTo(EMAIL));
    }

    @Test
    public void givenAValidIdentity_whenNewDomainIsAgency_shouldReturnSuccessfully() {
        AgencyToken agencyToken = new AgencyToken();
        agencyToken.setUid(UID);

        when(identityService.getIdentityForEmail(EMAIL)).thenReturn(IDENTITY);
        doNothing().when(identityService).updateEmailAddress(eq(IDENTITY), eq(NEW_EMAIL_ADDRESS), eq(agencyToken));
        doNothing().when(cslService).updateEmail(eq(IDENTITY.getUid()), eq(NEW_EMAIL_ADDRESS));

        emailUpdateService.updateEmailAddress(createPendingEmailUpdate(), agencyToken);

        verify(csrsService, times(1)).removeOrganisationalUnitFromCivilServant(any());
        verify(identityService, times(1)).updateEmailAddress(identityArgumentCaptor.capture(),
                eq(NEW_EMAIL_ADDRESS), eq(agencyToken));
        verify(cslService, times(1)).updateEmail(IDENTITY.getUid(), NEW_EMAIL_ADDRESS);

        Identity identityArgumentCaptorValue = identityArgumentCaptor.getValue();
        assertThat(identityArgumentCaptorValue.getEmail(), equalTo(EMAIL));
    }

    @Test
    public void shouldGetEmailUpdate() {
        EmailUpdate emailUpdate = createPendingEmailUpdate();
        when(emailUpdateRepository.findByCode(anyString())).thenReturn(Optional.of(emailUpdate));
        assertEquals(emailUpdateService.getEmailUpdateRequestForCode(CODE), emailUpdate);
    }

    private EmailUpdate createPendingEmailUpdate() {
        EmailUpdate emailUpdate = new EmailUpdate();
        emailUpdate.setId(100L);
        emailUpdate.setCode(CODE);
        emailUpdate.setPreviousEmail(EMAIL);
        emailUpdate.setNewEmail(NEW_EMAIL_ADDRESS);
        emailUpdate.setIdentity(IDENTITY);
        emailUpdate.setEmailUpdateStatus(PENDING);
        emailUpdate.setRequestedAt(now(clock));
        return emailUpdate;
    }
}

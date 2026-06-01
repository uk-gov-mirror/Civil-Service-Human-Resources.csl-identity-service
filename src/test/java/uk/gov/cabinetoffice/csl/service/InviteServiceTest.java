package uk.gov.cabinetoffice.csl.service;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.cabinetoffice.csl.domain.Invite;
import uk.gov.cabinetoffice.csl.domain.InviteStatus;
import uk.gov.cabinetoffice.csl.factory.InviteFactory;
import uk.gov.cabinetoffice.csl.repository.InviteRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static java.time.LocalDateTime.now;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class InviteServiceTest {

    private static final String EMAIL = "test@example.com";
    private final String govNotifyTemplateId = "template-id";
    private final int validityInSeconds = 259200;
    private final String signupUrlFormat = "invite-url";
    private final InviteRepository inviteRepository = mock(InviteRepository.class);
    private final InviteFactory inviteFactory = mock(InviteFactory.class);
    private final NotifyService notifyService = mock(NotifyService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2024-01-01T10:00:00.000Z"), ZoneId.of("Europe/London"));

    private final InviteService inviteService = new InviteService(govNotifyTemplateId, validityInSeconds,
            signupUrlFormat, notifyService, inviteRepository, inviteFactory, clock);

    @Test
    public void updateInviteByCodeShouldUpdateStatusCorrectly() {
        final String code = "123abc";

        Invite invite = new Invite();
        invite.setStatus(InviteStatus.PENDING);
        invite.setCode(code);

        when(inviteRepository.findByCode(code))
                .thenReturn(invite);

        inviteService.updateInviteStatus(code, InviteStatus.ACCEPTED);

        ArgumentCaptor<Invite> inviteArgumentCaptor = ArgumentCaptor.forClass(Invite.class);

        verify(inviteRepository).save(inviteArgumentCaptor.capture());

        invite = inviteArgumentCaptor.getValue();
        MatcherAssert.assertThat(invite.getCode(), equalTo(code));
        MatcherAssert.assertThat(invite.getStatus(), equalTo(InviteStatus.ACCEPTED));
    }

    @Test
    public void inviteCodesLessThan24HrsShouldNotBeExpired() {
        final String code = "123abc";

        Invite invite = new Invite();
        invite.setStatus(InviteStatus.PENDING);
        invite.setCode(code);
        invite.setInvitedAt(now(clock).minusDays(7));

        when(inviteRepository.findByCode(code)).thenReturn(invite);

        MatcherAssert.assertThat(inviteService.isInviteCodeExpired(code), equalTo(true));
    }

    @Test
    public void inviteCodesOlderThanValidityDurationShouldBeExpired() {
        final String code = "123abc";

        Invite invite = new Invite();
        invite.setStatus(InviteStatus.PENDING);
        invite.setCode(code);
        invite.setInvitedAt(now(clock));

        when(inviteRepository.findByCode(code))
                .thenReturn(invite);

        MatcherAssert.assertThat(inviteService.isInviteCodeExpired(code), equalTo(false));
    }

    @Test
    public void shouldSendAndSaveSelfSignupInvite() {
        String email = "use@domain.org";
        String code = "invite-code";

        Invite invite = new Invite();
        invite.setForEmail(email);
        invite.setCode(code);
        when(inviteFactory.createSelfSignUpInvite(email)).thenReturn(invite);

        inviteService.sendSelfSignupInvite(email, true);

        verify(notifyService).notify(email, code, govNotifyTemplateId, signupUrlFormat, 259200L);
        verify(inviteRepository).save(invite);
    }

    @Test
    public void shouldReturnTrueIfEmailInvited() {
        when(inviteRepository.existsByForEmailIgnoreCaseAndInviterIdIsNotNull(EMAIL)).thenReturn(true);
        assertTrue(inviteService.isEmailInvited(EMAIL));
    }

    @Test
    public void shouldReturnFalseIfEmailNotInvited() {
        when(inviteRepository.existsByForEmailIgnoreCaseAndInviterIdIsNotNull(EMAIL)).thenReturn(false);
        assertFalse(inviteService.isEmailInvited(EMAIL));
    }
}

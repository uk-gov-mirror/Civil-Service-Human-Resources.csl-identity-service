package uk.gov.cabinetoffice.csl.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.cabinetoffice.csl.domain.Reset;
import uk.gov.cabinetoffice.csl.domain.ResetStatus;
import uk.gov.cabinetoffice.csl.repository.ResetRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static java.time.Month.FEBRUARY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.gov.cabinetoffice.csl.domain.ResetStatus.*;

@SpringBootTest
@ActiveProfiles("no-redis")
public class ResetServiceTest {

    public static final String EMAIL = "test@example.com";
    public static final String CODE = "abc123";
    public static final String TEMPLATE_ID = "template123";
    public static final String URL = "localhost:8080";
    private final int validityInSeconds = 86400;
    private final Clock clock = Clock.fixed(Instant.parse("2024-03-01T00:00:00Z"), ZoneId.of("Europe/London"));

    private final ResetRepository resetRepository = mock(ResetRepository.class);

    private final NotifyService notifyService = mock(NotifyService.class);

    private final ResetService resetService = new ResetService(resetRepository, notifyService, clock, validityInSeconds);

    @Test
    public void shouldCreateNewPendingReset() {
        doNothing().when(notifyService).notify(EMAIL, CODE, TEMPLATE_ID, URL);

        resetService.createPendingResetRequestAndAndNotifyUser(EMAIL);

        ArgumentCaptor<Reset> resetArgumentCaptor = ArgumentCaptor.forClass(Reset.class);

        verify(resetRepository, times(1)).save(resetArgumentCaptor.capture());

        Reset reset = resetArgumentCaptor.getValue();
        assertNotEquals(reset.getCode(), CODE);
        assertEquals(reset.getEmail(), EMAIL);
        assertEquals(reset.getResetStatus(), PENDING);
        assertNotNull(reset.getRequestedAt());
    }

    @Test
    public void updatePendingResetShouldNotUpdateEmailStatusAndCode() {
        Reset reset = createReset();

        doNothing().when(notifyService).notify(reset.getEmail(), reset.getCode(), TEMPLATE_ID, URL);
        resetService.updatePendingResetRequestAndAndNotifyUser(reset);

        ArgumentCaptor<Reset> resetArgumentCaptor = ArgumentCaptor.forClass(Reset.class);

        verify(resetRepository, times(1)).save(resetArgumentCaptor.capture());

        Reset reset1 = resetArgumentCaptor.getValue();
        assertEquals(reset1.getCode(), CODE);
        assertEquals(reset1.getEmail(), EMAIL);
        assertEquals(reset1.getResetStatus(), PENDING);
        assertNotNull(reset1.getRequestedAt());

    }

    @Test
    public void shouldUpdateStatusOfAllExistingResetsToExpiredAndReturnNull() {
        doNothing().when(notifyService).notify(EMAIL, CODE, TEMPLATE_ID, URL);

        Reset reset1 = createReset();
        Reset reset2 = createReset();
        List<Reset> existingPendingResets = new ArrayList<>();
        existingPendingResets.add(reset1);
        existingPendingResets.add(reset2);

        when(resetRepository.findByEmailIgnoreCaseAndResetStatus(EMAIL, PENDING)).thenReturn(existingPendingResets);

        Reset reset = resetService.getPendingResetForEmail(EMAIL);
        assertNull(reset);

        verify(resetRepository, times(1)).saveAll(existingPendingResets);
        ResetStatus resetStatus1 = existingPendingResets.get(0).getResetStatus();
        assertEquals(resetStatus1, EXPIRED);
        ResetStatus resetStatus2 = existingPendingResets.get(0).getResetStatus();
        assertEquals(resetStatus2, EXPIRED);
    }

    @Test
    public void shouldUpdateStatusOfPendingResetToExpiredAndReturnNull() {
        doNothing().when(notifyService).notify(EMAIL, CODE, TEMPLATE_ID, URL);

        Reset reset = createReset();

        List<Reset> existingPendingResets = new ArrayList<>();
        existingPendingResets.add(reset);

        when(resetRepository.findByEmailIgnoreCaseAndResetStatus(EMAIL, PENDING)).thenReturn(existingPendingResets);

        Reset reset1 = resetService.getPendingResetForEmail(EMAIL);
        assertNull(reset1);
        ResetStatus resetStatus1 = existingPendingResets.get(0).getResetStatus();
        assertEquals(resetStatus1, EXPIRED);

        ArgumentCaptor<Reset> resetArgumentCaptor = ArgumentCaptor.forClass(Reset.class);
        verify(resetRepository).save(resetArgumentCaptor.capture());

        Reset actualReset = resetArgumentCaptor.getValue();

        assertEquals(actualReset.getResetStatus(), EXPIRED);

        verify(resetRepository, times(1)).save(reset);
    }

    @Test
    public void shouldReturnPendingReset() {
        doNothing().when(notifyService).notify(EMAIL, CODE, TEMPLATE_ID, URL);

        Reset reset = createReset();
        reset.setRequestedAt(LocalDateTime.now());

        List<Reset> existingPendingResets = new ArrayList<>();
        existingPendingResets.add(reset);

        when(resetRepository.findByEmailIgnoreCaseAndResetStatus(EMAIL, PENDING)).thenReturn(existingPendingResets);

        Reset reset1 = resetService.getPendingResetForEmail(EMAIL);
        ResetStatus resetStatus1 = reset1.getResetStatus();
        assertEquals(resetStatus1, PENDING);
        assertEquals(reset1.getCode(), reset.getCode());
        assertEquals(reset1.getEmail(), reset.getEmail());
        assertEquals(reset1.getRequestedAt(), reset.getRequestedAt());

        verify(resetRepository, times(0)).saveAll(existingPendingResets);
    }

    @Test
    public void shouldModifyExistingResetWhenResetSuccessFor() {
        doNothing().when(notifyService).notify(EMAIL, CODE, TEMPLATE_ID, URL);

        Reset expectedReset = createReset();

        resetService.notifyUserForSuccessfulReset(expectedReset);

        ArgumentCaptor<Reset> resetArgumentCaptor = ArgumentCaptor.forClass(Reset.class);

        verify(resetRepository).save(resetArgumentCaptor.capture());

        Reset actualReset = resetArgumentCaptor.getValue();
        assertEquals(actualReset.getCode(), CODE);
        assertEquals(actualReset.getEmail(), EMAIL);
        assertEquals(actualReset.getResetStatus(), RESET);
    }

    @Test
    public void isResetExpiredShouldReturnExpiredIfRequestedAtMoreThan24H() {
        Reset reset = createReset();

        LocalDateTime requestDateTime = LocalDateTime.of(2024, FEBRUARY, 1, 11, 30);
        reset.setRequestedAt(requestDateTime);

        assertTrue(resetService.isResetExpired(reset));

        ArgumentCaptor<Reset> resetArgumentCaptor = ArgumentCaptor.forClass(Reset.class);
        verify(resetRepository).save(resetArgumentCaptor.capture());

        Reset actualReset = resetArgumentCaptor.getValue();
        assertEquals(actualReset.getCode(), CODE);
        assertEquals(actualReset.getEmail(), EMAIL);
        assertEquals(actualReset.getResetStatus(), EXPIRED);
    }

    private Reset createReset() {
        Reset reset = new Reset();
        reset.setCode(CODE);
        reset.setEmail(EMAIL);
        reset.setResetStatus(PENDING);
        LocalDateTime requestDateTime = LocalDateTime.of(2024, FEBRUARY, 1, 11, 30);
        reset.setRequestedAt(requestDateTime);
        return reset;
    }
}

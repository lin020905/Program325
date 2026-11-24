package org.xidian.program325;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xidian.program325.common.ApiException;
import org.xidian.program325.common.Inputs;
import org.xidian.program325.mapper.ReservationMapper;
import org.xidian.program325.mapper.RoomMapper;
import org.xidian.program325.pojo.Reservations.Reservation;
import org.xidian.program325.pojo.Reservations.ReservationView;
import org.xidian.program325.pojo.Reservations.Search;
import org.xidian.program325.pojo.Reservations.Status;
import org.xidian.program325.service.ReservationService;
import org.xidian.program325.service.RoomService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;

class ReservationConfirmationTests {
    private static final long USER_ID = 1;
    private static final long ROOM_ID = 2;
    private static final long RESERVATION_ID = 3;
    private static final LocalDateTime START = LocalDateTime.of(2027, 1, 15, 10, 0);
    private static final LocalDateTime END = START.plusHours(1);

    private final ReservationMapper reservations = mock(ReservationMapper.class);
    private final ReservationService service = new ReservationService(
            reservations, mock(RoomMapper.class), mock(RoomService.class));

    @BeforeEach
    void loadApprovedReservation() {
        when(reservations.findRoomId(RESERVATION_ID)).thenReturn(ROOM_ID);
        when(reservations.lock(RESERVATION_ID)).thenReturn(new Reservation(
                RESERVATION_ID, USER_ID, ROOM_ID, "项目例会", 4, START, END, Status.APPROVED, START.minusDays(1)));
    }

    @ParameterizedTest
    @ValueSource(ints = {-31, 60})
    void rejectsConfirmationOutsideTheWindow(int minutesFromStart) {
        try (var clock = mockStatic(Inputs.class, CALLS_REAL_METHODS)) {
            clock.when(Inputs::now).thenReturn(START.plusMinutes(minutesFromStart));
            ApiException error = assertThrows(ApiException.class, () -> service.confirm(USER_ID, RESERVATION_ID));
            assertEquals(CONFLICT, error.status());
            assertEquals("CONFIRMATION_NOT_ALLOWED", error.code());
            verify(reservations, never()).confirm(eq(RESERVATION_ID), any());
        }
    }

    @Test
    void acceptsConfirmationExactlyThirtyMinutesEarly() {
        LocalDateTime boundary = START.minusMinutes(30);
        ReservationView confirmed = mock(ReservationView.class);
        when(reservations.detail(RESERVATION_ID, new Search(null, null, null, boundary))).thenReturn(confirmed);

        try (var clock = mockStatic(Inputs.class, CALLS_REAL_METHODS)) {
            clock.when(Inputs::now).thenReturn(boundary);
            assertSame(confirmed, service.confirm(USER_ID, RESERVATION_ID));
            verify(reservations).confirm(RESERVATION_ID, boundary);
        }
    }

    @Test
    void rejectsRepeatedConfirmationWithoutWritingAgain() {
        when(reservations.isConfirmed(RESERVATION_ID)).thenReturn(true);

        try (var clock = mockStatic(Inputs.class, CALLS_REAL_METHODS)) {
            clock.when(Inputs::now).thenReturn(START);
            ApiException error = assertThrows(ApiException.class, () -> service.confirm(USER_ID, RESERVATION_ID));
            assertEquals(CONFLICT, error.status());
            assertEquals("ALREADY_CONFIRMED", error.code());
            verify(reservations, never()).confirm(eq(RESERVATION_ID), any());
        }
    }
}

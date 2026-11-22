package org.xidian.program325.pojo;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import org.xidian.program325.common.Inputs;

public final class Reservations {
    private Reservations() {
    }

    public enum Status { PENDING, APPROVED, REJECTED, CANCELLED, EXPIRED }

    public enum Decision { APPROVED, REJECTED }

    public record CreateRequest(Long roomId, String subject, Integer attendees,
                                @JsonFormat(pattern = Inputs.LOCAL_DATE_TIME, lenient = OptBoolean.FALSE)
                                LocalDateTime startTime,
                                @JsonFormat(pattern = Inputs.LOCAL_DATE_TIME, lenient = OptBoolean.FALSE)
                                LocalDateTime endTime) {
    }

    public record ApprovalRequest(Decision result, String reason) {
    }

    public record Reservation(long reservationId, long userId, long roomId, String subject, int attendees,
                              LocalDateTime startTime, LocalDateTime endTime, Status status,
                              LocalDateTime createdAt) {
    }

    public record ReservationView(long reservationId, long userId, String username, long roomId,
                                  String roomName, String roomNo, String subject, int attendees,
                                  LocalDateTime startTime, LocalDateTime endTime, Status status,
                                  LocalDateTime createdAt, LocalDateTime cancelledAt, Decision approvalResult,
                                  String approverName, String approvalReason, LocalDateTime approvedAt,
                                  LocalDateTime confirmedAt) {
    }

    public record Occupancy(long reservationId, long roomId, String subject, LocalDateTime startTime,
                            LocalDateTime endTime, Status status) {
    }

    public record Schedule(LocalDateTime startTime, LocalDateTime endTime, List<Rooms.Room> rooms,
                           List<Occupancy> reservations) {
    }

    public record Page<T>(List<T> items, int page, int size, long total) {
    }

    public record Search(Long userId, Long roomId, Status status, LocalDateTime now) {
    }
}

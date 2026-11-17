package org.xidian.program325.backend;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class Models {
    private Models() {}

    public record Login(String username, String password) {}
    public record Account(long userId, String username, String role, String passwordHash) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Identity(long userId, String username, String role) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Room(long roomId, String name, String roomNo, int capacity,
                       BigDecimal area, String purpose, boolean bookable) {}
    public record RoomInput(String name, @JsonProperty("room_no") String roomNo, int capacity,
                            BigDecimal area, String purpose, Boolean bookable) {}
    public record Request(@JsonProperty("room_id") long roomId, String subject, int attendees,
                          @JsonProperty("start_time") LocalDateTime startTime,
                          @JsonProperty("end_time") LocalDateTime endTime) {}
    public record Review(String result, String reason) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Reservation(long reservationId, long userId, long roomId,
                              String username, String roomName, String roomNo,
                              String subject, int attendees, LocalDateTime startTime,
                              LocalDateTime endTime, String status, LocalDateTime createdAt,
                              LocalDateTime cancelledAt, Long approverId, String approvalResult,
                              String reason, LocalDateTime approvedAt, LocalDateTime confirmedAt) {}
}

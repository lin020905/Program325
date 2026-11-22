package org.xidian.program325.pojo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class Rooms {
    private Rooms() {
    }

    public record Room(long roomId, String name, String roomNo, int capacity, BigDecimal area,
                       String purpose, boolean bookable, LocalDateTime deletedAt,
                       LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record RoomRequest(String name, String roomNo, Integer capacity, BigDecimal area,
                              String purpose, Boolean bookable) {
    }
}

package org.xidian.program325.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.xidian.program325.common.ApiException;
import org.xidian.program325.common.Inputs;
import org.xidian.program325.mapper.ReservationMapper;
import org.xidian.program325.mapper.RoomMapper;
import org.xidian.program325.pojo.Rooms.Room;
import org.xidian.program325.pojo.Rooms.RoomRequest;

@Service
public class RoomService {
    private static final BigDecimal MAX_AREA = new BigDecimal("999999.99");

    private final RoomMapper rooms;
    private final ReservationMapper reservations;

    public RoomService(RoomMapper rooms, ReservationMapper reservations) {
        this.rooms = rooms;
        this.reservations = reservations;
    }

    public List<Room> list(Integer attendees, LocalDateTime start, LocalDateTime end) {
        LocalDateTime now = Inputs.now();
        if (attendees != null) {
            Inputs.positive(attendees, "参会人数");
        }
        if (start != null || end != null) {
            Inputs.reservationWindow(start, end, now);
        }
        return rooms.list(attendees, start, end, now);
    }

    public Room detail(long id) {
        Inputs.positive(id, "会议室编号");
        return requireActive(rooms.find(id));
    }

    @Transactional
    public Room create(RoomRequest request) {
        RoomRequest clean = validate(request, true);
        try {
            rooms.insert(clean);
        } catch (DuplicateKeyException error) {
            throw ApiException.conflict("DUPLICATE_ROOM_NO", "房号已存在");
        }
        return rooms.find(rooms.lastInsertId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Room update(long id, RoomRequest request) {
        Inputs.positive(id, "会议室编号");
        Room room = requireActive(rooms.lock(id));
        RoomRequest clean = validate(request, room.bookable());
        if (clean.capacity() < room.capacity()
                && reservations.maximumActiveAttendees(id, Inputs.now()) > clean.capacity()) {
            throw ApiException.conflict("ROOM_HAS_ACTIVE_RESERVATIONS", "新容量小于现有有效预约的参会人数");
        }
        try {
            rooms.update(id, clean);
        } catch (DuplicateKeyException error) {
            throw ApiException.conflict("DUPLICATE_ROOM_NO", "房号已存在");
        }
        return rooms.find(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(long id) {
        Inputs.positive(id, "会议室编号");
        requireActive(rooms.lock(id));
        LocalDateTime now = Inputs.now();
        if (reservations.maximumActiveAttendees(id, now) > 0) {
            throw ApiException.conflict("ROOM_HAS_ACTIVE_RESERVATIONS", "会议室仍有尚未结束的有效预约");
        }
        rooms.delete(id, now);
    }

    private Room requireActive(Room room) {
        if (room == null || room.deletedAt() != null) {
            throw ApiException.missing("ROOM_NOT_FOUND", "会议室不存在或已删除");
        }
        return room;
    }

    private RoomRequest validate(RoomRequest request, boolean defaultBookable) {
        String name = Inputs.text(request.name(), 100, "会议室名称");
        String roomNo = Inputs.text(request.roomNo(), 30, "房号");
        String purpose = Inputs.text(request.purpose(), 255, "用途");
        if (request.capacity() == null || request.capacity() <= 0) {
            throw ApiException.invalid("会议室容量必须为正整数");
        }
        BigDecimal area = request.area();
        if (area == null || area.signum() <= 0 || area.compareTo(MAX_AREA) > 0
                || area.stripTrailingZeros().scale() > 2) {
            throw ApiException.invalid("面积须大于 0 且不超过 999999.99，最多两位小数");
        }
        boolean bookable = request.bookable() == null ? defaultBookable : request.bookable();
        return new RoomRequest(name, roomNo, request.capacity(), area, purpose, bookable);
    }
}

package org.xidian.program325.backend;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import static org.springframework.http.HttpStatus.*;
import static org.xidian.program325.backend.ApiError.require;
import static org.xidian.program325.backend.Models.*;

@Service
public class BookingService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> ACTIVE = Set.of("PENDING", "APPROVED");
    private static final Set<String> STATUSES = Set.of("PENDING", "APPROVED", "REJECTED", "CANCELLED");
    private final BookingMapper mapper;

    public BookingService(BookingMapper mapper) {
        this.mapper = mapper;
    }

    public List<Room> rooms(LocalDateTime start, LocalDateTime end, Integer attendees) {
        if (start != null || end != null) {
            validateSlot(start, end);
            if (attendees == null) {
                attendees = 1;
            }
        }
        require(attendees == null || attendees > 0, BAD_REQUEST, "参会人数必须大于零");
        return mapper.rooms(start, end, attendees);
    }

    public Room room(long id) {
        Room room = mapper.room(id);
        require(room != null, NOT_FOUND, "会议室不存在");
        return room;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation create(Identity user, Request request) {
        validateSlot(request.startTime(), request.endTime());
        String subject = requiredText(request.subject(), 200, "会议主题");
        require(request.attendees() > 0, BAD_REQUEST, "参会人数必须大于零");
        Room room = lockedRoom(request.roomId());
        checkAvailability(room, 0, request.attendees(), request.startTime(), request.endTime());
        BookingMapper.NewReservation entry = new BookingMapper.NewReservation(user.userId(),
                new Request(room.roomId(), subject, request.attendees(), request.startTime(), request.endTime()));
        mapper.insertReservation(entry, now());
        return mapper.reservation(entry.id);
    }

    public List<Reservation> mine(Identity user) {
        return mapper.reservations(user.userId(), null, null, null, null);
    }

    public List<Reservation> all(Identity user, Long room, String status, LocalDateTime from, LocalDateTime until) {
        admin(user);
        require(room == null || room > 0, BAD_REQUEST, "会议室编号无效");
        require(status == null || STATUSES.contains(status), BAD_REQUEST, "预约状态无效");
        require(from == null || until == null || from.isBefore(until), BAD_REQUEST, "查询开始时间必须早于结束时间");
        return mapper.reservations(null, room, status, from, until);
    }

    public Reservation detail(Identity user, long id) {
        Reservation reservation = reservation(id);
        require(reservation.userId() == user.userId() || "ADMIN".equals(user.role()), FORBIDDEN, "无权查看此预约");
        return reservation;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation cancel(Identity user, long id) {
        lockReservationRoom(id);
        Reservation reservation = reservation(id);
        require(reservation.userId() == user.userId(), FORBIDDEN, "仅预约本人可以取消");
        LocalDateTime now = now();
        require(ACTIVE.contains(reservation.status()) && reservation.endTime().isAfter(now)
                && reservation.confirmedAt() == null, CONFLICT, "当前预约无法取消");
        require(mapper.cancel(id, now) == 1, CONFLICT, "预约状态已变更");
        return reservation(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation review(Identity user, long id, Review input) {
        admin(user);
        String result = input.result();
        require("APPROVED".equals(result) || "REJECTED".equals(result), BAD_REQUEST, "审批结果无效");
        String reason = input.reason() == null ? null : input.reason().strip();
        if ("REJECTED".equals(result) || (reason != null && !reason.isEmpty())) {
            reason = requiredText(reason, 500, "审批理由");
        }
        Room room = lockReservationRoom(id);
        Reservation current = reservation(id);
        require("PENDING".equals(current.status()), CONFLICT, "仅待审批预约可以审批");
        if ("APPROVED".equals(result)) {
            checkAvailability(room, id, current.attendees(), current.startTime(), current.endTime());
        }
        require(mapper.review(id, result) == 1, CONFLICT, "预约状态已变更");
        mapper.insertApproval(id, user.userId(), result, reason, now());
        return reservation(id);
    }

    public void createRoom(Identity user, RoomInput input) {
        admin(user);
        mapper.createRoom(roomInput(input, true));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Room updateRoom(Identity user, long id, RoomInput input) {
        admin(user);
        Room current = lockedRoom(id);
        RoomInput updated = roomInput(input, current.bookable());
        require(mapper.exceedsCapacity(id, now(), updated.capacity()) == null,
                CONFLICT, "新容量低于尚未结束的有效预约人数");
        mapper.updateRoom(id, updated);
        return room(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteRoom(Identity user, long id) {
        admin(user);
        lockedRoom(id);
        LocalDateTime now = now();
        require(mapper.exceedsCapacity(id, now, 0) == null, CONFLICT, "会议室存在尚未结束的有效预约");
        mapper.deleteRoom(id, now);
    }

    private Reservation reservation(long id) {
        Reservation result = mapper.reservation(id);
        require(result != null, NOT_FOUND, "预约不存在");
        return result;
    }

    private Room lockReservationRoom(long id) {
        Long roomId = mapper.reservationRoom(id);
        require(roomId != null, NOT_FOUND, "预约不存在");
        Room room = lockedRoom(roomId);
        require(mapper.lockReservation(id) != null, NOT_FOUND, "预约不存在");
        return room;
    }

    private Room lockedRoom(long id) {
        Room room = mapper.lockRoom(id);
        require(room != null, NOT_FOUND, "会议室不存在");
        return room;
    }

    private void checkAvailability(Room room, long exclude, int attendees, LocalDateTime start, LocalDateTime end) {
        require(start.isAfter(now()), CONFLICT, "预约开始时间必须晚于当前时间");
        require(room.bookable(), CONFLICT, "会议室已暂停预约");
        if (room.capacity() < attendees) {
            throw new ApiError(BAD_REQUEST, 40002, "参会人数超过会议室容量");
        }
        if (mapper.conflict(room.roomId(), exclude, start, end) != null) {
            throw new ApiError(CONFLICT, 40901, "会议室在所选时段不可用");
        }
    }

    private static RoomInput roomInput(RoomInput input, boolean defaultBookable) {
        BigDecimal area = input.area();
        require(input.capacity() > 0, BAD_REQUEST, "容量必须大于零");
        require(area != null && area.signum() > 0 && area.stripTrailingZeros().scale() <= 2
                && area.compareTo(new BigDecimal("999999.99")) <= 0, BAD_REQUEST, "面积必须为正且最多两位小数");
        return new RoomInput(requiredText(input.name(), 100, "会议室名称"),
                requiredText(input.roomNo(), 30, "房号"), input.capacity(), area,
                requiredText(input.purpose(), 255, "会议室用途"),
                input.bookable() == null ? defaultBookable : input.bookable());
    }

    private static void validateSlot(LocalDateTime start, LocalDateTime end) {
        require(start != null && end != null && start.isBefore(end)
                && start.getYear() >= 1000 && end.getYear() <= 9999
                && wholeHour(start) && wholeHour(end)
                && start.toLocalDate().equals(end.minusSeconds(1).toLocalDate()), BAD_REQUEST,
                "请选择同一天内连续的整点时段");
    }

    private static boolean wholeHour(LocalDateTime time) {
        return time.getMinute() == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }

    static String requiredText(String value, int max, String label) {
        String cleaned = value == null ? "" : value.strip();
        require(!cleaned.isBlank() && cleaned.codePointCount(0, cleaned.length()) <= max,
                BAD_REQUEST, label + "不能为空，且最多 " + max + " 字符");
        return cleaned;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    private static void admin(Identity user) {
        require("ADMIN".equals(user.role()), FORBIDDEN, "需要管理员权限");
    }
}

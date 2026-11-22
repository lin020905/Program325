package org.xidian.program325.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.xidian.program325.common.ApiException;
import org.xidian.program325.common.Inputs;
import org.xidian.program325.mapper.ReservationMapper;
import org.xidian.program325.mapper.RoomMapper;
import org.xidian.program325.pojo.Reservations.ApprovalRequest;
import org.xidian.program325.pojo.Reservations.CreateRequest;
import org.xidian.program325.pojo.Reservations.Decision;
import org.xidian.program325.pojo.Reservations.Page;
import org.xidian.program325.pojo.Reservations.Reservation;
import org.xidian.program325.pojo.Reservations.ReservationView;
import org.xidian.program325.pojo.Reservations.Schedule;
import org.xidian.program325.pojo.Reservations.Search;
import org.xidian.program325.pojo.Reservations.Status;
import org.xidian.program325.pojo.Rooms.Room;

@Service
public class ReservationService {
    private static final int CONFIRM_EARLY_MINUTES = 30;
    private final ReservationMapper reservations;
    private final RoomMapper rooms;
    private final RoomService roomService;

    public ReservationService(ReservationMapper reservations, RoomMapper rooms, RoomService roomService) {
        this.reservations = reservations;
        this.rooms = rooms;
        this.roomService = roomService;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationView create(long userId, CreateRequest request) {
        if (request.roomId() == null || request.attendees() == null) {
            throw ApiException.invalid("会议室编号和参会人数为必填项");
        }
        Inputs.positive(request.roomId(), "会议室编号");
        Inputs.positive(request.attendees(), "参会人数");
        String subject = Inputs.text(request.subject(), 200, "会议主题");
        Room room = rooms.lock(request.roomId());
        if (room == null || room.deletedAt() != null) {
            throw ApiException.missing("ROOM_NOT_FOUND", "会议室不存在或已删除");
        }
        LocalDateTime now = Inputs.now();
        Inputs.reservationWindow(request.startTime(), request.endTime(), now);
        verifyAvailability(room, request.attendees(), request.startTime(), request.endTime(), 0, now);
        reservations.insert(userId, new CreateRequest(request.roomId(), subject, request.attendees(),
                request.startTime(), request.endTime()), now);
        return view(reservations.lastInsertId(), now);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ReservationView> list(Long userId, Long roomId, Status status, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw ApiException.invalid("page 至少为 1，size 范围为 1 至 100");
        }
        if (roomId != null) {
            Inputs.positive(roomId, "会议室编号");
        }
        Search search = new Search(userId, roomId, status, Inputs.now());
        long total = reservations.count(search);
        long offset = (page - 1L) * size;
        List<ReservationView> items = offset < total ? reservations.list(search, size, offset) : List.of();
        return new Page<>(items, page, size, total);
    }

    public ReservationView detail(Long userId, long id) {
        Inputs.positive(id, "预约编号");
        ReservationView result = view(id, Inputs.now());
        if (userId != null) {
            requireOwner(userId, result.userId());
        }
        return result;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationView cancel(long userId, long id) {
        LockedReservation locked = lock(id);
        Reservation reservation = locked.reservation();
        requireOwner(userId, reservation.userId());
        LocalDateTime now = Inputs.now();
        boolean pending = reservation.status() == Status.PENDING && now.isBefore(reservation.startTime());
        boolean approved = reservation.status() == Status.APPROVED && now.isBefore(reservation.endTime())
                && !reservations.isConfirmed(id);
        if (!pending && !approved) {
            throw ApiException.conflict("RESERVATION_NOT_CANCELLABLE", "当前预约状态不允许取消");
        }
        reservations.changeStatus(id, Status.CANCELLED, now);
        return view(id, now);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationView approve(long approverId, long id, ApprovalRequest request) {
        if (request.result() == null) {
            throw ApiException.invalid("请选择通过或驳回");
        }
        String reason = null;
        if (request.result() == Decision.REJECTED || !Inputs.trimWhitespace(request.reason()).isEmpty()) {
            reason = Inputs.text(request.reason(), 500, "审批理由");
        }
        LockedReservation locked = lock(id);
        Reservation reservation = locked.reservation();
        LocalDateTime now = Inputs.now();
        if (reservation.status() != Status.PENDING || !now.isBefore(reservation.startTime())) {
            throw ApiException.conflict("RESERVATION_NOT_PENDING", "预约已处理或已过期，请刷新待审批列表");
        }
        if (request.result() == Decision.APPROVED) {
            verifyAvailability(locked.room(), reservation.attendees(), reservation.startTime(), reservation.endTime(),
                    id, now);
        }
        reservations.approve(id, approverId, request.result(), reason, now);
        reservations.changeStatus(id, Status.valueOf(request.result().name()), null);
        return view(id, now);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationView confirm(long userId, long id) {
        Reservation reservation = lock(id).reservation();
        requireOwner(userId, reservation.userId());
        LocalDateTime now = Inputs.now();
        if (reservation.status() != Status.APPROVED || now.isBefore(reservation.startTime().minusMinutes(
                CONFIRM_EARLY_MINUTES)) || !now.isBefore(reservation.endTime())) {
            throw ApiException.conflict("CONFIRMATION_NOT_ALLOWED", "仅可在开始前 30 分钟至结束前确认已通过的预约");
        }
        if (reservations.isConfirmed(id)) {
            throw ApiException.conflict("ALREADY_CONFIRMED", "预约已确认使用");
        }
        reservations.confirm(id, now);
        return view(id, now);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Schedule schedule(LocalDate from, int days, Long roomId) {
        Inputs.scheduleWindow(from, days);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = from.plusDays(days).atStartOfDay();
        LocalDateTime now = Inputs.now();
        List<Room> scheduleRooms = roomId == null ? rooms.list(null, null, null, now)
                : List.of(roomService.detail(roomId));
        return new Schedule(start, end, scheduleRooms, reservations.schedule(roomId, start, end, now));
    }

    private LockedReservation lock(long id) {
        Inputs.positive(id, "预约编号");
        Long roomId = reservations.findRoomId(id);
        if (roomId == null) {
            throw ApiException.missing("RESERVATION_NOT_FOUND", "预约不存在");
        }
        Room room = rooms.lock(roomId);
        Reservation reservation = reservations.lock(id);
        return new LockedReservation(room, reservation);
    }

    private ReservationView view(long id, LocalDateTime now) {
        ReservationView result = reservations.detail(id, new Search(null, null, null, now));
        if (result == null) {
            throw ApiException.missing("RESERVATION_NOT_FOUND", "预约不存在");
        }
        return result;
    }

    private void verifyAvailability(Room room, int attendees, LocalDateTime start, LocalDateTime end,
                                    long excludedReservation, LocalDateTime now) {
        if (room.deletedAt() != null || !room.bookable()) {
            throw ApiException.conflict("ROOM_UNBOOKABLE", "会议室当前不可预约");
        }
        if (attendees > room.capacity()) {
            throw ApiException.conflict("CAPACITY_EXCEEDED", "参会人数超过会议室容量");
        }
        if (reservations.hasOverlap(room.roomId(), start, end, excludedReservation, now)) {
            throw ApiException.conflict("TIME_CONFLICT", "会议室在该时段已有有效预约");
        }
    }

    private void requireOwner(long userId, long ownerId) {
        if (userId != ownerId) {
            throw ApiException.forbidden("仅预约本人可访问此操作");
        }
    }

    private record LockedReservation(Room room, Reservation reservation) {
    }
}

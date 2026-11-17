package org.xidian.program325.backend;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
import static org.xidian.program325.backend.Models.*;

@Mapper
public interface BookingMapper {
    String ROOM = "room_id, name, room_no, capacity, area, purpose, is_bookable AS bookable";
    String ACTIVE = "status IN ('PENDING', 'APPROVED')";
    String OVERLAP = "start_time < #{end} AND end_time > #{start}";
    String RESERVATIONS = """
        SELECT r.reservation_id, r.user_id, r.room_id, u.username, m.name AS room_name,
               m.room_no, r.subject, r.attendees, r.start_time, r.end_time, r.status,
               r.created_at, r.cancelled_at, a.approver_id, a.result AS approval_result,
               a.reason, a.approved_at, c.confirmed_at
        FROM Reservations r JOIN Users u ON u.user_id = r.user_id
        JOIN MeetingRooms m ON m.room_id = r.room_id
        LEFT JOIN Approval a ON a.reservation_id = r.reservation_id
        LEFT JOIN Confirmations c ON c.reservation_id = r.reservation_id
        """;

    @Select("SELECT user_id, username, role, password_hash FROM Users WHERE username = #{username}")
    Account account(String username);

    @Select("SELECT " + ROOM + " FROM MeetingRooms WHERE room_id = #{id} AND deleted_at IS NULL FOR UPDATE")
    Room lockRoom(long id);

    @Select("SELECT " + ROOM + " FROM MeetingRooms WHERE room_id = #{id} AND deleted_at IS NULL")
    Room room(long id);

    @Select("SELECT " + ROOM + " " + """
        FROM MeetingRooms m WHERE deleted_at IS NULL
        AND (#{attendees} IS NULL OR (is_bookable = 1 AND capacity >= #{attendees}))
        AND (#{start} IS NULL OR NOT EXISTS (
            SELECT 1 FROM Reservations r WHERE r.room_id = m.room_id
            AND """ + " " + ACTIVE + " AND " + OVERLAP + ")) ORDER BY room_no")
    List<Room> rooms(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                     @Param("attendees") Integer attendees);

    @Select("SELECT reservation_id FROM Reservations WHERE room_id = #{room} AND reservation_id <> #{exclude}"
            + " AND " + ACTIVE + " AND " + OVERLAP + " LIMIT 1 FOR UPDATE")
    Long conflict(@Param("room") long room, @Param("exclude") long exclude,
                  @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    class NewReservation {
        public Long id;
        public final long userId;
        public final Request request;

        NewReservation(long userId, Request request) {
            this.userId = userId;
            this.request = request;
        }
    }

    @Insert("""
        INSERT INTO Reservations (user_id, room_id, subject, attendees, start_time, end_time, created_at)
        VALUES (#{entry.userId}, #{entry.request.roomId}, #{entry.request.subject}, #{entry.request.attendees},
                #{entry.request.startTime}, #{entry.request.endTime}, #{now})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "entry.id", keyColumn = "reservation_id")
    void insertReservation(@Param("entry") NewReservation entry, @Param("now") LocalDateTime now);

    @Select("SELECT room_id FROM Reservations WHERE reservation_id = #{id}")
    Long reservationRoom(long id);

    @Select("SELECT reservation_id FROM Reservations WHERE reservation_id = #{id} FOR UPDATE")
    Long lockReservation(long id);

    @Select(RESERVATIONS + " WHERE r.reservation_id = #{id}")
    Reservation reservation(long id);

    @Select(RESERVATIONS + """
        WHERE (#{user} IS NULL OR r.user_id = #{user})
          AND (#{room} IS NULL OR r.room_id = #{room})
          AND (#{status} IS NULL OR r.status = #{status})
          AND (#{from} IS NULL OR r.end_time > #{from})
          AND (#{until} IS NULL OR r.start_time < #{until})
        ORDER BY r.created_at DESC, r.reservation_id DESC
        """)
    List<Reservation> reservations(@Param("user") Long user, @Param("room") Long room,
                                   @Param("status") String status, @Param("from") LocalDateTime from,
                                   @Param("until") LocalDateTime until);

    @Update("UPDATE Reservations SET status = 'CANCELLED', cancelled_at = #{now} WHERE reservation_id = #{id} AND " + ACTIVE)
    int cancel(@Param("id") long id, @Param("now") LocalDateTime now);

    @Update("UPDATE Reservations SET status = #{result} WHERE reservation_id = #{id} AND status = 'PENDING'")
    int review(@Param("id") long id, @Param("result") String result);

    @Insert("""
        INSERT INTO Approval (reservation_id, approver_id, result, reason, approved_at)
        VALUES (#{id}, #{user}, #{result}, #{reason}, #{now})
        """)
    void insertApproval(@Param("id") long id, @Param("user") long user, @Param("result") String result,
                        @Param("reason") String reason, @Param("now") LocalDateTime now);

    @Select("SELECT reservation_id FROM Reservations WHERE room_id = #{id} AND end_time > #{now}"
            + " AND attendees > #{capacity} AND " + ACTIVE + " LIMIT 1 FOR UPDATE")
    Long exceedsCapacity(@Param("id") long id, @Param("now") LocalDateTime now, @Param("capacity") int capacity);

    @Insert("""
        INSERT INTO MeetingRooms (name, room_no, capacity, area, purpose, is_bookable)
        VALUES (#{name}, #{roomNo}, #{capacity}, #{area}, #{purpose}, #{bookable})
        """)
    void createRoom(RoomInput input);

    @Update("""
        UPDATE MeetingRooms SET name = #{input.name}, room_no = #{input.roomNo},
          capacity = #{input.capacity}, area = #{input.area}, purpose = #{input.purpose},
          is_bookable = #{input.bookable} WHERE room_id = #{id}
        """)
    void updateRoom(@Param("id") long id, @Param("input") RoomInput input);

    @Update("UPDATE MeetingRooms SET deleted_at = #{now}, is_bookable = 0 WHERE room_id = #{id}")
    void deleteRoom(@Param("id") long id, @Param("now") LocalDateTime now);
}

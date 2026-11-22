package org.xidian.program325.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.xidian.program325.pojo.Reservations.CreateRequest;
import org.xidian.program325.pojo.Reservations.Decision;
import org.xidian.program325.pojo.Reservations.Occupancy;
import org.xidian.program325.pojo.Reservations.Reservation;
import org.xidian.program325.pojo.Reservations.ReservationView;
import org.xidian.program325.pojo.Reservations.Search;
import org.xidian.program325.pojo.Reservations.Status;

@Mapper
public interface ReservationMapper {
    String OCCUPYING = "(r.status = 'APPROVED' OR (r.status = 'PENDING' AND r.start_time > #{now}))";
    String OCCUPYING_XML = "(r.status = 'APPROVED' OR (r.status = 'PENDING' AND r.start_time &gt; #{now}))";
    String DISPLAY_STATUS = """
            CASE WHEN r.status = 'PENDING' AND r.start_time &lt;= #{query.now} THEN 'EXPIRED' ELSE r.status END
            """;
    String VIEW_SELECT = """
            SELECT r.reservation_id, r.user_id, u.username, r.room_id, m.name AS room_name, m.room_no,
                   r.subject, r.attendees, r.start_time, r.end_time,
            """ + DISPLAY_STATUS + """
                   AS status, r.created_at, r.cancelled_at, a.result AS approval_result,
                   admin.username AS approver_name, a.reason AS approval_reason, a.approved_at, c.confirmed_at
            FROM Reservations r
            JOIN Users u ON u.user_id = r.user_id
            JOIN MeetingRooms m ON m.room_id = r.room_id
            LEFT JOIN Approval a ON a.reservation_id = r.reservation_id
            LEFT JOIN Users admin ON admin.user_id = a.approver_id
            LEFT JOIN Confirmations c ON c.reservation_id = r.reservation_id
            """;
    String SEARCH_FILTER = """
            <where>
                <if test="query.userId != null">AND r.user_id = #{query.userId}</if>
                <if test="query.roomId != null">AND r.room_id = #{query.roomId}</if>
                <if test="query.status != null">
                    <choose>
                        <when test="query.status.name() == 'PENDING'">
                            AND r.status = 'PENDING' AND r.start_time &gt; #{query.now}
                        </when>
                        <when test="query.status.name() == 'EXPIRED'">
                            AND r.status = 'PENDING' AND r.start_time &lt;= #{query.now}
                        </when>
                        <otherwise>AND r.status = #{query.status}</otherwise>
                    </choose>
                </if>
            </where>
            """;

    @Select("""
            SELECT room_id FROM Reservations WHERE reservation_id = #{id}
            """)
    Long findRoomId(@Param("id") long id);

    @Select("""
            SELECT reservation_id, user_id, room_id, subject, attendees, start_time, end_time, status, created_at
            FROM Reservations WHERE reservation_id = #{id} FOR UPDATE
            """)
    Reservation lock(@Param("id") long id);

    @Select("""
            SELECT EXISTS(SELECT 1 FROM Reservations r WHERE r.room_id = #{roomId}
                AND r.reservation_id != #{excluded} AND r.start_time < #{end} AND r.end_time > #{start}
                AND
            """ + OCCUPYING + ")")
    boolean hasOverlap(@Param("roomId") long roomId, @Param("start") LocalDateTime start,
                       @Param("end") LocalDateTime end, @Param("excluded") long excluded,
                       @Param("now") LocalDateTime now);

    @Select("""
            SELECT COALESCE(MAX(r.attendees), 0) FROM Reservations r
            WHERE r.room_id = #{roomId} AND r.end_time > #{now} AND
            """ + OCCUPYING)
    int maximumActiveAttendees(@Param("roomId") long roomId, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO Reservations (user_id, room_id, subject, attendees, start_time, end_time, created_at)
            VALUES (#{userId}, #{request.roomId}, #{request.subject}, #{request.attendees},
                    #{request.startTime}, #{request.endTime}, #{now})
            """)
    void insert(@Param("userId") long userId, @Param("request") CreateRequest request,
                @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Select("<script>" + VIEW_SELECT + "WHERE r.reservation_id = #{id}</script>")
    ReservationView detail(@Param("id") long id, @Param("query") Search query);

    @Select("<script>" + VIEW_SELECT + SEARCH_FILTER + """
            ORDER BY r.created_at DESC, r.reservation_id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ReservationView> list(@Param("query") Search query, @Param("limit") int limit, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) FROM Reservations r " + SEARCH_FILTER + "</script>")
    long count(@Param("query") Search query);

    @Update("UPDATE Reservations SET status = #{status}, cancelled_at = #{cancelledAt} WHERE reservation_id = #{id}")
    void changeStatus(@Param("id") long id, @Param("status") Status status,
                      @Param("cancelledAt") LocalDateTime cancelledAt);

    @Insert("""
            INSERT INTO Approval (reservation_id, approver_id, result, reason, approved_at)
            VALUES (#{id}, #{approver}, #{result}, #{reason}, #{time})
            """)
    void approve(@Param("id") long id, @Param("approver") long approver, @Param("result") Decision result,
                 @Param("reason") String reason, @Param("time") LocalDateTime time);

    @Select("SELECT EXISTS(SELECT 1 FROM Confirmations WHERE reservation_id = #{id})")
    boolean isConfirmed(@Param("id") long id);

    @Insert("INSERT INTO Confirmations (reservation_id, confirmed_at) VALUES (#{id}, #{now})")
    void confirm(@Param("id") long id, @Param("now") LocalDateTime now);

    @Select("""
            <script>
            SELECT r.reservation_id, r.room_id, r.subject, r.start_time, r.end_time, r.status
            FROM Reservations r JOIN MeetingRooms m ON m.room_id = r.room_id
            WHERE m.deleted_at IS NULL AND r.start_time &lt; #{end} AND r.end_time &gt; #{start}
                AND
            """ + OCCUPYING_XML + """
            <if test="roomId != null">AND r.room_id = #{roomId}</if>
            ORDER BY r.start_time, r.room_id, r.reservation_id
            </script>
            """)
    List<Occupancy> schedule(@Param("roomId") Long roomId, @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end, @Param("now") LocalDateTime now);
}

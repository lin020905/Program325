package org.xidian.program325.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.xidian.program325.pojo.Rooms.Room;
import org.xidian.program325.pojo.Rooms.RoomRequest;

@Mapper
public interface RoomMapper {
    String ROOM_COLUMNS = """
            SELECT room_id, name, room_no, capacity, area, purpose, is_bookable AS bookable,
                   deleted_at, created_at, updated_at
            FROM MeetingRooms m
            """;

    @Select(ROOM_COLUMNS + "WHERE room_id = #{id} AND deleted_at IS NULL")
    Room find(@Param("id") long id);

    @Select(ROOM_COLUMNS + "WHERE room_id = #{id} FOR UPDATE")
    Room lock(@Param("id") long id);

    @Select("""
            <script>
            """ + ROOM_COLUMNS + """
            WHERE m.deleted_at IS NULL
            <if test="attendees != null">AND m.capacity &gt;= #{attendees}</if>
            <if test="start != null">
                AND m.is_bookable = 1
                AND NOT EXISTS (
                    SELECT 1 FROM Reservations r WHERE r.room_id = m.room_id
                    AND r.start_time &lt; #{end} AND r.end_time &gt; #{start}
                    AND
            """ + ReservationMapper.OCCUPYING_XML + """
                )
            </if>
            ORDER BY m.room_id
            </script>
            """)
    List<Room> list(@Param("attendees") Integer attendees, @Param("start") LocalDateTime start,
                    @Param("end") LocalDateTime end, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO MeetingRooms (name, room_no, capacity, area, purpose, is_bookable)
            VALUES (#{name}, #{roomNo}, #{capacity}, #{area}, #{purpose}, #{bookable})
            """)
    void insert(RoomRequest room);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Update("""
            UPDATE MeetingRooms SET name = #{room.name}, room_no = #{room.roomNo},
                capacity = #{room.capacity}, area = #{room.area}, purpose = #{room.purpose},
                is_bookable = #{room.bookable}
            WHERE room_id = #{id}
            """)
    void update(@Param("id") long id, @Param("room") RoomRequest room);

    @Update("UPDATE MeetingRooms SET deleted_at = #{now}, is_bookable = 0 WHERE room_id = #{id}")
    void delete(@Param("id") long id, @Param("now") LocalDateTime now);
}

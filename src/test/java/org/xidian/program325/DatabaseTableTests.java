package org.xidian.program325;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用 application.properties 连接真实 MySQL，运行前设置 DB_PASSWORD 并执行 schema.sql。
 * 可直接在 IDEA 运行本类，或在项目根目录执行 mvn test。
 * 每个用例均自动回滚，不执行建表、删表或清空表操作。
 * 权限、容量和并发防重由业务服务负责，不属于本类验证的数据库约束。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@DisplayName("会议室预约系统数据库表测试")
class DatabaseTableTests {
    private static final LocalDateTime START = LocalDateTime.of(2030, 1, 10, 9, 0);
    private static final LocalDateTime END = START.plusHours(2);
    private static final long MISSING_ID = Long.MAX_VALUE;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private long userId;
    private long adminId;
    private long roomId;
    private String username;
    private String adminUsername;
    private String roomNo;

    @BeforeEach
    void createFixtures() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        username = "test_user_" + suffix;
        adminUsername = "test_admin_" + suffix;
        roomNo = "QA-" + suffix;
        userId = insert("INSERT INTO Users(username, password_hash) VALUES (?, ?)", username, "test-only-hash");
        adminId = insert("INSERT INTO Users(username, password_hash, role) VALUES (?, ?, 'ADMIN')",
                adminUsername, "test-only-hash");
        roomId = insert("INSERT INTO MeetingRooms(name, room_no, capacity, area, purpose) VALUES (?, ?, ?, ?, ?)",
                "测试会议室", roomNo, 20, new BigDecimal("40.50"), "小组讨论");
    }

    @AfterTransaction
    void verifyTestDataWasRolledBack() {
        assertAll("测试数据应随事务回滚",
                () -> assertEquals(0, count("SELECT COUNT(*) FROM Users WHERE username IN (?, ?)", username, adminUsername)),
                () -> assertEquals(0, count("SELECT COUNT(*) FROM MeetingRooms WHERE room_no = ?", roomNo)));
    }

    @Test
    @DisplayName("应用能够读取五张真实 InnoDB 表")
    void applicationReadsAllFiveTables() {
        assertNotNull(jdbc.queryForObject("SELECT DATABASE()", String.class));
        for (String table : List.of("Users", "MeetingRooms", "Reservations", "Approval", "Confirmations")) {
            assertEquals("InnoDB", jdbc.queryForObject("SELECT ENGINE FROM information_schema.TABLES "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND LOWER(TABLE_NAME) = ?",
                    String.class, table.toLowerCase(Locale.ROOT)), table);
            assertNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        }
    }

    @Test
    @DisplayName("用户支持增删改查并具有正确默认值")
    void usersSupportCrudAndDefaults() {
        assertEquals("USER", jdbc.queryForObject("SELECT role FROM Users WHERE user_id = ?", String.class, userId));
        assertEquals("ADMIN", jdbc.queryForObject("SELECT role FROM Users WHERE user_id = ?", String.class, adminId));
        assertNotNull(jdbc.queryForObject("SELECT created_at FROM Users WHERE user_id = ?", Timestamp.class, userId));
        assertEquals(1, jdbc.update("UPDATE Users SET username = ? WHERE user_id = ?", "renamed_" + roomNo, userId));
        assertEquals("renamed_" + roomNo,
                jdbc.queryForObject("SELECT username FROM Users WHERE user_id = ?", String.class, userId));
        assertEquals(1, jdbc.update("DELETE FROM Users WHERE user_id = ?", userId));
        assertEquals(0, count("SELECT COUNT(*) FROM Users WHERE user_id = ?", userId));
    }

    @Test
    @DisplayName("用户名唯一且不区分大小写")
    void usersRejectDuplicateUsername() {
        expectMysqlError(1062, "INSERT INTO Users(username, password_hash) VALUES (?, ?)",
                username.toUpperCase(Locale.ROOT), "test-hash");
    }

    @ParameterizedTest(name = "拒绝非法用户字段 {0}={1}")
    @MethodSource("invalidUserFields")
    void usersRejectInvalidFields(String column, String value, int errorCode) {
        expectMysqlError(errorCode, "UPDATE Users SET " + column + " = ? WHERE user_id = ?", value, userId);
    }

    static Stream<Arguments> invalidUserFields() {
        return Stream.of(
                Arguments.of("username", null, 1048), Arguments.of("username", "", 3819),
                Arguments.of("username", "   ", 3819), Arguments.of("password_hash", null, 1048),
                Arguments.of("password_hash", "", 3819), Arguments.of("password_hash", "   ", 3819),
                Arguments.of("role", null, 1048), Arguments.of("role", "ROOT", 3819),
                Arguments.of("role", "admin", 3819));
    }

    @Test
    @DisplayName("会议室支持增删改查并保留面积精度和默认值")
    void meetingRoomsSupportCrudAndDefaults() {
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM MeetingRooms WHERE room_id = ?", roomId);
        assertEquals("测试会议室", row.get("name"));
        assertEquals(new BigDecimal("40.50"), row.get("area"));
        assertEquals(1, ((Number) row.get("is_bookable")).intValue());
        assertNull(row.get("deleted_at"));
        assertNotNull(row.get("created_at"));
        assertNotNull(row.get("updated_at"));
        assertEquals(1, jdbc.update("UPDATE MeetingRooms SET capacity = 30, purpose = '项目评审' WHERE room_id = ?", roomId));
        assertEquals(30, jdbc.queryForObject("SELECT capacity FROM MeetingRooms WHERE room_id = ?", Integer.class, roomId));
        assertEquals(1, jdbc.update("DELETE FROM MeetingRooms WHERE room_id = ?", roomId));
        assertEquals(0, count("SELECT COUNT(*) FROM MeetingRooms WHERE room_id = ?", roomId));
    }

    @Test
    @DisplayName("会议室房号不能重复")
    void meetingRoomsRejectDuplicateRoomNumber() {
        expectMysqlError(1062, "INSERT INTO MeetingRooms(name, room_no, capacity, area, purpose) "
                + "VALUES ('重复房号测试', ?, 10, 20.00, '讨论')", roomNo);
    }

    @ParameterizedTest(name = "拒绝非法会议室字段 {0}={1}")
    @MethodSource("invalidRoomFields")
    void meetingRoomsRejectInvalidFields(String column, Object value, int errorCode) {
        expectMysqlError(errorCode, "UPDATE MeetingRooms SET " + column + " = ? WHERE room_id = ?", value, roomId);
    }

    static Stream<Arguments> invalidRoomFields() {
        return Stream.of(
                Arguments.of("name", null, 1048), Arguments.of("name", "   ", 3819),
                Arguments.of("room_no", "", 3819), Arguments.of("capacity", 0, 3819),
                Arguments.of("area", new BigDecimal("0.00"), 3819),
                Arguments.of("area", new BigDecimal("-0.01"), 3819),
                Arguments.of("purpose", "   ", 3819), Arguments.of("is_bookable", 2, 3819));
    }

    @Test
    @DisplayName("逻辑删除保留预约历史且外键阻止物理删除")
    void deletedRoomRetainsReservationHistory() {
        long id = reservation(START, END);
        expectMysqlError(1451, "DELETE FROM MeetingRooms WHERE room_id = ?", roomId);
        jdbc.update("UPDATE MeetingRooms SET deleted_at = CURRENT_TIMESTAMP WHERE room_id = ?", roomId);
        assertEquals(1, count("SELECT COUNT(*) FROM Reservations r JOIN MeetingRooms m ON r.room_id = m.room_id "
                + "WHERE r.reservation_id = ? AND m.deleted_at IS NOT NULL", id));
        assertEquals(0, count("SELECT COUNT(*) FROM MeetingRooms WHERE room_id = ? AND deleted_at IS NULL", roomId));
    }

    @Test
    @DisplayName("预约保存详情和默认状态并支持更新删除")
    void reservationsStoreDetailsAndDefaults() {
        long id = reservation(START, END);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM Reservations WHERE reservation_id = ?", id);
        assertEquals(userId, ((Number) row.get("user_id")).longValue());
        assertEquals(roomId, ((Number) row.get("room_id")).longValue());
        assertEquals("数据库测试会议", row.get("subject"));
        assertEquals(10, ((Number) row.get("attendees")).intValue());
        assertEquals(START, jdbc.queryForObject("SELECT start_time FROM Reservations WHERE reservation_id = ?",
                Timestamp.class, id).toLocalDateTime());
        assertEquals(END, jdbc.queryForObject("SELECT end_time FROM Reservations WHERE reservation_id = ?",
                Timestamp.class, id).toLocalDateTime());
        assertEquals("PENDING", row.get("status"));
        assertNotNull(row.get("created_at"));
        assertNull(row.get("cancelled_at"));
        assertEquals(1, jdbc.update("UPDATE Reservations SET subject = '更新后的主题' WHERE reservation_id = ?", id));
        assertEquals("更新后的主题", jdbc.queryForObject("SELECT subject FROM Reservations WHERE reservation_id = ?", String.class, id));
        assertEquals(1, jdbc.update("DELETE FROM Reservations WHERE reservation_id = ?", id));
    }

    @ParameterizedTest(name = "预约外键必须引用已有记录 {0}")
    @ValueSource(strings = {"user_id", "room_id"})
    void reservationsRejectMissingParent(String column) {
        long id = reservation(START, END);
        expectMysqlError(1452, "UPDATE Reservations SET " + column + " = ? WHERE reservation_id = ?", MISSING_ID, id);
    }

    @ParameterizedTest(name = "拒绝非法预约字段 {0}={1}")
    @MethodSource("invalidReservationFields")
    void reservationsRejectInvalidFields(String column, Object value, int errorCode) {
        long id = reservation(START, END);
        expectMysqlError(errorCode, "UPDATE Reservations SET " + column + " = ? WHERE reservation_id = ?", value, id);
    }

    static Stream<Arguments> invalidReservationFields() {
        return Stream.of(
                Arguments.of("subject", null, 1048), Arguments.of("subject", "   ", 3819),
                Arguments.of("attendees", 0, 3819), Arguments.of("status", null, 1048),
                Arguments.of("status", "BOOKED", 3819), Arguments.of("status", "approved", 3819),
                Arguments.of("start_time", null, 1048), Arguments.of("end_time", null, 1048));
    }

    @ParameterizedTest(name = "拒绝非法时段 {0}")
    @MethodSource("invalidTimes")
    void reservationsRejectInvalidTimes(String description, LocalDateTime start, LocalDateTime end) {
        expectMysqlError(3819, "INSERT INTO Reservations(user_id, room_id, subject, attendees, start_time, end_time) "
                + "VALUES (?, ?, '时间边界测试', 10, ?, ?)", userId, roomId, start, end);
    }

    static Stream<Arguments> invalidTimes() {
        return Stream.of(
                Arguments.of("零时长", START, START), Arguments.of("结束早于开始", END, START),
                Arguments.of("开始非整点分钟", START.plusMinutes(30), END),
                Arguments.of("开始非整点秒", START.plusSeconds(1), END),
                Arguments.of("结束非整点分钟", START, END.plusMinutes(30)),
                Arguments.of("结束非整点秒", START, END.plusSeconds(1)),
                Arguments.of("跨日实际使用", START, START.plusDays(1)),
                Arguments.of("超过一个自然日", START.withHour(0), START.plusDays(2).withHour(0)));
    }

    @ParameterizedTest(name = "允许连续整点时段 {0}")
    @MethodSource("validTimes")
    void reservationsAcceptValidTimeBoundaries(String description, LocalDateTime start, LocalDateTime end) {
        assertEquals(1, count("SELECT COUNT(*) FROM Reservations WHERE reservation_id = ?", reservation(start, end)));
    }

    static Stream<Arguments> validTimes() {
        return Stream.of(
                Arguments.of("一小时", START, START.plusHours(1)), Arguments.of("多小时", START, END),
                Arguments.of("当天最后一小时", START.withHour(23), START.plusDays(1).withHour(0)),
                Arguments.of("完整自然日", START.withHour(0), START.plusDays(1).withHour(0)));
    }

    @Test
    @DisplayName("取消状态必须与取消时间同时设置")
    void cancellationRequiresMatchingTimestamp() {
        long id = reservation(START, END);
        expectMysqlError(3819, "UPDATE Reservations SET status = 'CANCELLED' WHERE reservation_id = ?", id);
        expectMysqlError(3819, "UPDATE Reservations SET cancelled_at = CURRENT_TIMESTAMP WHERE reservation_id = ?", id);
        assertEquals(1, jdbc.update("UPDATE Reservations SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP "
                + "WHERE reservation_id = ?", id));
        assertNotNull(jdbc.queryForObject("SELECT cancelled_at FROM Reservations WHERE reservation_id = ?", Timestamp.class, id));
    }

    @Test
    @DisplayName("有预约关联的用户不能被物理删除")
    void reservationsProtectTheirApplicant() {
        reservation(START, END);
        expectMysqlError(1451, "DELETE FROM Users WHERE user_id = ?", userId);
    }

    @Test
    @DisplayName("每次预约只能审批一次且保存审批人时间")
    void approvalStoresDecisionAndRejectsDuplicates() {
        long id = reservation(START, END);
        long approvalId = approval(id, "APPROVED", null);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM Approval WHERE approval_id = ?", approvalId);
        assertEquals(adminId, ((Number) row.get("approver_id")).longValue());
        assertEquals("APPROVED", row.get("result"));
        assertNull(row.get("reason"));
        assertNotNull(row.get("approved_at"));
        expectMysqlError(1062, "INSERT INTO Approval(reservation_id, approver_id, result) VALUES (?, ?, 'APPROVED')", id, adminId);
        expectMysqlError(1451, "DELETE FROM Reservations WHERE reservation_id = ?", id);
        expectMysqlError(1451, "DELETE FROM Users WHERE user_id = ?", adminId);
    }

    @ParameterizedTest(name = "驳回理由不能为空白 {0}")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void approvalRequiresRejectionReason(String reason) {
        long id = reservation(START, END);
        expectMysqlError(3819, "INSERT INTO Approval(reservation_id, approver_id, result, reason) "
                + "VALUES (?, ?, 'REJECTED', ?)", id, adminId, reason);
    }

    @Test
    @DisplayName("驳回决定和理由可以查询")
    void approvalStoresRejectionReason() {
        long id = reservation(START, END);
        approval(id, "REJECTED", "会议室临时维护");
        assertEquals("会议室临时维护", jdbc.queryForObject("SELECT reason FROM Approval WHERE reservation_id = ?", String.class, id));
    }

    @ParameterizedTest(name = "审批外键必须引用已有记录 {0}")
    @ValueSource(strings = {"reservation_id", "approver_id"})
    void approvalRejectsMissingParents(String column) {
        long approvalId = approval(reservation(START, END), "APPROVED", null);
        expectMysqlError(1452, "UPDATE Approval SET " + column + " = ? WHERE approval_id = ?", MISSING_ID, approvalId);
    }

    @ParameterizedTest(name = "拒绝非法审批结果 {0}")
    @NullAndEmptySource
    @ValueSource(strings = {"PENDING", "approved"})
    void approvalRejectsInvalidResults(String result) {
        long id = reservation(START, END);
        expectMysqlError(result == null ? 1048 : 3819,
                "INSERT INTO Approval(reservation_id, approver_id, result) VALUES (?, ?, ?)", id, adminId, result);
    }

    @Test
    @DisplayName("每次预约只能确认一次且保存确认时间")
    void confirmationsStoreTimestampAndRejectDuplicates() {
        long id = reservation(START, END);
        approveReservation(id);
        long confirmationId = insert("INSERT INTO Confirmations(reservation_id) VALUES (?)", id);
        assertNotNull(jdbc.queryForObject("SELECT confirmed_at FROM Confirmations WHERE confirmation_id = ?", Timestamp.class, confirmationId));
        expectMysqlError(1062, "INSERT INTO Confirmations(reservation_id) VALUES (?)", id);
        // 移除本次测试的审批关联后，仍应由确认表外键阻止删除预约。
        jdbc.update("DELETE FROM Approval WHERE reservation_id = ?", id);
        expectMysqlError(1451, "DELETE FROM Reservations WHERE reservation_id = ?", id);
    }

    @Test
    @DisplayName("使用确认必须关联已有预约")
    void confirmationsRejectMissingReservation() {
        expectMysqlError(1452, "INSERT INTO Confirmations(reservation_id) VALUES (?)", MISSING_ID);
    }

    @Test
    @DisplayName("左连接保留未审批未确认的预约")
    void historyIncludesPendingReservationWithoutOptionalRows() {
        Map<String, Object> row = history(reservation(START, END));
        assertEquals(username, row.get("username"));
        assertEquals(roomNo, row.get("room_no"));
        assertEquals("PENDING", row.get("status"));
        assertNull(row.get("result"));
        assertNull(row.get("confirmed_at"));
    }

    @Test
    @DisplayName("五表联查已审批且已确认的预约")
    void historyJoinsAllFiveTables() {
        long id = reservation(START, END);
        approveReservation(id);
        insert("INSERT INTO Confirmations(reservation_id) VALUES (?)", id);
        Map<String, Object> row = history(id);
        assertEquals(username, row.get("username"));
        assertEquals(roomNo, row.get("room_no"));
        assertEquals("APPROVED", row.get("status"));
        assertEquals("APPROVED", row.get("result"));
        assertNotNull(row.get("approved_at"));
        assertNotNull(row.get("confirmed_at"));
    }

    @Test
    @DisplayName("取消已通过预约后保留原审批记录")
    void cancellationRetainsApprovalHistory() {
        long id = reservation(START, END);
        approveReservation(id);
        jdbc.update("UPDATE Reservations SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP WHERE reservation_id = ?", id);
        Map<String, Object> row = history(id);
        assertEquals("CANCELLED", row.get("status"));
        assertEquals("APPROVED", row.get("result"));
        assertNotNull(row.get("approved_at"));
    }

    @Test
    @DisplayName("审批记录及预约状态修改能够一起回滚")
    void approvalAndReservationUpdatesRollBackTogether() {
        long id = reservation(START, END);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        transaction.executeWithoutResult(status -> {
            approveReservation(id);
            assertEquals(1, count("SELECT COUNT(*) FROM Approval WHERE reservation_id = ?", id));
            assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM Reservations WHERE reservation_id = ?", String.class, id));
            status.setRollbackOnly();
        });
        assertEquals(0, count("SELECT COUNT(*) FROM Approval WHERE reservation_id = ?", id));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM Reservations WHERE reservation_id = ?", String.class, id));
    }

    private long reservation(LocalDateTime start, LocalDateTime end) {
        return insert("INSERT INTO Reservations(user_id, room_id, subject, attendees, start_time, end_time) "
                + "VALUES (?, ?, '数据库测试会议', 10, ?, ?)", userId, roomId, start, end);
    }

    private long approval(long reservationId, String result, String reason) {
        return insert("INSERT INTO Approval(reservation_id, approver_id, result, reason) VALUES (?, ?, ?, ?)",
                reservationId, adminId, result, reason);
    }

    private void approveReservation(long reservationId) {
        approval(reservationId, "APPROVED", null);
        assertEquals(1, jdbc.update("UPDATE Reservations SET status = 'APPROVED' WHERE reservation_id = ?", reservationId));
    }

    private Map<String, Object> history(long reservationId) {
        return jdbc.queryForMap("SELECT u.username, m.room_no, r.status, a.result, a.approved_at, c.confirmed_at "
                + "FROM Reservations r JOIN Users u ON u.user_id = r.user_id "
                + "JOIN MeetingRooms m ON m.room_id = r.room_id "
                + "LEFT JOIN Approval a ON a.reservation_id = r.reservation_id "
                + "LEFT JOIN Confirmations c ON c.reservation_id = r.reservation_id "
                + "WHERE r.reservation_id = ?", reservationId);
    }

    private long insert(String sql, Object... arguments) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            return statement;
        }, keys);
        assertNotNull(keys.getKey(), "插入后应返回自增主键");
        return keys.getKey().longValue();
    }

    private long count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }

    private void expectMysqlError(int errorCode, String sql, Object... arguments) {
        // 当前 Spring 版本将部分 CHECK 错误包装为 UncategorizedSQLException，统一核对底层错误码。
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> jdbc.update(sql, arguments));
        SQLException cause = assertInstanceOf(SQLException.class, failure.getMostSpecificCause());
        assertEquals(errorCode, cause.getErrorCode(), "应由预期的 MySQL 约束拒绝，而非其他 SQL 错误");
    }
}

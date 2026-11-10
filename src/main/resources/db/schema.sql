-- 会议室预约系统表设计
-- 设计人：张茂林
-- MySQL 8.0.16+ / InnoDB / utf8mb4
-- 在空数据库中手动执行；本文件不删除已有表、不创建业务账号。
-- 表名与 ER 图一致，部署至 Linux 时也应保持大小写一致。
SET NAMES utf8mb4;

CREATE TABLE `Users` (
    `user_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户编号',
    `username` VARCHAR(50) NOT NULL COMMENT '登录账号',
    `password_hash` VARCHAR(255) NOT NULL COMMENT '带算法信息及盐的密码散列，不保存明文',
    `role` VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'USER' COMMENT 'USER预约用户 ADMIN管理员',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_users_username` (`username`),
    CONSTRAINT `ck_users_username` CHECK (CHAR_LENGTH(TRIM(`username`)) > 0),
    CONSTRAINT `ck_users_password` CHECK (CHAR_LENGTH(TRIM(`password_hash`)) > 0),
    CONSTRAINT `ck_users_role` CHECK (`role` IN ('USER', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

CREATE TABLE `MeetingRooms` (
    `room_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会议室编号',
    `name` VARCHAR(100) NOT NULL COMMENT '会议室名称',
    `room_no` VARCHAR(30) NOT NULL COMMENT '房号，全中心唯一',
    `capacity` INT UNSIGNED NOT NULL COMMENT '最大容纳人数',
    `area` DECIMAL(8,2) NOT NULL COMMENT '面积，平方米',
    `purpose` VARCHAR(255) NOT NULL COMMENT '用途说明',
    `is_bookable` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1可预约 0暂停预约，可选扩展',
    `deleted_at` DATETIME NULL DEFAULT NULL COMMENT '逻辑删除时间，NULL表示未删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    PRIMARY KEY (`room_id`),
    UNIQUE KEY `uk_rooms_room_no` (`room_no`),
    KEY `idx_rooms_filter` (`deleted_at`, `is_bookable`, `capacity`),
    CONSTRAINT `ck_rooms_name` CHECK (CHAR_LENGTH(TRIM(`name`)) > 0),
    CONSTRAINT `ck_rooms_room_no` CHECK (CHAR_LENGTH(TRIM(`room_no`)) > 0),
    CONSTRAINT `ck_rooms_capacity` CHECK (`capacity` > 0),
    CONSTRAINT `ck_rooms_area` CHECK (`area` > 0),
    CONSTRAINT `ck_rooms_purpose` CHECK (CHAR_LENGTH(TRIM(`purpose`)) > 0),
    CONSTRAINT `ck_rooms_bookable` CHECK (`is_bookable` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会议室表';

CREATE TABLE `Reservations` (
    `reservation_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '预约编号',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '申请人编号',
    `room_id` BIGINT UNSIGNED NOT NULL COMMENT '会议室编号',
    `subject` VARCHAR(200) NOT NULL COMMENT '会议主题',
    `attendees` INT UNSIGNED NOT NULL COMMENT '参会人数',
    `start_time` DATETIME NOT NULL COMMENT '开始时间，整点',
    `end_time` DATETIME NOT NULL COMMENT '结束时间，整点，区间右端不包含',
    `status` VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待审批 APPROVED已通过 REJECTED已驳回 CANCELLED已取消',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    `cancelled_at` DATETIME NULL DEFAULT NULL COMMENT '取消时间',
    PRIMARY KEY (`reservation_id`),
    KEY `idx_reservations_user_created` (`user_id`, `created_at`),
    KEY `idx_reservations_room_status_time` (`room_id`, `status`, `start_time`, `end_time`),
    KEY `idx_reservations_status_created` (`status`, `created_at`),
    CONSTRAINT `fk_reservations_user` FOREIGN KEY (`user_id`) REFERENCES `Users` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_reservations_room` FOREIGN KEY (`room_id`) REFERENCES `MeetingRooms` (`room_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `ck_reservations_subject` CHECK (CHAR_LENGTH(TRIM(`subject`)) > 0),
    CONSTRAINT `ck_reservations_attendees` CHECK (`attendees` > 0),
    CONSTRAINT `ck_reservations_time_order` CHECK (`end_time` > `start_time`),
    CONSTRAINT `ck_reservations_whole_hour` CHECK (
        MINUTE(`start_time`) = 0 AND SECOND(`start_time`) = 0
        AND MINUTE(`end_time`) = 0 AND SECOND(`end_time`) = 0
    ),
    -- 允许当天最后一段 23:00 至次日 00:00，区间内实际使用的小时仍属于同一天。
    CONSTRAINT `ck_reservations_same_day` CHECK (DATE(`start_time`) = DATE(DATE_SUB(`end_time`, INTERVAL 1 SECOND))),
    CONSTRAINT `ck_reservations_status` CHECK (`status` IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT `ck_reservations_cancelled` CHECK (
        (`status` = 'CANCELLED' AND `cancelled_at` IS NOT NULL)
        OR (`status` <> 'CANCELLED' AND `cancelled_at` IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约表';

CREATE TABLE `Approval` (
    `approval_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审批编号',
    `reservation_id` BIGINT UNSIGNED NOT NULL COMMENT '预约编号，每条预约至多审批一次',
    `approver_id` BIGINT UNSIGNED NOT NULL COMMENT '审批人编号，业务层校验ADMIN角色',
    `result` VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'APPROVED通过 REJECTED驳回',
    `reason` VARCHAR(500) NULL DEFAULT NULL COMMENT '审批意见，驳回时必填',
    `approved_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审批时间',
    PRIMARY KEY (`approval_id`),
    UNIQUE KEY `uk_approval_reservation` (`reservation_id`),
    KEY `idx_approval_approver_time` (`approver_id`, `approved_at`),
    CONSTRAINT `fk_approval_reservation` FOREIGN KEY (`reservation_id`) REFERENCES `Reservations` (`reservation_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_approval_approver` FOREIGN KEY (`approver_id`) REFERENCES `Users` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `ck_approval_result` CHECK (`result` IN ('APPROVED', 'REJECTED')),
    CONSTRAINT `ck_approval_reason` CHECK (`result` <> 'REJECTED' OR (`reason` IS NOT NULL AND CHAR_LENGTH(TRIM(`reason`)) > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批表';

-- 可选功能：不开启使用确认时，此表可保留为空，不得自动生成确认记录。
CREATE TABLE `Confirmations` (
    `confirmation_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '使用确认编号',
    `reservation_id` BIGINT UNSIGNED NOT NULL COMMENT '预约编号，每条预约至多确认一次',
    `confirmed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '实际确认时间',
    PRIMARY KEY (`confirmation_id`),
    UNIQUE KEY `uk_confirmations_reservation` (`reservation_id`),
    CONSTRAINT `fk_confirmations_reservation` FOREIGN KEY (`reservation_id`) REFERENCES `Reservations` (`reservation_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='使用确认表，可选功能';

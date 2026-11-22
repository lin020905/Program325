-- Initial schema for MySQL 8.0.16+.
-- Select the target database before running this script.
-- Application sessions should use Asia/Shanghai (+08:00) for DATETIME defaults.

CREATE TABLE Users (
    user_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'USER',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_username (username),
    CONSTRAINT chk_users_username CHECK (REGEXP_LIKE(username, '[^[:space:]]')),
    CONSTRAINT chk_users_password_hash CHECK (REGEXP_LIKE(password_hash, '[^[:space:]]')),
    CONSTRAINT chk_users_role CHECK (CAST(role AS BINARY) IN ('USER', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE MeetingRooms (
    room_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    room_no VARCHAR(30) NOT NULL,
    capacity INT UNSIGNED NOT NULL,
    area DECIMAL(8,2) NOT NULL,
    purpose VARCHAR(255) NOT NULL,
    is_bookable TINYINT UNSIGNED NOT NULL DEFAULT 1,
    deleted_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (room_id),
    UNIQUE KEY uk_rooms_room_no (room_no),
    KEY idx_rooms_filter (deleted_at, is_bookable, capacity),
    CONSTRAINT chk_rooms_name CHECK (REGEXP_LIKE(name, '[^[:space:]]')),
    CONSTRAINT chk_rooms_room_no CHECK (REGEXP_LIKE(room_no, '[^[:space:]]')),
    CONSTRAINT chk_rooms_capacity CHECK (capacity > 0),
    CONSTRAINT chk_rooms_area CHECK (area > 0),
    CONSTRAINT chk_rooms_purpose CHECK (REGEXP_LIKE(purpose, '[^[:space:]]')),
    CONSTRAINT chk_rooms_is_bookable CHECK (is_bookable IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE Reservations (
    reservation_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    room_id BIGINT UNSIGNED NOT NULL,
    subject VARCHAR(200) NOT NULL,
    attendees INT UNSIGNED NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at DATETIME DEFAULT NULL,

    PRIMARY KEY (reservation_id),
    KEY idx_reservations_user_created (user_id, created_at),
    KEY idx_reservations_room_status_time (room_id, status, start_time, end_time),
    KEY idx_reservations_status_created (status, created_at),
    CONSTRAINT fk_reservations_user FOREIGN KEY (user_id)
        REFERENCES Users (user_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_reservations_room FOREIGN KEY (room_id)
        REFERENCES MeetingRooms (room_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_reservations_subject CHECK (REGEXP_LIKE(subject, '[^[:space:]]')),
    CONSTRAINT chk_reservations_attendees CHECK (attendees > 0),
    CONSTRAINT chk_reservations_status
        CHECK (CAST(status AS BINARY) IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    -- The last occupied second must be on the start date. Midnight can be the end boundary.
    CONSTRAINT chk_reservations_time CHECK (
        created_at < start_time
        AND start_time < end_time
        AND MINUTE(start_time) = 0 AND SECOND(start_time) = 0
        AND MINUTE(end_time) = 0 AND SECOND(end_time) = 0
        AND DATE(start_time) = DATE(end_time - INTERVAL 1 SECOND)
    ),
    CONSTRAINT chk_reservations_cancelled_at CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_at >= created_at)
        OR (status <> 'CANCELLED' AND cancelled_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE Approval (
    approval_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT UNSIGNED NOT NULL,
    approver_id BIGINT UNSIGNED NOT NULL,
    result VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(500) DEFAULT NULL,
    approved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (approval_id),
    UNIQUE KEY uk_approval_reservation (reservation_id),
    KEY idx_approval_approver_time (approver_id, approved_at),
    CONSTRAINT fk_approval_reservation FOREIGN KEY (reservation_id)
        REFERENCES Reservations (reservation_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_approval_approver FOREIGN KEY (approver_id)
        REFERENCES Users (user_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_approval_result CHECK (CAST(result AS BINARY) IN ('APPROVED', 'REJECTED')),
    CONSTRAINT chk_approval_reason CHECK (
        result <> 'REJECTED' OR (reason IS NOT NULL AND REGEXP_LIKE(reason, '[^[:space:]]'))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE Confirmations (
    confirmation_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT UNSIGNED NOT NULL,
    confirmed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (confirmation_id),
    UNIQUE KEY uk_confirmations_reservation (reservation_id),
    CONSTRAINT fk_confirmations_reservation FOREIGN KEY (reservation_id)
        REFERENCES Reservations (reservation_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

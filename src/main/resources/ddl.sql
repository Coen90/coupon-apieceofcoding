-- 쿠폰 MVP 스키마 (MySQL 8.4)
--
-- 런타임 스키마는 Hibernate ddl-auto 가 관리한다 (application.yaml).
-- 이 파일은 리뷰/온보딩용 기준 DDL 이며, 엔티티(Coupon, Issuance)와 1:1 로 맞춘다.

CREATE TABLE coupon (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    name              VARCHAR(80)  NOT NULL,
    total_quantity    INT          NOT NULL,
    issued_quantity   INT          NOT NULL DEFAULT 0,
    validity_days     INT          NOT NULL DEFAULT 7,
    starts_at         DATETIME     NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE issuance (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    coupon_id       BIGINT       NOT NULL,
    issuance_attempt_id VARCHAR(36) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ISSUED',
    issued_at       DATETIME     NOT NULL,
    expires_at      DATETIME     NOT NULL,
    used_at         DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_issuance_attempt_id (issuance_attempt_id),
    UNIQUE KEY uk_issuance_user_coupon (user_id, coupon_id),
    KEY idx_issuance_status (status),
    KEY idx_issuance_coupon (coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE issuance_history (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    issuance_attempt_id VARCHAR(36)  NOT NULL,
    user_id             BIGINT       NOT NULL,
    coupon_id           BIGINT       NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    reason              VARCHAR(64)  NOT NULL,
    recorded_at         DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_issuance_history_user_coupon (user_id, coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE issuance_dlt_log (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    message_key         VARCHAR(300)  NOT NULL,
    dlt_partition       INT           NOT NULL,
    dlt_offset          BIGINT        NOT NULL,
    coupon_id           BIGINT        NULL,
    user_id             BIGINT        NULL,
    issuance_attempt_id VARCHAR(36)   NULL,
    issued_at           DATETIME(3)   NULL,
    expires_at          DATETIME(3)   NULL,
    exception_type      VARCHAR(200)  NULL,
    failure_reason      VARCHAR(500)  NULL,
    retry_count         INT           NOT NULL DEFAULT 0,
    status              VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    decision_reason     VARCHAR(64)   NULL,
    received_at         DATETIME(3)   NOT NULL,
    resolved_at         DATETIME(3)   NULL,
    version             BIGINT        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_issuance_dlt_message_key (message_key),
    KEY idx_issuance_dlt_status (status),
    KEY idx_issuance_dlt_attempt (issuance_attempt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

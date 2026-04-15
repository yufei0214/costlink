-- CostLink Production Migration Script
-- Safe to run on both fresh and existing databases (idempotent, MySQL 8.0 compatible)

-- Reset admin password to admin123 (BCrypt hash)
UPDATE t_user SET password = '$2a$10$j1aX26jRc.2JOrVWbUJ9J.zOHqdANyQnre4XwZxcKgsPFZ0iQphey'
WHERE username = 'admin';

-- Helper: add column if missing
DROP PROCEDURE IF EXISTS costlink_add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE costlink_add_column_if_missing(
    IN tbl VARCHAR(64),
    IN col VARCHAR(64),
    IN ddl_after_add VARCHAR(1024)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', tbl, ' ADD COLUMN ', ddl_after_add);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- Helper: drop column if exists
DROP PROCEDURE IF EXISTS costlink_drop_column_if_exists;
DELIMITER $$
CREATE PROCEDURE costlink_drop_column_if_exists(
    IN tbl VARCHAR(64),
    IN col VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', tbl, ' DROP COLUMN ', col);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- t_reimbursement: add reimbursement_month / remark, drop legacy start_date / end_date
CALL costlink_add_column_if_missing('t_reimbursement', 'reimbursement_month',
    '`reimbursement_month` VARCHAR(7) NULL COMMENT ''报销月份(yyyy-MM)'' AFTER `total_amount`');
CALL costlink_add_column_if_missing('t_reimbursement', 'remark',
    '`remark` VARCHAR(500) NULL COMMENT ''备注说明'' AFTER `reimbursement_month`');
CALL costlink_drop_column_if_exists('t_reimbursement', 'start_date');
CALL costlink_drop_column_if_exists('t_reimbursement', 'end_date');

-- t_user: add department for reimbursement group classification
CALL costlink_add_column_if_missing('t_user', 'department',
    '`department` VARCHAR(50) NULL COMMENT ''所属组'' AFTER `alipay_account`');

-- Cleanup helpers
DROP PROCEDURE IF EXISTS costlink_add_column_if_missing;
DROP PROCEDURE IF EXISTS costlink_drop_column_if_exists;

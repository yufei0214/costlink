-- CostLink Production Migration Script
-- Safe to run on both fresh and existing databases

-- Reset admin password to admin123 (BCrypt hash)
UPDATE t_user SET password = '$2a$10$j1aX26jRc.2JOrVWbUJ9J.zOHqdANyQnre4XwZxcKgsPFZ0iQphey'
WHERE username = 'admin';

-- Add new columns if they don't exist, drop old columns if they exist
ALTER TABLE t_reimbursement
  ADD COLUMN IF NOT EXISTS `reimbursement_month` VARCHAR(7) NULL COMMENT '报销月份(yyyy-MM)' AFTER `total_amount`,
  ADD COLUMN IF NOT EXISTS `remark` VARCHAR(500) NULL COMMENT '备注说明' AFTER `reimbursement_month`,
  DROP COLUMN IF EXISTS `start_date`,
  DROP COLUMN IF EXISTS `end_date`;

-- =====================================================================
-- CostLink 生产升级脚本 - 新增所属组功能
-- 日期: 2026-04-15
-- 适用: 已部署并有生产数据的 MySQL 8.0 实例
--
-- 变更内容:
--   1. t_user 表新增 department 列, 存储用户所属组
--
-- 特性:
--   - 幂等: 重复执行不会报错, 列存在时跳过
--   - 兼容 MySQL 8.0 (不使用 MariaDB 的 IF NOT EXISTS 语法)
--   - 不影响现有数据, 新列默认 NULL
--
-- 执行方式:
--   mysql -u<user> -p<pass> <database> < upgrade_20260415_department.sql
-- 或在 MySQL 客户端内:
--   SOURCE /path/to/upgrade_20260415_department.sql;
-- =====================================================================

-- 1) 新增 t_user.department (幂等)
SET @exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_user'
      AND COLUMN_NAME = 'department'
);
SET @ddl := IF(@exists = 0,
    'ALTER TABLE t_user ADD COLUMN department VARCHAR(50) NULL COMMENT ''所属组'' AFTER alipay_account',
    'SELECT ''t_user.department already exists, skip'' AS info'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 验证
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 't_user'
  AND COLUMN_NAME = 'department';

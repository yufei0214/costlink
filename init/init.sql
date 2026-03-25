-- CostLink Database Initialization Script
-- VPN报销服务系统

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table: t_user 用户表
-- ----------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL COMMENT 'LDAP用户名',
    `password` VARCHAR(255) NULL COMMENT '密码(模拟登录时使用)',
    `display_name` VARCHAR(100) NULL COMMENT '显示名称',
    `email` VARCHAR(100) NULL COMMENT '邮箱',
    `alipay_account` VARCHAR(100) NULL COMMENT '支付宝收款账号',
    `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色: USER/ADMIN',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ----------------------------
-- Table: t_admin_config 管理员配置表
-- ----------------------------
DROP TABLE IF EXISTS `t_admin_config`;
CREATE TABLE `t_admin_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `admin_user_id` BIGINT NOT NULL COMMENT '管理员用户ID',
    `alipay_pay_account` VARCHAR(100) NULL COMMENT '支付宝付款账号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_user` (`admin_user_id`),
    CONSTRAINT `fk_admin_config_user` FOREIGN KEY (`admin_user_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员配置表';

-- ----------------------------
-- Table: t_reimbursement 报销申请表
-- ----------------------------
DROP TABLE IF EXISTS `t_reimbursement`;
CREATE TABLE `t_reimbursement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '申请人ID',
    `total_amount` DECIMAL(10,2) NOT NULL COMMENT '总金额',
    `reimbursement_month` VARCHAR(7) NOT NULL COMMENT '报销月份(yyyy-MM)',
    `remark` VARCHAR(500) NULL COMMENT '备注说明',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/CONFIRMED/PAID/REJECTED',
    `reject_reason` VARCHAR(500) NULL COMMENT '驳回原因',
    `paid_at` DATETIME NULL COMMENT '付款时间',
    `paid_by` BIGINT NULL COMMENT '付款管理员ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`),
    CONSTRAINT `fk_reimbursement_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_reimbursement_paid_by` FOREIGN KEY (`paid_by`) REFERENCES `t_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报销申请表';

-- ----------------------------
-- Table: t_reimbursement_image 报销图片表
-- ----------------------------
DROP TABLE IF EXISTS `t_reimbursement_image`;
CREATE TABLE `t_reimbursement_image` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `reimbursement_id` BIGINT NOT NULL COMMENT '报销申请ID',
    `image_path` VARCHAR(500) NOT NULL COMMENT '图片存储路径',
    `original_name` VARCHAR(200) NULL COMMENT '原始文件名',
    `ocr_amount` DECIMAL(10,2) NULL COMMENT 'OCR识别金额',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_reimbursement_id` (`reimbursement_id`),
    CONSTRAINT `fk_image_reimbursement` FOREIGN KEY (`reimbursement_id`) REFERENCES `t_reimbursement` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报销图片表';

-- ----------------------------
-- Insert default admin user (password: admin123)
-- ----------------------------
INSERT INTO `t_user` (`username`, `password`, `display_name`, `role`) VALUES
('admin', '$2a$10$j1aX26jRc.2JOrVWbUJ9J.zOHqdANyQnre4XwZxcKgsPFZ0iQphey', '系统管理员', 'ADMIN');

SET FOREIGN_KEY_CHECKS = 1;

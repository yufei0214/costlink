package com.costlink.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_reimbursement")
public class Reimbursement {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private BigDecimal totalAmount;

    private LocalDate vpnStartDate;

    private LocalDate vpnEndDate;

    private String status;

    private String rejectReason;

    private LocalDateTime paidAt;

    private Long paidBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // Non-persistent fields
    @TableField(exist = false)
    private String username;

    @TableField(exist = false)
    private String displayName;

    @TableField(exist = false)
    private String alipayAccount;
}

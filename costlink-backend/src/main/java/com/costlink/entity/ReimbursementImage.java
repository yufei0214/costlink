package com.costlink.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_reimbursement_image")
public class ReimbursementImage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reimbursementId;

    private String imagePath;

    private String originalName;

    private BigDecimal ocrAmount;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

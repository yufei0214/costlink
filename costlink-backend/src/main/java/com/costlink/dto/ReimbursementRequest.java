package com.costlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ReimbursementRequest {
    @NotNull(message = "总金额不能为空")
    private BigDecimal totalAmount;

    @NotBlank(message = "报销月份不能为空")
    private String reimbursementMonth;

    private String remark;

    private List<ImageInfo> images;

    @Data
    public static class ImageInfo {
        private String imagePath;
        private String originalName;
        private BigDecimal ocrAmount;
        private Integer sortOrder;
    }
}

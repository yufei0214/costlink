package com.costlink.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ReimbursementRequest {
    @NotNull(message = "总金额不能为空")
    private BigDecimal totalAmount;

    @NotNull(message = "VPN开始日期不能为空")
    private LocalDate vpnStartDate;

    @NotNull(message = "VPN结束日期不能为空")
    private LocalDate vpnEndDate;

    private List<ImageInfo> images;

    @Data
    public static class ImageInfo {
        private String imagePath;
        private String originalName;
        private BigDecimal ocrAmount;
        private Integer sortOrder;
    }
}

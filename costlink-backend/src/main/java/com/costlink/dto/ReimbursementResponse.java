package com.costlink.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReimbursementResponse {
    private Long id;
    private Long userId;
    private String username;
    private String displayName;
    private String alipayAccount;
    private BigDecimal totalAmount;
    private LocalDate vpnStartDate;
    private LocalDate vpnEndDate;
    private String status;
    private String rejectReason;
    private LocalDateTime paidAt;
    private Long paidBy;
    private LocalDateTime createdAt;
    private List<ImageInfo> images;

    @Data
    public static class ImageInfo {
        private Long id;
        private String imagePath;
        private String originalName;
        private BigDecimal ocrAmount;
        private Integer sortOrder;
    }
}

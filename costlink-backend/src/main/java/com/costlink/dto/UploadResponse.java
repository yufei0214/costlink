package com.costlink.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UploadResponse {
    private String imagePath;
    private String originalName;
    private BigDecimal ocrAmount;
    private String message;

    public UploadResponse(String imagePath, String originalName, BigDecimal ocrAmount) {
        this.imagePath = imagePath;
        this.originalName = originalName;
        this.ocrAmount = ocrAmount;
        this.message = "上传成功";
    }
}

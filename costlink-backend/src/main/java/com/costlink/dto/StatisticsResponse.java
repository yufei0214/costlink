package com.costlink.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StatisticsResponse {
    private long totalApplications;
    private long pendingCount;
    private long confirmedCount;
    private long paidCount;
    private long rejectedCount;
}

package com.costlink.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.costlink.config.UserPrincipal;
import com.costlink.dto.ApiResponse;
import com.costlink.dto.ReimbursementResponse;
import com.costlink.dto.StatisticsResponse;
import com.costlink.entity.AdminConfig;
import com.costlink.service.OcrService;
import com.costlink.service.ReimbursementService;
import com.costlink.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final ReimbursementService reimbursementService;
    private final UserService userService;
    private final OcrService ocrService;

    @GetMapping("/statistics")
    public ApiResponse<StatisticsResponse> getStatistics() {
        return ApiResponse.success(reimbursementService.getStatistics());
    }

    @GetMapping("/reimbursements")
    public ApiResponse<IPage<ReimbursementResponse>> getAllReimbursements(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        IPage<ReimbursementResponse> result = reimbursementService.getAllReimbursements(page, size, status);
        return ApiResponse.success(result);
    }

    @PutMapping("/reimbursement/{id}/confirm")
    public ApiResponse<Void> confirmReimbursement(@PathVariable Long id) {
        reimbursementService.confirmReimbursement(id);
        return ApiResponse.success("确认成功", null);
    }

    @PutMapping("/reimbursement/{id}/reject")
    public ApiResponse<Void> rejectReimbursement(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String reason = body.get("reason");
        reimbursementService.rejectReimbursement(id, reason);
        return ApiResponse.success("驳回成功", null);
    }

    @PostMapping("/reimbursement/{id}/pay")
    public ApiResponse<Void> markAsPaid(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        reimbursementService.markAsPaid(id, principal.getId());
        return ApiResponse.success("标记付款成功", null);
    }

    @DeleteMapping("/reimbursements")
    public ApiResponse<Void> deleteReimbursements(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        reimbursementService.deleteReimbursements(ids);
        return ApiResponse.success("删除成功", null);
    }

    @PutMapping("/config/alipay")
    public ApiResponse<Void> updatePayAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {
        String alipayPayAccount = body.get("alipayPayAccount");
        userService.updateAdminPayAccount(principal.getId(), alipayPayAccount);
        return ApiResponse.success("付款账号配置成功", null);
    }

    @GetMapping("/config")
    public ApiResponse<AdminConfig> getConfig(@AuthenticationPrincipal UserPrincipal principal) {
        AdminConfig config = userService.getAdminConfig(principal.getId());
        return ApiResponse.success(config);
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> exportImages(@RequestBody Map<String, List<Long>> body) throws IOException {
        List<Long> ids = body.get("ids");
        byte[] zipData = reimbursementService.exportImages(ids, ocrService.getUploadPath());

        String filename = "reimbursements_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".zip";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zipData);
    }
}

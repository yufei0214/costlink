package com.costlink.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.costlink.config.UserPrincipal;
import com.costlink.dto.*;
import com.costlink.entity.Reimbursement;
import com.costlink.service.OcrService;
import com.costlink.service.ReimbursementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/reimbursement")
@RequiredArgsConstructor
public class ReimbursementController {

    private final ReimbursementService reimbursementService;
    private final OcrService ocrService;

    @PostMapping("/upload")
    public ApiResponse<UploadResponse> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        String imagePath = ocrService.saveFile(file);
        BigDecimal amount = ocrService.recognizeAmount(imagePath);
        return ApiResponse.success(new UploadResponse(imagePath, file.getOriginalFilename(), amount));
    }

    @PostMapping
    public ApiResponse<Reimbursement> createReimbursement(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ReimbursementRequest request) {
        Reimbursement reimbursement = reimbursementService.createReimbursement(principal.getId(), request);
        return ApiResponse.success("报销申请提交成功", reimbursement);
    }

    @GetMapping
    public ApiResponse<IPage<ReimbursementResponse>> getMyReimbursements(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        IPage<ReimbursementResponse> result = reimbursementService.getUserReimbursements(principal.getId(), page, size);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<ReimbursementResponse> getReimbursementDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        ReimbursementResponse response = reimbursementService.getReimbursementDetail(id);
        // Non-admin can only view their own reimbursements
        if (!principal.isAdmin() && !response.getUserId().equals(principal.getId())) {
            return ApiResponse.error(403, "没有权限查看此报销记录");
        }
        return ApiResponse.success(response);
    }
}

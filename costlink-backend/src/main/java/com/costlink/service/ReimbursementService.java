package com.costlink.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.costlink.dto.ReimbursementRequest;
import com.costlink.dto.ReimbursementResponse;
import com.costlink.dto.StatisticsResponse;
import com.costlink.entity.Reimbursement;
import com.costlink.entity.ReimbursementImage;
import com.costlink.entity.User;
import com.costlink.exception.BusinessException;
import com.costlink.mapper.ReimbursementImageMapper;
import com.costlink.mapper.ReimbursementMapper;
import com.costlink.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class ReimbursementService {

    private final ReimbursementMapper reimbursementMapper;
    private final ReimbursementImageMapper imageMapper;
    private final UserMapper userMapper;

    @Transactional
    public Reimbursement createReimbursement(Long userId, ReimbursementRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getAlipayAccount() == null || user.getAlipayAccount().isEmpty()) {
            throw new BusinessException("请先设置支付宝收款账号");
        }

        Reimbursement reimbursement = new Reimbursement();
        reimbursement.setUserId(userId);
        reimbursement.setTotalAmount(request.getTotalAmount());
        reimbursement.setVpnStartDate(request.getVpnStartDate());
        reimbursement.setVpnEndDate(request.getVpnEndDate());
        reimbursement.setStatus("PENDING");
        reimbursementMapper.insert(reimbursement);

        if (request.getImages() != null) {
            int order = 0;
            for (ReimbursementRequest.ImageInfo imageInfo : request.getImages()) {
                ReimbursementImage image = new ReimbursementImage();
                image.setReimbursementId(reimbursement.getId());
                image.setImagePath(imageInfo.getImagePath());
                image.setOriginalName(imageInfo.getOriginalName());
                image.setOcrAmount(imageInfo.getOcrAmount());
                image.setSortOrder(order++);
                imageMapper.insert(image);
            }
        }

        return reimbursement;
    }

    public IPage<ReimbursementResponse> getUserReimbursements(Long userId, int page, int size) {
        Page<Reimbursement> pageParam = new Page<>(page, size);
        IPage<Reimbursement> result = reimbursementMapper.selectByUserIdWithUser(pageParam, userId);
        return result.convert(this::toResponse);
    }

    public IPage<ReimbursementResponse> getAllReimbursements(int page, int size, String status) {
        Page<Reimbursement> pageParam = new Page<>(page, size);
        IPage<Reimbursement> result;
        if (status != null && !status.isEmpty()) {
            result = reimbursementMapper.selectByStatusWithUser(pageParam, status);
        } else {
            result = reimbursementMapper.selectAllWithUser(pageParam);
        }
        return result.convert(this::toResponse);
    }

    public ReimbursementResponse getReimbursementDetail(Long id) {
        Reimbursement reimbursement = reimbursementMapper.selectByIdWithUser(id);
        if (reimbursement == null) {
            throw new BusinessException("报销记录不存在");
        }
        return toResponse(reimbursement);
    }

    @Transactional
    public void confirmReimbursement(Long id) {
        Reimbursement reimbursement = reimbursementMapper.selectById(id);
        if (reimbursement == null) {
            throw new BusinessException("报销记录不存在");
        }
        if (!"PENDING".equals(reimbursement.getStatus())) {
            throw new BusinessException("只能确认待审核的报销申请");
        }
        reimbursement.setStatus("CONFIRMED");
        reimbursementMapper.updateById(reimbursement);
    }

    @Transactional
    public void rejectReimbursement(Long id, String reason) {
        Reimbursement reimbursement = reimbursementMapper.selectById(id);
        if (reimbursement == null) {
            throw new BusinessException("报销记录不存在");
        }
        if (!"PENDING".equals(reimbursement.getStatus())) {
            throw new BusinessException("只能驳回待审核的报销申请");
        }
        reimbursement.setStatus("REJECTED");
        reimbursement.setRejectReason(reason);
        reimbursementMapper.updateById(reimbursement);
    }

    @Transactional
    public void markAsPaid(Long id, Long adminId) {
        Reimbursement reimbursement = reimbursementMapper.selectById(id);
        if (reimbursement == null) {
            throw new BusinessException("报销记录不存在");
        }
        if (!"CONFIRMED".equals(reimbursement.getStatus())) {
            throw new BusinessException("只能支付已确认的报销申请");
        }
        reimbursement.setStatus("PAID");
        reimbursement.setPaidAt(LocalDateTime.now());
        reimbursement.setPaidBy(adminId);
        reimbursementMapper.updateById(reimbursement);
    }

    public StatisticsResponse getStatistics() {
        long total = reimbursementMapper.selectCount(null);
        long pending = reimbursementMapper.countByStatus("PENDING");
        long confirmed = reimbursementMapper.countByStatus("CONFIRMED");
        long paid = reimbursementMapper.countByStatus("PAID");
        long rejected = reimbursementMapper.countByStatus("REJECTED");
        return new StatisticsResponse(total, pending, confirmed, paid, rejected);
    }

    public byte[] exportImages(List<Long> ids, String uploadPath) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

            for (Long id : ids) {
                Reimbursement reimbursement = reimbursementMapper.selectByIdWithUser(id);
                if (reimbursement == null || !"PAID".equals(reimbursement.getStatus())) {
                    continue;
                }

                List<ReimbursementImage> images = imageMapper.selectList(
                    new LambdaQueryWrapper<ReimbursementImage>()
                        .eq(ReimbursementImage::getReimbursementId, id)
                        .orderByAsc(ReimbursementImage::getSortOrder)
                );

                String dateStr = reimbursement.getCreatedAt().format(formatter);
                String displayName = reimbursement.getDisplayName() != null ?
                    reimbursement.getDisplayName() : reimbursement.getUsername();

                int seq = 1;
                for (ReimbursementImage image : images) {
                    Path imagePath = Paths.get(uploadPath, image.getImagePath());
                    if (Files.exists(imagePath)) {
                        String ext = getFileExtension(image.getOriginalName());
                        String entryName = String.format("%s_%s_%d%s", displayName, dateStr, seq++, ext);
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(imagePath, zos);
                        zos.closeEntry();
                    }
                }
            }
        }
        return baos.toByteArray();
    }

    private String getFileExtension(String filename) {
        if (filename == null) return ".jpg";
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot) : ".jpg";
    }

    private ReimbursementResponse toResponse(Reimbursement r) {
        ReimbursementResponse response = new ReimbursementResponse();
        response.setId(r.getId());
        response.setUserId(r.getUserId());
        response.setUsername(r.getUsername());
        response.setDisplayName(r.getDisplayName());
        response.setAlipayAccount(r.getAlipayAccount());
        response.setTotalAmount(r.getTotalAmount());
        response.setVpnStartDate(r.getVpnStartDate());
        response.setVpnEndDate(r.getVpnEndDate());
        response.setStatus(r.getStatus());
        response.setRejectReason(r.getRejectReason());
        response.setPaidAt(r.getPaidAt());
        response.setPaidBy(r.getPaidBy());
        response.setCreatedAt(r.getCreatedAt());

        List<ReimbursementImage> images = imageMapper.selectList(
            new LambdaQueryWrapper<ReimbursementImage>()
                .eq(ReimbursementImage::getReimbursementId, r.getId())
                .orderByAsc(ReimbursementImage::getSortOrder)
        );

        response.setImages(images.stream().map(img -> {
            ReimbursementResponse.ImageInfo info = new ReimbursementResponse.ImageInfo();
            info.setId(img.getId());
            info.setImagePath(img.getImagePath());
            info.setOriginalName(img.getOriginalName());
            info.setOcrAmount(img.getOcrAmount());
            info.setSortOrder(img.getSortOrder());
            return info;
        }).collect(Collectors.toList()));

        return response;
    }
}

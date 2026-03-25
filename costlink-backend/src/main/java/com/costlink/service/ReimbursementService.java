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

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        reimbursement.setReimbursementMonth(request.getReimbursementMonth());
        reimbursement.setRemark(request.getRemark());
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

    public IPage<ReimbursementResponse> getAllReimbursements(int page, int size, String status, String username, String month) {
        Page<Reimbursement> pageParam = new Page<>(page, size);
        String statusParam = (status != null && !status.isEmpty()) ? status : null;
        String usernameParam = (username != null && !username.isEmpty()) ? "%" + username + "%" : null;
        String monthParam = (month != null && !month.isEmpty()) ? month : null;
        IPage<Reimbursement> result = reimbursementMapper.selectWithFilters(pageParam, statusParam, usernameParam, monthParam);
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

    @Transactional
    public void deleteReimbursements(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的记录");
        }
        List<Reimbursement> list = reimbursementMapper.selectBatchIds(ids);
        for (Reimbursement r : list) {
            if (!"REJECTED".equals(r.getStatus()) && !"PAID".equals(r.getStatus())) {
                throw new BusinessException("只能删除已驳回或已付款的记录（ID: " + r.getId() + "）");
            }
        }
        imageMapper.delete(new LambdaQueryWrapper<ReimbursementImage>()
                .in(ReimbursementImage::getReimbursementId, ids));
        reimbursementMapper.deleteBatchIds(ids);
    }

    public StatisticsResponse getStatistics() {
        long total = reimbursementMapper.selectCount(null);
        long pending = reimbursementMapper.countByStatus("PENDING");
        long confirmed = reimbursementMapper.countByStatus("CONFIRMED");
        long paid = reimbursementMapper.countByStatus("PAID");
        long rejected = reimbursementMapper.countByStatus("REJECTED");
        return new StatisticsResponse(total, pending, confirmed, paid, rejected);
    }

    public byte[] exportReimbursements(List<Long> ids, String uploadPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("报销汇总");
            XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Data cell style
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setWrapText(true);

            // Header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"真实姓名", "报销月份", "报销总金额", "备注说明", "支付截图"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Column widths
            sheet.setColumnWidth(0, 15 * 256);
            sheet.setColumnWidth(1, 12 * 256);
            sheet.setColumnWidth(2, 15 * 256);
            sheet.setColumnWidth(3, 25 * 256);
            sheet.setColumnWidth(4, 60 * 256);

            int rowIndex = 1;
            for (Long id : ids) {
                Reimbursement reimbursement = reimbursementMapper.selectByIdWithUser(id);
                if (reimbursement == null ||
                    (!"PAID".equals(reimbursement.getStatus()) && !"CONFIRMED".equals(reimbursement.getStatus()))) {
                    continue;
                }

                String displayName = reimbursement.getAlipayAccount() != null ?
                    reimbursement.getAlipayAccount() : (reimbursement.getDisplayName() != null ?
                    reimbursement.getDisplayName() : reimbursement.getUsername());
                String month = reimbursement.getReimbursementMonth() != null ?
                    reimbursement.getReimbursementMonth() : "";

                List<ReimbursementImage> images = imageMapper.selectList(
                    new LambdaQueryWrapper<ReimbursementImage>()
                        .eq(ReimbursementImage::getReimbursementId, id)
                        .orderByAsc(ReimbursementImage::getSortOrder)
                );

                Row row = sheet.createRow(rowIndex);

                Cell nameCell = row.createCell(0);
                nameCell.setCellValue(displayName);
                nameCell.setCellStyle(dataStyle);

                Cell monthCell = row.createCell(1);
                monthCell.setCellValue(month);
                monthCell.setCellStyle(dataStyle);

                Cell amountCell = row.createCell(2);
                amountCell.setCellValue(reimbursement.getTotalAmount().doubleValue());
                amountCell.setCellStyle(dataStyle);

                Cell remarkCell = row.createCell(3);
                remarkCell.setCellValue(reimbursement.getRemark() != null ? reimbursement.getRemark() : "");
                remarkCell.setCellStyle(dataStyle);

                // Embed images in the "支付截图" column
                int imgCol = 4;
                int imageCount = 0;
                for (int i = 0; i < images.size(); i++) {
                    ReimbursementImage image = images.get(i);
                    Path imagePath = Paths.get(uploadPath, image.getImagePath());
                    if (!Files.exists(imagePath)) continue;

                    byte[] imageBytes = Files.readAllBytes(imagePath);
                    int pictureType = getPictureType(image.getOriginalName());
                    int pictureIdx = workbook.addPicture(imageBytes, pictureType);

                    // Each image occupies one column, side by side
                    int col = imgCol + imageCount;
                    XSSFClientAnchor anchor = new XSSFClientAnchor(
                        Units.EMU_PER_PIXEL, Units.EMU_PER_PIXEL,
                        0, 0,
                        col, rowIndex, col + 1, rowIndex + 1
                    );
                    anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
                    drawing.createPicture(anchor, pictureIdx);

                    // Set column width for extra image columns
                    if (imageCount > 0) {
                        sheet.setColumnWidth(col, 60 * 256);
                    }
                    imageCount++;
                }

                // Set row height to accommodate images
                row.setHeightInPoints(150);
                rowIndex++;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private int getPictureType(String filename) {
        if (filename == null) return Workbook.PICTURE_TYPE_JPEG;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return Workbook.PICTURE_TYPE_PNG;
        return Workbook.PICTURE_TYPE_JPEG;
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
        response.setReimbursementMonth(r.getReimbursementMonth());
        response.setRemark(r.getRemark());
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

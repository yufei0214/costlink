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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class ReimbursementService {

    private final ReimbursementMapper reimbursementMapper;
    private final ReimbursementImageMapper imageMapper;
    private final UserMapper userMapper;
    private final UserService userService;

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

    public IPage<ReimbursementResponse> getAllReimbursements(int page, int size, String status, String username, String month, String department) {
        Page<Reimbursement> pageParam = new Page<>(page, size);
        String statusParam = (status != null && !status.isEmpty()) ? status : null;
        String usernameParam = (username != null && !username.isEmpty()) ? "%" + username + "%" : null;
        String monthParam = (month != null && !month.isEmpty()) ? month : null;
        String departmentParam = (department != null && !department.isEmpty()) ? department : null;
        IPage<Reimbursement> result = reimbursementMapper.selectWithFilters(pageParam, statusParam, usernameParam, monthParam, departmentParam);
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
    public void confirmReimbursement(Long id, String department) {
        Reimbursement reimbursement = reimbursementMapper.selectById(id);
        if (reimbursement == null) {
            throw new BusinessException("报销记录不存在");
        }
        if (!"PENDING".equals(reimbursement.getStatus())) {
            throw new BusinessException("只能确认待审核的报销申请");
        }
        if (department != null && !department.isEmpty()) {
            userService.updateDepartment(reimbursement.getUserId(), department);
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
    public void markAsPaidBatch(List<Long> ids, Long adminId) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要标记的记录");
        }
        List<Reimbursement> list = reimbursementMapper.selectBatchIds(ids);
        if (list.size() != ids.size()) {
            throw new BusinessException("部分记录不存在");
        }
        for (Reimbursement r : list) {
            if (!"CONFIRMED".equals(r.getStatus())) {
                throw new BusinessException("只能支付已确认的报销申请（ID: " + r.getId() + "）");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (Reimbursement r : list) {
            r.setStatus("PAID");
            r.setPaidAt(now);
            r.setPaidBy(adminId);
            reimbursementMapper.updateById(r);
        }
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
        // Load records and group by department (null/empty → "未分组")
        Map<String, List<Reimbursement>> groups = new LinkedHashMap<>();
        for (Long id : ids) {
            Reimbursement r = reimbursementMapper.selectByIdWithUser(id);
            if (r == null ||
                (!"PAID".equals(r.getStatus()) && !"CONFIRMED".equals(r.getStatus()))) {
                continue;
            }
            String dept = (r.getDepartment() != null && !r.getDepartment().isEmpty())
                ? r.getDepartment() : "未分组";
            groups.computeIfAbsent(dept, k -> new ArrayList<>()).add(r);
        }

        ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipOut)) {
            for (Map.Entry<String, List<Reimbursement>> entry : groups.entrySet()) {
                byte[] xlsx = buildGroupExcel(entry.getValue(), uploadPath);
                zos.putNextEntry(new ZipEntry(sanitizeFilename(entry.getKey()) + ".xlsx"));
                zos.write(xlsx);
                zos.closeEntry();
            }
        }
        return zipOut.toByteArray();
    }

    private byte[] buildGroupExcel(List<Reimbursement> records, String uploadPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("报销汇总");
            XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setWrapText(true);

            Row headerRow = sheet.createRow(0);
            String[] headers = {"真实姓名", "所属组", "报销月份", "报销总金额", "备注说明", "支付截图"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            sheet.setColumnWidth(0, 15 * 256);
            sheet.setColumnWidth(1, 18 * 256);
            sheet.setColumnWidth(2, 12 * 256);
            sheet.setColumnWidth(3, 15 * 256);
            sheet.setColumnWidth(4, 25 * 256);
            sheet.setColumnWidth(5, 60 * 256);

            int rowIndex = 1;
            java.math.BigDecimal groupTotal = java.math.BigDecimal.ZERO;
            for (Reimbursement reimbursement : records) {
                if (reimbursement.getTotalAmount() != null) {
                    groupTotal = groupTotal.add(reimbursement.getTotalAmount());
                }
                String displayName = reimbursement.getAlipayAccount() != null ?
                    reimbursement.getAlipayAccount() : (reimbursement.getDisplayName() != null ?
                    reimbursement.getDisplayName() : reimbursement.getUsername());
                String month = reimbursement.getReimbursementMonth() != null ?
                    reimbursement.getReimbursementMonth() : "";
                String department = reimbursement.getDepartment() != null ?
                    reimbursement.getDepartment() : "";

                List<ReimbursementImage> images = imageMapper.selectList(
                    new LambdaQueryWrapper<ReimbursementImage>()
                        .eq(ReimbursementImage::getReimbursementId, reimbursement.getId())
                        .orderByAsc(ReimbursementImage::getSortOrder)
                );

                Row row = sheet.createRow(rowIndex);

                Cell nameCell = row.createCell(0);
                nameCell.setCellValue(displayName);
                nameCell.setCellStyle(dataStyle);

                Cell deptCell = row.createCell(1);
                deptCell.setCellValue(department);
                deptCell.setCellStyle(dataStyle);

                Cell monthCell = row.createCell(2);
                monthCell.setCellValue(month);
                monthCell.setCellStyle(dataStyle);

                Cell amountCell = row.createCell(3);
                amountCell.setCellValue(reimbursement.getTotalAmount().doubleValue());
                amountCell.setCellStyle(dataStyle);

                Cell remarkCell = row.createCell(4);
                remarkCell.setCellValue(reimbursement.getRemark() != null ? reimbursement.getRemark() : "");
                remarkCell.setCellStyle(dataStyle);

                int imgCol = 5;
                int imageCount = 0;
                for (int i = 0; i < images.size(); i++) {
                    ReimbursementImage image = images.get(i);
                    Path imagePath = Paths.get(uploadPath, image.getImagePath());
                    if (!Files.exists(imagePath)) continue;

                    byte[] imageBytes = Files.readAllBytes(imagePath);
                    int pictureType = getPictureType(image.getOriginalName());
                    int pictureIdx = workbook.addPicture(imageBytes, pictureType);

                    int col = imgCol + imageCount;
                    XSSFClientAnchor anchor = new XSSFClientAnchor(
                        Units.EMU_PER_PIXEL, Units.EMU_PER_PIXEL,
                        0, 0,
                        col, rowIndex, col + 1, rowIndex + 1
                    );
                    anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
                    drawing.createPicture(anchor, pictureIdx);

                    if (imageCount > 0) {
                        sheet.setColumnWidth(col, 60 * 256);
                    }
                    imageCount++;
                }

                row.setHeightInPoints(150);
                rowIndex++;
            }

            // Totals row
            Row totalRow = sheet.createRow(rowIndex);
            Cell totalLabel = totalRow.createCell(0);
            totalLabel.setCellValue("合计");
            totalLabel.setCellStyle(headerStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIndex, rowIndex, 0, 2));
            Cell totalCell = totalRow.createCell(3);
            totalCell.setCellValue(groupTotal.doubleValue());
            totalCell.setCellStyle(headerStyle);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
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
        response.setDepartment(r.getDepartment());
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

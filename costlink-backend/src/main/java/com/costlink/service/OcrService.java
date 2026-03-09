package com.costlink.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OcrService {

    @Value("${app.upload.path:/app/uploads}")
    private String uploadPath;

    @Value("${app.ocr.data-path:/usr/share/tesseract-ocr/4.00/tessdata}")
    private String tessDataPath;

    @Value("${app.ocr.language:chi_sim+eng}")
    private String language;

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "(?:(?:\\u00a5|CNY|RMB|\\u5143)?\\s*)?([0-9]+(?:[,，][0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*(?:\\u5143|CNY|RMB)?",
        Pattern.CASE_INSENSITIVE
    );

    public String saveFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String newFilename = UUID.randomUUID().toString() + ext;
        String datePath = java.time.LocalDate.now().toString().replace("-", "/");
        String relativePath = datePath + "/" + newFilename;

        Path fullPath = Paths.get(uploadPath, relativePath);
        Files.createDirectories(fullPath.getParent());
        Files.copy(file.getInputStream(), fullPath);

        return relativePath;
    }

    public BigDecimal recognizeAmount(String relativePath) {
        try {
            Path imagePath = Paths.get(uploadPath, relativePath);
            if (!Files.exists(imagePath)) {
                log.warn("Image file not found: {}", imagePath);
                return null;
            }

            BufferedImage image = ImageIO.read(imagePath.toFile());
            if (image == null) {
                log.warn("Cannot read image: {}", imagePath);
                return null;
            }

            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(tessDataPath);
            tesseract.setLanguage(language);
            tesseract.setPageSegMode(6);

            String text = tesseract.doOCR(image);
            log.info("OCR Result: {}", text);

            return extractAmount(text);
        } catch (TesseractException | IOException e) {
            log.error("OCR recognition failed", e);
            return null;
        }
    }

    private BigDecimal extractAmount(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        BigDecimal maxAmount = null;
        Matcher matcher = AMOUNT_PATTERN.matcher(text);

        while (matcher.find()) {
            try {
                String amountStr = matcher.group(1).replace(",", "").replace("，", "");
                BigDecimal amount = new BigDecimal(amountStr);
                if (amount.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(new BigDecimal("100000")) < 0) {
                    if (maxAmount == null || amount.compareTo(maxAmount) > 0) {
                        maxAmount = amount;
                    }
                }
            } catch (NumberFormatException e) {
                // Skip invalid number
            }
        }

        return maxAmount;
    }

    public String getUploadPath() {
        return uploadPath;
    }
}

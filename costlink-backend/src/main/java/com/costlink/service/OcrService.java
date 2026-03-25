package com.costlink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OcrService {

    @Value("${app.upload.path:/app/uploads}")
    private String uploadPath;

    @Value("${app.ocr.api-key:}")
    private String apiKey;

    @Value("${app.ocr.model:qwen-vl-plus}")
    private String model;

    @Value("${app.ocr.endpoint:https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation}")
    private String endpoint;

    @Value("${app.ocr.timeout:30}")
    private int timeoutSeconds;

    private RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PROMPT = "请仔细查看这张图片，这是一张消费/购买截图。请识别并提取图片中的实际支付金额（实付款金额）。只返回一个纯数字金额（例如：29.90），不要包含货币符号、单位或任何其他文字说明。如果图片中无法识别到支付金额，请只返回 null。";

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "(?:(?:\\u00a5|CNY|RMB|\\u5143)?\\s*)?([0-9]+(?:[,，][0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*(?:\\u5143|CNY|RMB)?",
        Pattern.CASE_INSENSITIVE
    );

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("QWEN_API_KEY is not configured. Image amount recognition will be unavailable.");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder()
            .baseUrl(endpoint)
            .requestFactory(factory)
            .build();
    }

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
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Qwen API key not configured, skipping amount recognition");
            return null;
        }

        try {
            Path imagePath = Paths.get(uploadPath, relativePath);
            if (!Files.exists(imagePath)) {
                log.warn("Image file not found: {}", imagePath);
                return null;
            }

            // 1. Encode image as base64 data URL
            byte[] imageBytes = Files.readAllBytes(imagePath);
            String mimeType = Files.probeContentType(imagePath);
            if (mimeType == null) {
                mimeType = "image/png";
            }
            String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

            // 2. Build DashScope request
            Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", Map.of(
                    "messages", List.of(
                        Map.of(
                            "role", "user",
                            "content", List.of(
                                Map.of("image", dataUrl),
                                Map.of("text", PROMPT)
                            )
                        )
                    )
                ),
                "parameters", Map.of("result_format", "message")
            );

            // 3. Call Qwen VL API (retry up to 2 times on transient I/O errors)
            String responseBody = null;
            int maxRetries = 2;
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    responseBody = restClient.post()
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + apiKey)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);
                    break;
                } catch (org.springframework.web.client.ResourceAccessException e) {
                    if (attempt < maxRetries) {
                        log.warn("Qwen API request failed (attempt {}/{}), retrying: {}", attempt + 1, maxRetries + 1, e.getMessage());
                        Thread.sleep(1000L * (attempt + 1));
                    } else {
                        throw e;
                    }
                }
            }

            // 4. Parse response
            String responseText = extractResponseText(responseBody);
            log.info("Qwen VL result for {}: {}", relativePath, responseText);

            if (responseText == null || responseText.isBlank() || "null".equalsIgnoreCase(responseText.trim())) {
                return null;
            }

            // Try direct parse first
            try {
                BigDecimal amount = new BigDecimal(responseText.trim());
                if (amount.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(new BigDecimal("100000")) < 0) {
                    return amount;
                }
            } catch (NumberFormatException ignored) {
            }

            // Fall back to regex extraction
            return extractAmount(responseText);

        } catch (Exception e) {
            log.error("Qwen VL recognition failed for {}", relativePath, e);
            return null;
        }
    }

    private String extractResponseText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("output").path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode content = choices.get(0).path("message").path("content");
                if (content.isArray() && !content.isEmpty()) {
                    return content.get(0).path("text").asText(null);
                }
                if (content.isTextual()) {
                    return content.asText(null);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Qwen API response: {}", responseBody, e);
        }
        return null;
    }

    private BigDecimal extractAmount(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        BigDecimal maxAmount = null;
        Matcher matcher = AMOUNT_PATTERN.matcher(text);

        while (matcher.find()) {
            try {
                String amountStr = matcher.group(1).replace(",", "").replace("\uff0c", "");
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

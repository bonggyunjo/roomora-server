package com.roomora.server.global.storage;

import com.roomora.server.global.exception.BusinessException;
import com.roomora.server.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * S3 / Cloudflare R2 연동 전 로컬·CI 개발용 Presigned URL 생성기.
 * 추후 실제 S3Presigner 구현체로 @Primary 교체 가능하다.
 */
@Service
public class LocalStubPresignedUploadService implements PresignedUploadService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    @Value("${app.storage.public-base-url:https://cdn.roomora.local}")
    private String publicBaseUrl;

    @Override
    public PresignedUrlResult generatePresignedUrl(String fileName, String contentType, String domain) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_UPLOAD_REQUEST);
        }
        String sanitizedDomain = (domain != null ? domain : "misc").toLowerCase().replaceAll("[^a-z0-9_-]", "");
        String ext = extractExtension(fileName);
        String key = sanitizedDomain + "/" + UUID.randomUUID() + ext;
        return new PresignedUrlResult(
                publicBaseUrl + "/upload/" + key + "?signature=stub",
                publicBaseUrl + "/" + key
        );
    }

    private String extractExtension(String fileName) {
        if (fileName == null) {
            return ".jpg";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return ".jpg";
        }
        String ext = fileName.substring(dot).toLowerCase();
        return ext.matches("\\.(jpg|jpeg|png|webp)") ? ext : ".jpg";
    }
}

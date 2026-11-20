package com.roomora.server.global.storage;

public interface PresignedUploadService {

    PresignedUrlResult generatePresignedUrl(String fileName, String contentType, String domain);

    record PresignedUrlResult(String uploadUrl, String fileUrl) {
    }
}

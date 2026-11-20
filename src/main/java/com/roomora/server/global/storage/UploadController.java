package com.roomora.server.global.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final PresignedUploadService presignedUploadService;

    public record PresignedUrlRequest(
            @NotBlank String fileName,
            @NotBlank String contentType,
            @NotBlank String domain
    ) {
    }

    @PostMapping("/presigned-url")
    public ResponseEntity<PresignedUploadService.PresignedUrlResult> createPresignedUrl(
            @Valid @RequestBody PresignedUrlRequest request) {
        return ResponseEntity.ok(
                presignedUploadService.generatePresignedUrl(
                        request.fileName(), request.contentType(), request.domain()
                )
        );
    }
}

package com.roomora.server.domain.rag;

import com.roomora.server.domain.rag.dto.RagDtos.*;
import com.roomora.server.global.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RagSearchController {

    private final RagSearchService ragSearchService;
    private final RagIndexingService ragIndexingService;

    @PostMapping("/api/search/rag")
    public ResponseEntity<RagSearchResponse> search(
            @Valid @RequestBody RagSearchRequest request,
            @AuthenticationPrincipal CustomUserDetails user,
            HttpServletRequest httpRequest) {
        String callerKey = user != null ? "u:" + user.getUserId() : "ip:" + httpRequest.getRemoteAddr();
        return ResponseEntity.ok(ragSearchService.search(request, callerKey));
    }

    @PostMapping("/api/admin/rag/reindex")
    public ResponseEntity<ReindexResponse> reindexAll() {
        int count = ragIndexingService.reindexAllGuidesAndRecipes();
        return ResponseEntity.ok(new ReindexResponse(count));
    }
}

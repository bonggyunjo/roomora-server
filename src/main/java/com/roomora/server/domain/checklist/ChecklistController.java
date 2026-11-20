package com.roomora.server.domain.checklist;

import com.roomora.server.domain.checklist.dto.ChecklistDtos.*;
import com.roomora.server.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/checklists")
@RequiredArgsConstructor
public class ChecklistController {

    private final ChecklistService checklistService;

    @PostMapping
    public ResponseEntity<ChecklistDetailResponse> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ChecklistCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(checklistService.create(user.getUserId(), request));
    }

    @GetMapping
    public ResponseEntity<List<ChecklistSummaryResponse>> listMine(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(checklistService.listMine(user.getUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChecklistDetailResponse> getDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(checklistService.getDetail(id, user.getUserId()));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ChecklistDetailResponse> addItem(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ChecklistItemCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(checklistService.addItem(id, user.getUserId(), request));
    }

    @PatchMapping("/{id}/items/{itemId}")
    public ResponseEntity<ChecklistDetailResponse> updateItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ChecklistItemUpdateRequest request) {
        return ResponseEntity.ok(checklistService.updateItem(id, itemId, user.getUserId(), request));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<ChecklistDetailResponse> deleteItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(checklistService.deleteItem(id, itemId, user.getUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        checklistService.delete(id, user.getUserId());
        return ResponseEntity.noContent().build();
    }
}

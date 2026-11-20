package com.roomora.server.domain.mypage;

import com.roomora.server.domain.mypage.dto.SocialDtos.*;
import com.roomora.server.global.security.CustomUserDetails;
import com.roomora.server.global.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserSocialService userSocialService;

    @GetMapping("/me")
    public ResponseEntity<MyProfileResponse> getMe(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(userSocialService.getMyProfile(user.getUserId()));
    }

    @PatchMapping("/me")
    public ResponseEntity<MyProfileResponse> updateMe(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(userSocialService.updateMyProfile(user.getUserId(), request));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal CustomUserDetails user,
            HttpServletRequest request) {
        userSocialService.withdraw(user.getUserId(), JwtAuthenticationFilter.resolveToken(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}")
    public ResponseEntity<PublicUserProfileResponse> getPublicProfile(
            @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails viewer) {
        Long viewerId = viewer != null ? viewer.getUserId() : null;
        return ResponseEntity.ok(userSocialService.getPublicProfile(userId, viewerId));
    }

    @PostMapping("/{userId}/follow")
    public ResponseEntity<FollowToggleResponse> toggleFollow(
            @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(userSocialService.toggleFollow(user.getUserId(), userId));
    }

    @GetMapping("/{userId}/followers")
    public ResponseEntity<PagedListResponse<FollowUserItem>> getFollowers(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userSocialService.getFollowers(userId, page, size));
    }

    @GetMapping("/{userId}/followings")
    public ResponseEntity<PagedListResponse<FollowUserItem>> getFollowings(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userSocialService.getFollowings(userId, page, size));
    }
}

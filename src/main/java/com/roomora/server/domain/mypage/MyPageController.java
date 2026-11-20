package com.roomora.server.domain.mypage;

import com.roomora.server.domain.mypage.dto.SocialDtos.*;
import com.roomora.server.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mypage")
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;

    @GetMapping("/recipes")
    public ResponseEntity<PagedListResponse<MyRecipeItem>> getMyRecipes(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(myPageService.getMyRecipes(user.getUserId(), page, size));
    }

    @GetMapping("/contests")
    public ResponseEntity<PagedListResponse<MyContestItem>> getMyContests(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(myPageService.getMyContests(user.getUserId(), page, size));
    }

    @GetMapping("/comments")
    public ResponseEntity<PagedListResponse<MyCommentItem>> getMyComments(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(myPageService.getMyComments(user.getUserId(), page, size));
    }

    @GetMapping("/likes/recipes")
    public ResponseEntity<PagedListResponse<MyRecipeItem>> getMyLikedRecipes(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(myPageService.getMyLikedRecipes(user.getUserId(), page, size));
    }

    @GetMapping("/scraps")
    public ResponseEntity<PagedListResponse<MyScrapItem>> getMyScraps(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "RECIPE") ScrapTargetType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(myPageService.getMyScraps(user.getUserId(), type, page, size));
    }
}

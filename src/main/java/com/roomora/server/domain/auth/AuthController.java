package com.roomora.server.domain.auth;

import com.roomora.server.domain.auth.dto.AuthDtos.*;
import com.roomora.server.global.security.CustomUserDetails;
import com.roomora.server.global.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<UserSummary> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @GetMapping("/check-email")
    public ResponseEntity<AvailabilityResponse> checkEmail(@RequestParam String email) {
        return ResponseEntity.ok(new AvailabilityResponse(authService.isEmailAvailable(email)));
    }

    @GetMapping("/check-nickname")
    public ResponseEntity<AvailabilityResponse> checkNickname(@RequestParam String nickname) {
        return ResponseEntity.ok(new AvailabilityResponse(authService.isNicknameAvailable(nickname)));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/reissue")
    public ResponseEntity<AuthTokenResponse> reissue(@Valid @RequestBody ReissueRequest request) {
        return ResponseEntity.ok(authService.reissue(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal CustomUserDetails user,
            HttpServletRequest request) {
        authService.logout(user.getUserId(), JwtAuthenticationFilter.resolveToken(request));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/onboarding")
    public ResponseEntity<UserSummary> completeOnboarding(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody OnboardingRequest request) {
        return ResponseEntity.ok(authService.completeOnboarding(user.getUserId(), request));
    }
}

package com.roomora.server.domain.auth;

import com.roomora.server.domain.auth.dto.AuthDtos.*;
import com.roomora.server.domain.user.AuthProvider;
import com.roomora.server.domain.user.User;
import com.roomora.server.domain.user.UserRepository;
import com.roomora.server.domain.user.UserRole;
import com.roomora.server.global.exception.BusinessException;
import com.roomora.server.global.exception.ErrorCode;
import com.roomora.server.global.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRedisRepository tokenRepository;

    @Transactional
    public UserSummary signup(SignupRequest request) {
        if (userRepository.existsByEmailAndProviderAndDeletedAtIsNull(request.email(), AuthProvider.LOCAL)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNicknameAndDeletedAtIsNull(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .provider(AuthProvider.LOCAL)
                .role(UserRole.USER)
                .build();
        return UserSummary.from(userRepository.save(user));
    }

    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmailAndProviderAndDeletedAtIsNull(email, AuthProvider.LOCAL);
    }

    public boolean isNicknameAvailable(String nickname) {
        return !userRepository.existsByNicknameAndDeletedAtIsNull(nickname);
    }

    public AuthTokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndProviderAndDeletedAtIsNull(request.email(), AuthProvider.LOCAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(user);
    }

    public AuthTokenResponse reissue(ReissueRequest request) {
        String incomingToken = request.refreshToken();
        if (!jwtTokenProvider.validateToken(incomingToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        Claims claims = jwtTokenProvider.parseClaims(incomingToken);
        if (!"REFRESH".equals(claims.get("type", String.class))) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        Long userId = Long.parseLong(claims.getSubject());
        String savedToken = tokenRepository.findRefreshToken(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (!savedToken.equals(incomingToken)) {
            // Refresh Token reuse detected: revoke all sessions for this user
            tokenRepository.deleteRefreshToken(userId);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return issueTokens(user);
    }

    public void logout(Long userId, String accessToken) {
        tokenRepository.deleteRefreshToken(userId);
        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            tokenRepository.blacklistAccessToken(accessToken, jwtTokenProvider.getRemainingExpiration(accessToken));
        }
    }

    @Transactional
    public UserSummary completeOnboarding(Long userId, OnboardingRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!user.getNickname().equals(request.nickname())
                && userRepository.existsByNicknameAndDeletedAtIsNull(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        user.updateNickname(request.nickname());
        return UserSummary.from(user);
    }

    public AuthTokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getEmail(), user.getNickname(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        tokenRepository.saveRefreshToken(user.getId(), refreshToken, jwtTokenProvider.getRefreshExpiration());
        return new AuthTokenResponse(accessToken, refreshToken, UserSummary.from(user));
    }
}

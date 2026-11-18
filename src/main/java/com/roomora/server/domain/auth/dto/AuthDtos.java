package com.roomora.server.domain.auth.dto;

import com.roomora.server.domain.user.AuthProvider;
import com.roomora.server.domain.user.User;
import com.roomora.server.domain.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(min = 8, max = 64) String password,
            @NotBlank @Size(min = 2, max = 30) String nickname
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record ReissueRequest(
            @NotBlank String refreshToken
    ) {
    }

    public record OnboardingRequest(
            @NotBlank @Size(min = 2, max = 30) String nickname
    ) {
    }

    public record AvailabilityResponse(boolean available) {
    }

    public record UserSummary(
            Long id,
            String email,
            String nickname,
            String profileImageUrl,
            AuthProvider provider,
            UserRole role
    ) {
        public static UserSummary from(User user) {
            return new UserSummary(
                    user.getId(),
                    user.getEmail(),
                    user.getNickname(),
                    user.getProfileImageUrl(),
                    user.getProvider(),
                    user.getRole()
            );
        }
    }

    public record AuthTokenResponse(
            String accessToken,
            String refreshToken,
            UserSummary user
    ) {
    }
}

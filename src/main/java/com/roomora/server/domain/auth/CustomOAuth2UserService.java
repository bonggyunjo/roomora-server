package com.roomora.server.domain.auth;

import com.roomora.server.domain.user.AuthProvider;
import com.roomora.server.domain.user.User;
import com.roomora.server.domain.user.UserRepository;
import com.roomora.server.domain.user.UserRole;
import com.roomora.server.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        AuthProvider provider = AuthProvider.valueOf(registrationId);

        OAuthProfile profile = extractProfile(provider, oAuth2User.getAttributes());
        Optional<User> existing = userRepository.findByProviderAndProviderIdAndDeletedAtIsNull(
                provider, profile.providerId());

        boolean needsOnboarding = existing.isEmpty();
        User user = existing.orElseGet(() -> createSocialUser(provider, profile));

        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                needsOnboarding,
                oAuth2User.getAttributes()
        );
    }

    private User createSocialUser(AuthProvider provider, OAuthProfile profile) {
        String tempNickname = "room_" + UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder()
                .email(profile.email())
                .nickname(tempNickname)
                .profileImageUrl(profile.profileImageUrl())
                .provider(provider)
                .providerId(profile.providerId())
                .role(UserRole.USER)
                .build();
        return userRepository.save(user);
    }

    @SuppressWarnings("unchecked")
    private OAuthProfile extractProfile(AuthProvider provider, Map<String, Object> attributes) {
        if (provider == AuthProvider.GOOGLE) {
            String providerId = String.valueOf(attributes.get("sub"));
            String email = (String) attributes.getOrDefault("email", providerId + "@google.local");
            String picture = (String) attributes.get("picture");
            return new OAuthProfile(providerId, email, picture);
        }

        // KAKAO
        String providerId = String.valueOf(attributes.get("id"));
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.getOrDefault("kakao_account", Map.of());
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.getOrDefault("profile", Map.of());
        String email = (String) kakaoAccount.getOrDefault("email", providerId + "@kakao.local");
        String imageUrl = (String) profile.get("profile_image_url");
        return new OAuthProfile(providerId, email, imageUrl);
    }

    private record OAuthProfile(String providerId, String email, String profileImageUrl) {
    }
}

package com.roomora.server.domain.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomora.server.domain.auth.dto.AuthDtos.*;
import com.roomora.server.domain.user.AuthProvider;
import com.roomora.server.domain.user.User;
import com.roomora.server.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRedisRepository tokenRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void signupAndDuplicateCheckAndLoginFlow() throws Exception {
        SignupRequest signup = new SignupRequest("room@test.com", "password123!", "루모라유저");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("room@test.com"))
                .andExpect(jsonPath("$.nickname").value("루모라유저"));

        mockMvc.perform(get("/api/auth/check-email").param("email", "room@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isConflict());

        LoginRequest login = new LoginRequest("room@test.com", "password123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void refreshTokenRotationDetectsReuseAndRevokesSession() throws Exception {
        SignupRequest signup = new SignupRequest("rotate@test.com", "password123!", "로테이션");
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("rotate@test.com", "password123!"))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String oldRefreshToken = loginJson.get("refreshToken").asText();
        Long userId = loginJson.get("user").get("id").asLong();

        // Wait 1ms so new token differs if needed (UUID jti guarantees uniqueness)
        MvcResult reissueResult = mockMvc.perform(post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReissueRequest(oldRefreshToken))))
                .andExpect(status().isOk())
                .andReturn();

        String newRefreshToken = objectMapper.readTree(reissueResult.getResponse().getContentAsString())
                .get("refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);

        // Reuse old refresh token -> 401 and entire session revoked
        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReissueRequest(oldRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A006"));

        assertThat(tokenRepository.findRefreshToken(userId)).isEmpty();
    }

    @Test
    void unauthenticatedAndSpoofedHeaderBlockedAndLogoutBlacklistsToken() throws Exception {
        // Spoofed userId header without JWT must be rejected (401)
        mockMvc.perform(patch("/api/auth/onboarding")
                        .header("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OnboardingRequest("해커"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest("logout@test.com", "password123!", "로그아웃유저"))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("logout@test.com", "password123!"))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // Blacklisted access token must be rejected
        mockMvc.perform(patch("/api/auth/onboarding")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OnboardingRequest("새닉네임"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void softDeletedUserBlockedFromLoginAndCanResignupWithSameEmailAndNickname() throws Exception {
        SignupRequest signup = new SignupRequest("rejoin@test.com", "password123!", "재가입유저");
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmailAndProviderAndDeletedAtIsNull("rejoin@test.com", AuthProvider.LOCAL)
                .orElseThrow();
        user.softDelete();
        userRepository.save(user);

        // Deleted user cannot login
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("rejoin@test.com", "password123!"))))
                .andExpect(status().isUnauthorized());

        // Partial Unique Index allows re-signup with exact same email & nickname
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());
    }
}

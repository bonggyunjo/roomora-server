package com.roomora.server.domain.mypage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomora.server.domain.auth.dto.AuthDtos.LoginRequest;
import com.roomora.server.domain.auth.dto.AuthDtos.SignupRequest;
import com.roomora.server.domain.contest.*;
import com.roomora.server.domain.contest.dto.ContestDtos.ContestCommentCreateRequest;
import com.roomora.server.domain.contest.dto.ContestDtos.ContestPostCreateRequest;
import com.roomora.server.domain.mypage.dto.SocialDtos.ProfileUpdateRequest;
import com.roomora.server.domain.recipe.*;
import com.roomora.server.domain.recipe.dto.RecipeDtos.CommentCreateRequest;
import com.roomora.server.domain.recipe.dto.RecipeDtos.RecipeCreateRequest;
import com.roomora.server.domain.recipe.dto.RecipeDtos.RecipeItemRequest;
import com.roomora.server.domain.user.AuthProvider;
import com.roomora.server.domain.user.User;
import com.roomora.server.domain.user.UserFollowRepository;
import com.roomora.server.domain.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MyPageAndSocialIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserFollowRepository userFollowRepository;

    @Autowired
    private RecipePostRepository recipePostRepository;

    @Autowired
    private RecipeLikeRepository recipeLikeRepository;

    @Autowired
    private RecipeScrapRepository recipeScrapRepository;

    @Autowired
    private RecipeCommentRepository recipeCommentRepository;

    @Autowired
    private ContestRoundRepository contestRoundRepository;

    @Autowired
    private ContestPostRepository contestPostRepository;

    @Autowired
    private ContestVoteRepository contestVoteRepository;

    @Autowired
    private ContestScrapRepository contestScrapRepository;

    @Autowired
    private ContestCommentRepository contestCommentRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private String userAToken;
    private String userBToken;
    private Long userAId;
    private Long userBId;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabaseAndRedis();
        userAToken = signupAndLogin("usera@mypage.test", "마이페이지유저A");
        userBToken = signupAndLogin("userb@mypage.test", "마이페이지유저B");
        userAId = findUserIdByEmail("usera@mypage.test");
        userBId = findUserIdByEmail("userb@mypage.test");
    }

    @AfterEach
    void tearDown() {
        cleanDatabaseAndRedis();
    }

    private void cleanDatabaseAndRedis() {
        userFollowRepository.deleteAllInBatch();

        contestVoteRepository.deleteAllInBatch();
        contestScrapRepository.deleteAllInBatch();
        contestCommentRepository.deleteAllInBatch();
        contestPostRepository.deleteAll();
        contestRoundRepository.deleteAllInBatch();

        recipeLikeRepository.deleteAllInBatch();
        recipeScrapRepository.deleteAllInBatch();
        recipeCommentRepository.deleteAllInBatch();
        recipePostRepository.deleteAll();

        userRepository.deleteAll();

        deleteRedisKeys("auth:rt:*");
        deleteRedisKeys("auth:bl:*");
        deleteRedisKeys("contest:ranking:*");
    }

    private void deleteRedisKeys(String pattern) {
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    private String signupAndLogin(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, "password123!", nickname))))
                .andExpect(status().isCreated());

        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123!"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private Long findUserIdByEmail(String email) {
        return userRepository.findByEmailAndProviderAndDeletedAtIsNull(email, AuthProvider.LOCAL)
                .map(User::getId)
                .orElseThrow();
    }

    @Test
    void myProfileAuthUpdateDuplicateNicknameAndWithdrawInvalidatesToken() throws Exception {
        // Unauthenticated GET /api/users/me -> 401
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A004"));

        // Authenticated GET /api/users/me -> 200
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userAId))
                .andExpect(jsonPath("$.email").value("usera@mypage.test"))
                .andExpect(jsonPath("$.nickname").value("마이페이지유저A"))
                .andExpect(jsonPath("$.followerCount").value(0))
                .andExpect(jsonPath("$.followingCount").value(0));

        // PATCH /api/users/me with duplicate active nickname -> 409
        ProfileUpdateRequest dupNicknameReq = new ProfileUpdateRequest("마이페이지유저B", null, null);
        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupNicknameReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("A002"));

        // PATCH /api/users/me with invalid short nickname -> 400
        ProfileUpdateRequest invalidReq = new ProfileUpdateRequest("A", null, null);
        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());

        // Valid PATCH /api/users/me -> 200 and persisted
        ProfileUpdateRequest validUpdate = new ProfileUpdateRequest(
                "새닉네임A",
                "https://cdn.roomora.local/profile/a.png",
                "원룸 꾸미기 좋아합니다"
        );
        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새닉네임A"))
                .andExpect(jsonPath("$.profileImageUrl").value("https://cdn.roomora.local/profile/a.png"))
                .andExpect(jsonPath("$.bio").value("원룸 꾸미기 좋아합니다"));

        // DELETE /api/users/me -> 204, token blacklisted & user soft-deleted
        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isNoContent());

        // Subsequent /api/users/me with same token -> 401
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isUnauthorized());

        // Public profile for withdrawn user -> 404
        mockMvc.perform(get("/api/users/" + userAId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("A008"));
    }

    @Test
    void followToggleSelfFollowBlockedAndWithdrawnTargetReturns404() throws Exception {
        // Self-follow -> 400 (U001)
        mockMvc.perform(post("/api/users/" + userAId + "/follow")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("U001"));

        // Unauthenticated follow -> 401
        mockMvc.perform(post("/api/users/" + userBId + "/follow"))
                .andExpect(status().isUnauthorized());

        // UserA follows UserB -> following=true, followerCount=1, followingCount=1
        mockMvc.perform(post("/api/users/" + userBId + "/follow")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followerCount").value(1))
                .andExpect(jsonPath("$.followingCount").value(1));

        // Public profile shows followedByMe=true for UserA, false for anonymous
        mockMvc.perform(get("/api/users/" + userBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followedByMe").value(true))
                .andExpect(jsonPath("$.followerCount").value(1));

        mockMvc.perform(get("/api/users/" + userBId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followedByMe").value(false))
                .andExpect(jsonPath("$.followerCount").value(1));

        // UserA unfollows UserB -> following=false, counts=0
        mockMvc.perform(post("/api/users/" + userBId + "/follow")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(false))
                .andExpect(jsonPath("$.followerCount").value(0))
                .andExpect(jsonPath("$.followingCount").value(0));

        // UserB withdraws -> UserA following UserB returns 404
        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userBToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/users/" + userBId + "/follow")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("A008"));
    }

    @Test
    void concurrentFollowsSyncFollowerCountAccurately() throws Exception {
        int followerThreads = 10;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < followerThreads; i++) {
            tokens.add(signupAndLogin("follower" + i + "@mypage.test", "팔로워" + i));
        }

        ExecutorService pool = Executors.newFixedThreadPool(followerThreads);
        CountDownLatch ready = new CountDownLatch(followerThreads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (String token : tokens) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(post("/api/users/" + userAId + "/follow")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andReturn().getResponse().getStatus();
            }));
        }

        ready.await();
        start.countDown();
        for (Future<Integer> future : futures) {
            assertThat(future.get()).isEqualTo(200);
        }
        pool.shutdown();

        mockMvc.perform(get("/api/users/" + userAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followerCount").value(followerThreads));
    }

    @Test
    void followerAndFollowingPagedListsExcludeDeletedUsers() throws Exception {
        String userCToken = signupAndLogin("userc@mypage.test", "마이페이지유저C");
        Long userCId = findUserIdByEmail("userc@mypage.test");

        // B and C follow A; A follows B and C
        mockMvc.perform(post("/api/users/" + userAId + "/follow")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userBToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/" + userAId + "/follow")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userCToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/" + userBId + "/follow")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/" + userCId + "/follow")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk());

        // Paging check (size=1)
        mockMvc.perform(get("/api/users/" + userAId + "/followers?page=0&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));

        // UserC withdraws
        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userCToken))
                .andExpect(status().isNoContent());

        // Followers & followings of A must exclude withdrawn UserC
        mockMvc.perform(get("/api/users/" + userAId + "/followers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].userId").value(userBId));

        mockMvc.perform(get("/api/users/" + userAId + "/followings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].userId").value(userBId));
    }

    @Test
    void myPageEndpointsReturnAccurateSeparatedResults() throws Exception {
        // Unauthenticated /api/mypage/recipes -> 401
        mockMvc.perform(get("/api/mypage/recipes"))
                .andExpect(status().isUnauthorized());

        // Create recipes: 1 by UserA, 1 by UserB
        Long recipeAId = createRecipe(userAToken, "유저A의 레시피");
        Long recipeBId = createRecipe(userBToken, "유저B의 레시피");

        // Create active contest round & posts: 1 by UserA, 1 by UserB
        ContestRound round = contestRoundRepository.save(ContestRound.builder()
                .roundNo(1)
                .title("1회차 테스트")
                .themeDescription("원룸 인테리어")
                .status(ContestRoundStatus.ACTIVE)
                .startAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .endAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .build());

        Long contestAId = createContestPost(userAToken, round.getId(), "유저A의 출품작");
        Long contestBId = createContestPost(userBToken, round.getId(), "유저B의 출품작");

        // UserA comments on UserB's recipe and UserB's contest post
        mockMvc.perform(post("/api/recipes/" + recipeBId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentCreateRequest(null, "레시피 댓글입니다"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/contests/posts/" + contestBId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ContestCommentCreateRequest(null, "출품작 댓글입니다"))))
                .andExpect(status().isCreated());

        // UserA likes UserB's recipe
        mockMvc.perform(post("/api/recipes/" + recipeBId + "/like")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        // UserA scraps UserB's recipe AND UserB's contest post
        mockMvc.perform(post("/api/recipes/" + recipeBId + "/scrap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/contests/posts/" + contestBId + "/scrap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        // 1. GET /api/mypage/recipes
        mockMvc.perform(get("/api/mypage/recipes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(recipeAId))
                .andExpect(jsonPath("$.items[0].title").value("유저A의 레시피"));

        // 2. GET /api/mypage/contests
        mockMvc.perform(get("/api/mypage/contests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(contestAId))
                .andExpect(jsonPath("$.items[0].title").value("유저A의 출품작"));

        // 3. GET /api/mypage/comments
        MvcResult commentsRes = mockMvc.perform(get("/api/mypage/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();
        JsonNode commentItems = objectMapper.readTree(
                commentsRes.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("items");
        assertThat(commentItems).hasSize(2);
        assertThat(commentItems.get(0).get("targetType").asText()).isEqualTo("CONTEST");
        assertThat(commentItems.get(0).get("content").asText()).isEqualTo("출품작 댓글입니다");
        assertThat(commentItems.get(1).get("targetType").asText()).isEqualTo("RECIPE");
        assertThat(commentItems.get(1).get("content").asText()).isEqualTo("레시피 댓글입니다");

        // 4. GET /api/mypage/likes/recipes
        mockMvc.perform(get("/api/mypage/likes/recipes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(recipeBId))
                .andExpect(jsonPath("$.items[0].title").value("유저B의 레시피"));

        // 5. GET /api/mypage/scraps?type=RECIPE
        mockMvc.perform(get("/api/mypage/scraps?type=RECIPE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("RECIPE"))
                .andExpect(jsonPath("$.items[0].postId").value(recipeBId))
                .andExpect(jsonPath("$.items[0].title").value("유저B의 레시피"));

        // 6. GET /api/mypage/scraps?type=CONTEST
        mockMvc.perform(get("/api/mypage/scraps?type=CONTEST")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("CONTEST"))
                .andExpect(jsonPath("$.items[0].postId").value(contestBId))
                .andExpect(jsonPath("$.items[0].title").value("유저B의 출품작"));
    }

    private Long createRecipe(String token, String title) throws Exception {
        RecipeCreateRequest req = new RecipeCreateRequest(
                title, "테스트 본문", HousingType.ONE_ROOM, (short) 7, ContractType.MONTHLY,
                true, 300000, StyleTag.MODERN, "https://cdn.roomora.local/r.jpg",
                List.of("https://cdn.roomora.local/r1.jpg"),
                List.of(new RecipeItemRequest("책상", "가구", 100000, "메모"))
        );
        MvcResult res = mockMvc.perform(post("/api/recipes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createContestPost(String token, Long roundId, String title) throws Exception {
        ContestPostCreateRequest req = new ContestPostCreateRequest(
                title, "출품 설명", "https://cdn.roomora.local/c.jpg",
                List.of("https://cdn.roomora.local/c1.jpg")
        );
        MvcResult res = mockMvc.perform(post("/api/contests/rounds/" + roundId + "/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }
}

package com.roomora.server.domain.contest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomora.server.domain.auth.dto.AuthDtos.LoginRequest;
import com.roomora.server.domain.auth.dto.AuthDtos.SignupRequest;
import com.roomora.server.domain.contest.dto.ContestDtos.ContestCommentCreateRequest;
import com.roomora.server.domain.contest.dto.ContestDtos.ContestPostCreateRequest;
import com.roomora.server.domain.contest.dto.ContestDtos.ContestPostUpdateRequest;
import com.roomora.server.domain.user.AuthProvider;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContestIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ContestService contestService;

    @Autowired
    private ContestRoundRepository roundRepository;

    @Autowired
    private ContestPostRepository postRepository;

    @Autowired
    private ContestVoteRepository voteRepository;

    @Autowired
    private ContestScrapRepository scrapRepository;

    @Autowired
    private ContestCommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private String authorToken;
    private String voterToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabaseAndRedis();
        authorToken = signupAndLogin("author@contest.test", "콘테스트작가");
        voterToken = signupAndLogin("voter@contest.test", "투표유저");
    }

    @AfterEach
    void tearDown() {
        cleanDatabaseAndRedis();
    }

    private void cleanDatabaseAndRedis() {
        voteRepository.deleteAllInBatch();
        scrapRepository.deleteAllInBatch();
        commentRepository.deleteAllInBatch();
        postRepository.deleteAll();
        roundRepository.deleteAllInBatch();
        userRepository.deleteAll();

        Set<String> keys = stringRedisTemplate.keys("contest:ranking:*");
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

    private ContestRound createRound(int roundNo, ContestRoundStatus status, Instant startAt, Instant endAt) {
        return roundRepository.save(ContestRound.builder()
                .roundNo(roundNo)
                .title("제" + roundNo + "회 자취방 콘테스트")
                .themeDescription("따뜻한 무타공 인테리어")
                .status(status)
                .startAt(startAt)
                .endAt(endAt)
                .build());
    }

    private long submitContestPost(Long roundId, String token, String title, List<String> images) throws Exception {
        ContestPostCreateRequest req = new ContestPostCreateRequest(
                title,
                "콘테스트 출품 설명입니다.",
                "https://cdn.roomora.local/thumb.jpg",
                images
        );
        MvcResult res = mockMvc.perform(post("/api/contests/rounds/" + roundId + "/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void activeRoundLookupMultiImageSubmitDuplicateAndInactiveRoundValidation() throws Exception {
        Instant now = Instant.now();
        ContestRound activeRound = createRound(1, ContestRoundStatus.ACTIVE,
                now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        ContestRound upcomingRound = createRound(2, ContestRoundStatus.UPCOMING,
                now.plus(7, ChronoUnit.DAYS), now.plus(14, ChronoUnit.DAYS));
        ContestRound closedRound = createRound(3, ContestRoundStatus.CLOSED,
                now.minus(14, ChronoUnit.DAYS), now.minus(7, ChronoUnit.DAYS));

        // Active round lookup (REQ-CNT-001)
        mockMvc.perform(get("/api/contests/rounds/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(activeRound.getId()))
                .andExpect(jsonPath("$.roundNo").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Unauthenticated submission -> 401
        ContestPostCreateRequest createReq = new ContestPostCreateRequest(
                "원룸 데스크테리어",
                "간접조명과 우드 선반 조합",
                "https://cdn.roomora.local/thumb.jpg",
                List.of("https://cdn.roomora.local/1.jpg", "https://cdn.roomora.local/2.jpg")
        );
        mockMvc.perform(post("/api/contests/rounds/" + activeRound.getId() + "/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isUnauthorized());

        // Multi-image submission -> 201 (REQ-CNT-002)
        MvcResult created = mockMvc.perform(post("/api/contests/rounds/" + activeRound.getId() + "/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.voteCount").value(0))
                .andReturn();
        long postId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Duplicate submission in same round -> 409 N004
        mockMvc.perform(post("/api/contests/rounds/" + activeRound.getId() + "/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("N004"));

        // Non-owner update/delete -> 403
        ContestPostUpdateRequest updateReq = new ContestPostUpdateRequest(
                "탈취 시도", "수정 불가", null, List.of()
        );
        mockMvc.perform(patch("/api/contests/posts/" + postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + voterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // Inactive round submit (UPCOMING & CLOSED) -> 400 N002
        mockMvc.perform(post("/api/contests/rounds/" + upcomingRound.getId() + "/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + voterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("N002"));

        mockMvc.perform(post("/api/contests/rounds/" + closedRound.getId() + "/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + voterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("N002"));

        // Inactive round vote -> 400 N002
        ContestPost closedPost = postRepository.save(ContestPost.builder()
                .round(closedRound)
                .user(userRepository.findByEmailAndProviderAndDeletedAtIsNull("author@contest.test", AuthProvider.LOCAL).orElseThrow())
                .title("지난 회차 출품작")
                .content("내용")
                .build());
        mockMvc.perform(post("/api/contests/posts/" + closedPost.getId() + "/vote")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + voterToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("N002"));
    }

    @Test
    void selfVoteIsBlockedWith400N006() throws Exception {
        Instant now = Instant.now();
        ContestRound round = createRound(1, ContestRoundStatus.ACTIVE,
                now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        long postId = submitContestPost(round.getId(), authorToken, "자가투표 테스트작",
                List.of("https://cdn.roomora.local/a.jpg"));

        mockMvc.perform(post("/api/contests/posts/" + postId + "/vote")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("N006"));

        ContestPost post = postRepository.findById(postId).orElseThrow();
        assertThat(post.getVoteCount()).isZero();
    }

    @Test
    void concurrent100ThreadVotesAllowExactlyOneVoteAndSyncRedisZset() throws Exception {
        Instant now = Instant.now();
        ContestRound round = createRound(1, ContestRoundStatus.ACTIVE,
                now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        long postId = submitContestPost(round.getId(), authorToken, "동시성 테스트 출품작",
                List.of("https://cdn.roomora.local/c1.jpg"));

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Future<Integer>> futures = new ArrayList<>(threadCount);

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    MvcResult res = mockMvc.perform(post("/api/contests/posts/" + postId + "/vote")
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + voterToken))
                            .andReturn();
                    int status = res.getResponse().getStatus();
                    if (status == 200) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    }
                    return status;
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        for (Future<Integer> future : futures) {
            assertThat(future.get()).isIn(200, 409);
        }
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(99);

        ContestPost updated = postRepository.findById(postId).orElseThrow();
        assertThat(updated.getVoteCount()).isEqualTo(1);

        Double redisScore = stringRedisTemplate.opsForZSet()
                .score("contest:ranking:" + round.getId(), String.valueOf(postId));
        assertThat(redisScore).isEqualTo(1.0);
    }

    @Test
    void redisZsetRealtimeTopNRankingReturnsOrderedPosts() throws Exception {
        Instant now = Instant.now();
        ContestRound round = createRound(1, ContestRoundStatus.ACTIVE,
                now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));

        String author2Token = signupAndLogin("a2@contest.test", "작가2");
        String author3Token = signupAndLogin("a3@contest.test", "작가3");

        long postA = submitContestPost(round.getId(), authorToken, "작품 A", List.of("https://cdn.roomora.local/a.jpg"));
        long postB = submitContestPost(round.getId(), author2Token, "작품 B", List.of("https://cdn.roomora.local/b.jpg"));
        long postC = submitContestPost(round.getId(), author3Token, "작품 C", List.of("https://cdn.roomora.local/c.jpg"));

        // Post B: 3 votes, Post C: 2 votes, Post A: 1 vote
        for (int i = 1; i <= 3; i++) {
            String t = signupAndLogin("vb" + i + "@contest.test", "투표자B" + i);
            mockMvc.perform(post("/api/contests/posts/" + postB + "/vote")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + t))
                    .andExpect(status().isOk());
        }
        for (int i = 1; i <= 2; i++) {
            String t = signupAndLogin("vc" + i + "@contest.test", "투표자C" + i);
            mockMvc.perform(post("/api/contests/posts/" + postC + "/vote")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + t))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/contests/posts/" + postA + "/vote")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + voterToken))
                .andExpect(status().isOk());

        // Top 2
        mockMvc.perform(get("/api/contests/rounds/" + round.getId() + "/ranking")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].postId").value(postB))
                .andExpect(jsonPath("$[0].voteCount").value(3))
                .andExpect(jsonPath("$[1].rank").value(2))
                .andExpect(jsonPath("$[1].postId").value(postC))
                .andExpect(jsonPath("$[1].voteCount").value(2));

        // Top 10 (all 3)
        mockMvc.perform(get("/api/contests/rounds/" + round.getId() + "/ranking")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].rank").value(3))
                .andExpect(jsonPath("$[2].postId").value(postA))
                .andExpect(jsonPath("$[2].voteCount").value(1));
    }

    @Test
    void scrapToggleAnd2DepthCommentWith3rdDepthBlocked() throws Exception {
        Instant now = Instant.now();
        ContestRound round = createRound(1, ContestRoundStatus.ACTIVE,
                now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        long postId = submitContestPost(round.getId(), authorToken, "스크랩/댓글 테스트작",
                List.of("https://cdn.roomora.local/s.jpg"));

        // Scrap toggle ON -> OFF
        mockMvc.perform(post("/api/contests/posts/" + postId + "/scrap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + voterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(post("/api/contests/posts/" + postId + "/scrap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + voterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.count").value(0));

        // 1-depth comment
        MvcResult rootRes = mockMvc.perform(post("/api/contests/posts/" + postId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + voterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ContestCommentCreateRequest(null, "분위기 너무 좋네요!"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").isEmpty())
                .andReturn();
        long rootCommentId = objectMapper.readTree(rootRes.getResponse().getContentAsString()).get("id").asLong();

        // 2-depth reply
        MvcResult replyRes = mockMvc.perform(post("/api/contests/posts/" + postId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ContestCommentCreateRequest(rootCommentId, "감사합니다!"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(rootCommentId))
                .andReturn();
        long replyCommentId = objectMapper.readTree(replyRes.getResponse().getContentAsString()).get("id").asLong();

        // 3-depth reply -> 400 R003
        mockMvc.perform(post("/api/contests/posts/" + postId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + voterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ContestCommentCreateRequest(replyCommentId, "대대댓글 시도"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("R003"));

        // List comments returns 2 valid comments
        mockMvc.perform(get("/api/contests/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void closeExpiredRoundsPersistsFinalRankAndWinnersEndpointReturnsRankedList() throws Exception {
        Instant now = Instant.now();
        ContestRound expiredRound = createRound(1, ContestRoundStatus.ACTIVE,
                now.minus(8, ChronoUnit.DAYS), now.minus(1, ChronoUnit.MINUTES));
        ContestRound ongoingRound = createRound(2, ContestRoundStatus.ACTIVE,
                now.minus(1, ChronoUnit.HOURS), now.plus(6, ChronoUnit.DAYS));

        String author2Token = signupAndLogin("winner2@contest.test", "우승후보2");
        long post1 = submitContestPost(expiredRound.getId(), authorToken, "출품작 1",
                List.of("https://cdn.roomora.local/w1.jpg"));
        long post2 = submitContestPost(expiredRound.getId(), author2Token, "출품작 2",
                List.of("https://cdn.roomora.local/w2.jpg"));

        // Post 2 gets 2 votes, Post 1 gets 1 vote
        String v1 = signupAndLogin("wv1@contest.test", "마감투표1");
        String v2 = signupAndLogin("wv2@contest.test", "마감투표2");
        mockMvc.perform(post("/api/contests/posts/" + post2 + "/vote")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + v1))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/contests/posts/" + post2 + "/vote")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + v2))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/contests/posts/" + post1 + "/vote")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + voterToken))
                .andExpect(status().isOk());

        int closedCount = contestService.closeExpiredRounds(Instant.now());
        assertThat(closedCount).isEqualTo(1);

        ContestRound reloadedExpired = roundRepository.findById(expiredRound.getId()).orElseThrow();
        ContestRound reloadedOngoing = roundRepository.findById(ongoingRound.getId()).orElseThrow();
        assertThat(reloadedExpired.getStatus()).isEqualTo(ContestRoundStatus.CLOSED);
        assertThat(reloadedOngoing.getStatus()).isEqualTo(ContestRoundStatus.ACTIVE);

        ContestPost p2Entity = postRepository.findById(post2).orElseThrow();
        ContestPost p1Entity = postRepository.findById(post1).orElseThrow();
        assertThat(p2Entity.getFinalRank()).isEqualTo(1);
        assertThat(p1Entity.getFinalRank()).isEqualTo(2);

        mockMvc.perform(get("/api/contests/rounds/" + expiredRound.getId() + "/winners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].postId").value(post2))
                .andExpect(jsonPath("$[0].voteCount").value(2))
                .andExpect(jsonPath("$[1].rank").value(2))
                .andExpect(jsonPath("$[1].postId").value(post1))
                .andExpect(jsonPath("$[1].voteCount").value(1));
    }
}

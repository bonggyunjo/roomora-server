package com.roomora.server.domain.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomora.server.domain.auth.dto.AuthDtos.LoginRequest;
import com.roomora.server.domain.auth.dto.AuthDtos.SignupRequest;
import com.roomora.server.domain.checklist.AiChecklistRepository;
import com.roomora.server.domain.checklist.dto.ChecklistDtos.ChecklistCreateRequest;
import com.roomora.server.domain.checklist.dto.ChecklistDtos.ChecklistItemCreateRequest;
import com.roomora.server.domain.checklist.dto.ChecklistDtos.ChecklistItemUpdateRequest;
import com.roomora.server.domain.guide.GuideCategory;
import com.roomora.server.domain.guide.InteriorGuide;
import com.roomora.server.domain.guide.InteriorGuideRepository;
import com.roomora.server.domain.rag.RagDocumentChunkRepository.SearchFilter;
import com.roomora.server.domain.rag.dto.RagDtos.RagSearchRequest;
import com.roomora.server.domain.recipe.*;
import com.roomora.server.domain.user.AuthProvider;
import com.roomora.server.domain.user.User;
import com.roomora.server.domain.user.UserRepository;
import com.roomora.server.domain.user.UserRole;
import com.roomora.server.global.security.JwtTokenProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RagAndChecklistIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecipePostRepository recipePostRepository;

    @Autowired
    private InteriorGuideRepository guideRepository;

    @Autowired
    private AiChecklistRepository checklistRepository;

    @Autowired
    private RagIndexingService ragIndexingService;

    @Autowired
    private RagQueryParser ragQueryParser;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @SpyBean
    private RagLlmClient ragLlmClient;

    private User user;
    private String userToken;
    private String otherToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanAll();
        Mockito.reset(ragLlmClient);
        circuitBreakerRegistry.circuitBreaker("ragAi").reset();

        userToken = signupAndLogin("raguser@test.com", "래그유저");
        otherToken = signupAndLogin("otherchk@test.com", "타인유저");
        user = userRepository.findByEmailAndProviderAndDeletedAtIsNull("raguser@test.com", AuthProvider.LOCAL).orElseThrow();

        User admin = userRepository.save(User.builder()
                .email("admin@test.com")
                .password(passwordEncoder.encode("password123!"))
                .nickname("관리자")
                .provider(AuthProvider.LOCAL)
                .role(UserRole.ADMIN)
                .build());
        adminToken = jwtTokenProvider.createAccessToken(admin.getId(), admin.getEmail(), admin.getNickname(), UserRole.ADMIN);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(ragLlmClient);
        circuitBreakerRegistry.circuitBreaker("ragAi").reset();
        cleanAll();
    }

    private void cleanAll() {
        checklistRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM rag_document_chunk");
        recipePostRepository.deleteAll();
        guideRepository.deleteAll();
        userRepository.deleteAll();

        Set<String> rlKeys = stringRedisTemplate.keys("rag:rl:*");
        if (rlKeys != null && !rlKeys.isEmpty()) {
            stringRedisTemplate.delete(rlKeys);
        }
        Set<String> cacheKeys = stringRedisTemplate.keys("rag:cache:*");
        if (cacheKeys != null && !cacheKeys.isEmpty()) {
            stringRedisTemplate.delete(cacheKeys);
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

    private RecipePost createRecipe(String title, String content, HousingType housingType, int pyeong,
                                    ContractType contractType, boolean noDrill, int budget, StyleTag styleTag) {
        return recipePostRepository.save(RecipePost.builder()
                .user(user)
                .title(title)
                .content(content)
                .housingType(housingType)
                .areaPyeong((short) pyeong)
                .contractType(contractType)
                .noDrill(noDrill)
                .budget(budget)
                .styleTag(styleTag)
                .thumbnailUrl("https://cdn.roomora.test/thumb.jpg")
                .build());
    }

    private InteriorGuide createGuide(String title, GuideCategory category, String styleTag, String content) {
        return guideRepository.save(InteriorGuide.builder()
                .title(title)
                .category(category)
                .styleTag(styleTag)
                .content(content)
                .build());
    }

    @Test
    void indexingCreatesPrefixAndJsonbMetadataAndIsIdempotentOnReindex() {
        RecipePost recipe = createRecipe(
                "6평 원룸 무타공 따뜻한 우드톤",
                "전세 원룸에서 꼭꼬핀과 압축봉, 3000K 간접조명으로 따뜻하게 꾸민 사례입니다.",
                HousingType.ONE_ROOM, 6, ContractType.JEONSE, true, 450_000, StyleTag.WOOD
        );
        InteriorGuide guide = createGuide(
                "북향 원룸 3000K 전구색 조명 가이드",
                GuideCategory.LIGHTING,
                "WOOD",
                "북향 공간은 3000K 전구색 스탠드 조명으로 따뜻한 분위기를 보완하세요."
        );

        ragIndexingService.indexRecipe(recipe.getId());
        ragIndexingService.indexGuide(guide.getId());

        Integer initialCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rag_document_chunk", Integer.class);
        assertThat(initialCount).isNotNull().isGreaterThanOrEqualTo(2);

        String recipeChunkContent = jdbcTemplate.queryForObject(
                "SELECT content FROM rag_document_chunk WHERE source_type = 'RECIPE' AND source_id = ? AND chunk_index = 0",
                String.class, recipe.getId());
        assertThat(recipeChunkContent).contains("[ONE_ROOM | 6평 | JEONSE | 무타공 | 450000원 | WOOD]");

        String recipeMetaHousing = jdbcTemplate.queryForObject(
                "SELECT metadata->>'housingType' FROM rag_document_chunk WHERE source_type = 'RECIPE' AND source_id = ? AND chunk_index = 0",
                String.class, recipe.getId());
        Integer recipeMetaBudget = jdbcTemplate.queryForObject(
                "SELECT (metadata->>'budget')::int FROM rag_document_chunk WHERE source_type = 'RECIPE' AND source_id = ? AND chunk_index = 0",
                Integer.class, recipe.getId());
        assertThat(recipeMetaHousing).isEqualTo("ONE_ROOM");
        assertThat(recipeMetaBudget).isEqualTo(450_000);

        String guideChunkContent = jdbcTemplate.queryForObject(
                "SELECT content FROM rag_document_chunk WHERE source_type = 'GUIDE' AND source_id = ? AND chunk_index = 0",
                String.class, guide.getId());
        assertThat(guideChunkContent).contains("[가이드 | LIGHTING | WOOD]");

        // Re-index multiple times and verify idempotency (no duplicate rows)
        ragIndexingService.indexRecipe(recipe.getId());
        ragIndexingService.indexGuide(guide.getId());
        ragIndexingService.reindexAllGuidesAndRecipes();

        Integer afterReindexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rag_document_chunk", Integer.class);
        assertThat(afterReindexCount).isEqualTo(initialCount);
    }

    @Test
    void searchParsesNaturalQueryPreFiltersOutOfBudgetAndReturnsStructuredJson() throws Exception {
        String query = "전세 6평 원룸 무타공 50만원 따뜻하게";
        SearchFilter parsed = ragQueryParser.parseFilter(query, null);
        assertThat(parsed.housingType()).isEqualTo("ONE_ROOM");
        assertThat(parsed.isNoDrill()).isTrue();
        assertThat(parsed.maxBudget()).isEqualTo(500_000);
        assertThat(parsed.maxPyeong()).isEqualTo(6);

        RecipePost matchingRecipe = createRecipe(
                "전세 6평 원룸 무타공 따뜻한 방",
                "무타공 압축봉과 3000K 간접조명으로 45만원에 따뜻하게 완성했습니다.",
                HousingType.ONE_ROOM, 6, ContractType.JEONSE, true, 450_000, StyleTag.WOOD
        );
        RecipePost outOfBudgetApartment = createRecipe(
                "32평 아파트 500만원 모던 리모델링",
                "타공 벽선반과 대형 가구로 꾸민 500만원대 아파트 인테리어입니다.",
                HousingType.APARTMENT, 32, ContractType.OWNED, false, 5_000_000, StyleTag.MODERN
        );
        InteriorGuide guide = createGuide(
                "무타공 전세 원룸 조명 가이드",
                GuideCategory.NO_DRILL,
                "NATURAL",
                "무타공 환경에서는 스탠드 조명과 꼭꼬핀을 활용해 따뜻하게 연출할 수 있습니다."
        );

        ragIndexingService.indexRecipe(matchingRecipe.getId());
        ragIndexingService.indexRecipe(outOfBudgetApartment.getId());
        ragIndexingService.indexGuide(guide.getId());

        MvcResult res = mockMvc.perform(post("/api/search/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RagSearchRequest(query, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.relaxed").value(false))
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andExpect(jsonPath("$.warnings").isArray())
                .andExpect(jsonPath("$.budgetBreakdown").isArray())
                .andExpect(jsonPath("$.citations").isArray())
                .andExpect(jsonPath("$.checklistDraft").isArray())
                .andReturn();

        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("budgetBreakdown").size()).isGreaterThan(0);
        assertThat(body.get("checklistDraft").size()).isGreaterThan(0);

        List<Long> citedRecipeIds = new java.util.ArrayList<>();
        boolean hasGuideCitation = false;
        for (JsonNode c : body.get("citations")) {
            if ("RECIPE".equals(c.get("sourceType").asText())) {
                citedRecipeIds.add(c.get("sourceId").asLong());
            } else if ("GUIDE".equals(c.get("sourceType").asText())) {
                hasGuideCitation = true;
            }
        }
        assertThat(citedRecipeIds).contains(matchingRecipe.getId());
        assertThat(citedRecipeIds).doesNotContain(outOfBudgetApartment.getId());
        assertThat(hasGuideCitation).isTrue();
    }

    @Test
    void searchRelaxesFiltersWhenStrictFilterMatchesZeroRecipes() throws Exception {
        // Strict filter from query: ONE_ROOM, maxPyeong=6, maxBudget=500000, isNoDrill=true
        // Seed only a 12-pyeong, 1,200,000 KRW recipe so strict filter matches 0 recipes
        RecipePost broaderRecipe = createRecipe(
                "12평 원룸 따뜻한 우드 인테리어",
                "예산 120만원으로 따뜻하게 꾸민 넓은 원룸 사례입니다.",
                HousingType.ONE_ROOM, 12, ContractType.JEONSE, true, 1_200_000, StyleTag.WOOD
        );
        ragIndexingService.indexRecipe(broaderRecipe.getId());

        mockMvc.perform(post("/api/search/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RagSearchRequest("전세 6평 원룸 무타공 50만원 따뜻하게", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.relaxed").value(true))
                .andExpect(jsonPath("$.warnings[0]").value(org.hamcrest.Matchers.containsString("완화")))
                .andExpect(jsonPath("$.citations[0].sourceId").value(broaderRecipe.getId()));
    }

    @Test
    void searchUsesRedisCacheAndEnforcesDailyRateLimit429() throws Exception {
        RecipePost recipe = createRecipe(
                "6평 원룸 캐시 테스트",
                "따뜻한 무타공 원룸 인테리어 사례입니다.",
                HousingType.ONE_ROOM, 6, ContractType.JEONSE, true, 400_000, StyleTag.WOOD
        );
        ragIndexingService.indexRecipe(recipe.getId());

        RagSearchRequest req = new RagSearchRequest("전세 6평 원룸 무타공 50만원 따뜻하게", null);

        // 1st call -> cache miss, invokes LLM once, stores in Redis cache
        mockMvc.perform(post("/api/search/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(false));

        Set<String> cacheKeys = stringRedisTemplate.keys("rag:cache:*");
        assertThat(cacheKeys).isNotNull().hasSize(1);

        // 2nd call -> cache hit, LLM must NOT be invoked a second time
        mockMvc.perform(post("/api/search/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(false));

        verify(ragLlmClient, times(1)).generate(any(), any(), any());

        // Exhaust daily rate limit (default 20) and verify 429 G002 on 21st call
        String rlKey = "rag:rl:u:" + user.getId() + ":" + LocalDate.now();
        stringRedisTemplate.opsForValue().set(rlKey, "20");

        mockMvc.perform(post("/api/search/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("G002"));
    }

    @Test
    void circuitBreakerFallbackReturns200WithKeywordCitationsAndDoesNotCache() throws Exception {
        RecipePost recipe = createRecipe(
                "따뜻한 북향 원룸 조명 레시피",
                "무타공 전구색 스탠드 조명으로 따뜻하게 꾸민 원룸입니다.",
                HousingType.ONE_ROOM, 6, ContractType.JEONSE, true, 450_000, StyleTag.WOOD
        );
        ragIndexingService.indexRecipe(recipe.getId());

        doThrow(new RuntimeException("Simulated LLM timeout"))
                .when(ragLlmClient).generate(any(), any(), any());

        mockMvc.perform(post("/api/search/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RagSearchRequest("스탠드 조명", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.citations[0].sourceId").value(recipe.getId()))
                .andExpect(jsonPath("$.citations[0].title").value("따뜻한 북향 원룸 조명 레시피"));

        // Fallback response must NOT be cached in Redis
        Set<String> cacheKeys = stringRedisTemplate.keys("rag:cache:*");
        assertThat(cacheKeys == null || cacheKeys.isEmpty()).isTrue();
    }

    @Test
    void adminReindexEndpointRequiresAdminRole() throws Exception {
        createGuide("어드민 재색인 테스트 가이드", GuideCategory.STYLE, "MINIMAL", "미니멀 스타일 가이드 본문");

        mockMvc.perform(post("/api/admin/rag/reindex")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/rag/reindex")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexedCount").value(1));
    }

    @Test
    void checklistCrudProgressPercentAndOwnershipEnforcement() throws Exception {
        // 0-item checklist -> progressPercent = 0 (no division by zero)
        ChecklistCreateRequest emptyReq = new ChecklistCreateRequest(
                "빈 체크리스트", "테스트", 300_000, "ONE_ROOM", true, List.of()
        );
        MvcResult emptyRes = mockMvc.perform(post("/api/checklists")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.completedCount").value(0))
                .andExpect(jsonPath("$.progressPercent").value(0))
                .andReturn();
        long emptyId = objectMapper.readTree(emptyRes.getResponse().getContentAsString()).get("id").asLong();

        // 1-Click save with 2 items (out of stepOrder to verify stepOrder ASC sorting)
        ChecklistCreateRequest createReq = new ChecklistCreateRequest(
                "6평 원룸 무타공 플랜",
                "전세 6평 원룸 무타공 50만원 따뜻하게",
                500_000,
                "ONE_ROOM",
                true,
                List.of(
                        new ChecklistItemCreateRequest(2, "패브릭", "암막 커튼 설치", 120_000, null),
                        new ChecklistItemCreateRequest(1, "조명", "3000K 스탠드 조명 구매", 80_000, "전구색 확인")
                )
        );

        MvcResult createRes = mockMvc.perform(post("/api/checklists")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.completedCount").value(0))
                .andExpect(jsonPath("$.progressPercent").value(0))
                .andReturn();

        JsonNode createdBody = objectMapper.readTree(createRes.getResponse().getContentAsString());
        long checklistId = createdBody.get("id").asLong();

        // Verify GET detail orders items by stepOrder ASC
        MvcResult detailRes = mockMvc.perform(get("/api/checklists/{id}", checklistId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].stepOrder").value(1))
                .andExpect(jsonPath("$.items[1].stepOrder").value(2))
                .andReturn();
        long firstItemId = objectMapper.readTree(detailRes.getResponse().getContentAsString())
                .get("items").get(0).get("id").asLong();

        // Check 1 of 2 items -> progressPercent = 50%
        mockMvc.perform(patch("/api/checklists/{id}/items/{itemId}", checklistId, firstItemId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChecklistItemUpdateRequest(true, "배송 완료", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.progressPercent").value(50))
                .andExpect(jsonPath("$.items[0].isChecked").value(true))
                .andExpect(jsonPath("$.items[0].memo").value("배송 완료"));

        // Verify GET list shows both checklists with accurate progressPercent
        mockMvc.perform(get("/api/checklists")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Add 3rd item -> totalCount = 3, progressPercent = 33%
        MvcResult addedRes = mockMvc.perform(post("/api/checklists/{id}/items", checklistId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChecklistItemCreateRequest(3, "수납", "무타공 선반 추가", 50_000, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.progressPercent").value(33))
                .andReturn();
        long addedItemId = objectMapper.readTree(addedRes.getResponse().getContentAsString())
                .get("items").get(2).get("id").asLong();

        // Delete 3rd item -> totalCount = 2, progressPercent = 50%
        mockMvc.perform(delete("/api/checklists/{id}/items/{itemId}", checklistId, addedItemId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.progressPercent").value(50));

        // Other user attempts -> 403 Forbidden
        mockMvc.perform(get("/api/checklists/{id}", checklistId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/checklists/{id}/items/{itemId}", checklistId, firstItemId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChecklistItemUpdateRequest(false, "hack", null, null))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/checklists/{id}", checklistId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        // Missing checklistId / mismatched or missing itemId -> 404 Not Found
        mockMvc.perform(get("/api/checklists/{id}", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/checklists/{id}/items/{itemId}", emptyId, firstItemId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChecklistItemUpdateRequest(true, null, null, null))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/checklists/{id}/items/{itemId}", checklistId, 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isNotFound());

        // Owner deletes checklist -> 204 No Content, subsequent GET -> 404
        mockMvc.perform(delete("/api/checklists/{id}", checklistId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/checklists/{id}", checklistId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }
}

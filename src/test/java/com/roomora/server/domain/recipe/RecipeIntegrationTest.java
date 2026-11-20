package com.roomora.server.domain.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomora.server.domain.auth.dto.AuthDtos.*;
import com.roomora.server.domain.recipe.dto.RecipeDtos.*;
import com.roomora.server.domain.recipe.event.RecipeIndexEvent;
import com.roomora.server.global.storage.UploadController.PresignedUrlRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class RecipeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecipePostRepository recipePostRepository;

    @Autowired
    private com.roomora.server.domain.user.UserRepository userRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() throws Exception {
        recipePostRepository.deleteAll();
        userRepository.deleteAll();
        ownerToken = signupAndLogin("owner@test.com", "레시피작성자");
        otherToken = signupAndLogin("other@test.com", "다른유저");
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

    @Test
    void presignedUrlGeneratesUploadUrlAndRejectsInvalidMimeType() throws Exception {
        mockMvc.perform(post("/api/uploads/presigned-url")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PresignedUrlRequest("room.jpg", "image/jpeg", "RECIPE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
                .andExpect(jsonPath("$.fileUrl").isNotEmpty());

        mockMvc.perform(post("/api/uploads/presigned-url")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PresignedUrlRequest("virus.exe", "application/octet-stream", "RECIPE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recipeCrudValidationForbiddenAndAsyncIndexEvent() throws Exception {
        // Invalid budget -> 400
        RecipeCreateRequest invalidReq = new RecipeCreateRequest(
                "잘못된 글", "내용", HousingType.ONE_ROOM, (short) 6, ContractType.JEONSE,
                true, -1000, StyleTag.WOOD, null, List.of(), List.of()
        );
        mockMvc.perform(post("/api/recipes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());

        // Valid create -> 201 + RecipeIndexEvent(CREATED)
        RecipeCreateRequest createReq = new RecipeCreateRequest(
                "전세 6평 무타공 원룸", "따뜻한 3000K 조명 활용", HousingType.ONE_ROOM, (short) 6,
                ContractType.JEONSE, true, 450000, StyleTag.WOOD,
                "https://cdn.roomora.local/thumb.jpg",
                List.of("https://cdn.roomora.local/1.jpg", "https://cdn.roomora.local/2.jpg"),
                List.of(new RecipeItemRequest("단스탠드", "조명", 45000, "오늘의집"))
        );
        MvcResult created = mockMvc.perform(post("/api/recipes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.items[0].itemName").value("단스탠드"))
                .andReturn();

        Long postId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        assertThat(applicationEvents.stream(RecipeIndexEvent.class)
                .anyMatch(e -> e.recipeId().equals(postId) && e.action() == RecipeIndexEvent.Action.CREATED)).isTrue();

        // Other user cannot update or delete -> 403
        RecipeUpdateRequest updateReq = new RecipeUpdateRequest(
                "수정", "수정", HousingType.ONE_ROOM, (short) 6,
                ContractType.JEONSE, true, 400000, StyleTag.MODERN, null, List.of(), List.of()
        );
        mockMvc.perform(patch("/api/recipes/" + postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/recipes/" + postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        // Owner delete -> 204 + Soft Delete
        mockMvc.perform(delete("/api/recipes/" + postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/recipes/" + postId))
                .andExpect(status().isNotFound());
    }

    @Test
    void compositeFilterAndKeysetPagingAndLikeScrapViewDeduplication() throws Exception {
        Long p1 = createSampleRecipe("원룸 30만", HousingType.ONE_ROOM, true, 300000, StyleTag.WOOD);
        Long p2 = createSampleRecipe("원룸 45만", HousingType.ONE_ROOM, true, 450000, StyleTag.WOOD);
        Long p3 = createSampleRecipe("원룸 50만", HousingType.ONE_ROOM, true, 500000, StyleTag.MODERN);
        createSampleRecipe("아파트 200만", HousingType.APARTMENT, false, 2000000, StyleTag.MODERN);

        // Page 1 (size=2, housingType=ONE_ROOM, isNoDrill=true, maxBudget=500000) -> p3, p2
        MvcResult page1 = mockMvc.perform(get("/api/recipes")
                        .param("housingType", "ONE_ROOM")
                        .param("isNoDrill", "true")
                        .param("maxBudget", "500000")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(p3))
                .andExpect(jsonPath("$.items[1].id").value(p2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn();

        long nextCursor = objectMapper.readTree(page1.getResponse().getContentAsString()).get("nextCursor").asLong();

        // Page 2 with lastId -> p1, hasNext=false
        mockMvc.perform(get("/api/recipes")
                        .param("housingType", "ONE_ROOM")
                        .param("isNoDrill", "true")
                        .param("maxBudget", "500000")
                        .param("lastId", String.valueOf(nextCursor))
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(p1))
                .andExpect(jsonPath("$.hasNext").value(false));

        // Redis view deduplication: 2 calls by same user -> viewCount == 1
        mockMvc.perform(get("/api/recipes/" + p1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(1));

        mockMvc.perform(get("/api/recipes/" + p1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(1));

        // Like & Scrap toggle
        mockMvc.perform(post("/api/recipes/" + p1 + "/like")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(post("/api/recipes/" + p1 + "/like")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.count").value(0));

        mockMvc.perform(post("/api/recipes/" + p1 + "/scrap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void twoDepthCommentEnforcementAndOwnershipCheck() throws Exception {
        Long postId = createSampleRecipe("댓글 테스트 방", HousingType.ONE_ROOM, true, 300000, StyleTag.MINIMAL);

        // Parent comment
        MvcResult parentRes = mockMvc.perform(post("/api/recipes/" + postId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentCreateRequest(null, "첫 댓글"))))
                .andExpect(status().isCreated())
                .andReturn();
        Long parentId = objectMapper.readTree(parentRes.getResponse().getContentAsString()).get("id").asLong();

        // Reply (2-depth) -> OK
        MvcResult replyRes = mockMvc.perform(post("/api/recipes/" + postId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentCreateRequest(parentId, "대댓글"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(parentId))
                .andReturn();
        Long replyId = objectMapper.readTree(replyRes.getResponse().getContentAsString()).get("id").asLong();

        // 3rd-depth reply -> 400 INVALID_COMMENT_DEPTH
        mockMvc.perform(post("/api/recipes/" + postId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentCreateRequest(replyId, "3단 불가"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("R003"));

        // Owner cannot delete other user's reply -> 403
        mockMvc.perform(delete("/api/recipes/" + postId + "/comments/" + replyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    private Long createSampleRecipe(String title, HousingType housingType, boolean noDrill, int budget, StyleTag style) throws Exception {
        RecipeCreateRequest req = new RecipeCreateRequest(
                title, "내용", housingType, (short) 7, ContractType.JEONSE,
                noDrill, budget, style, null, List.of(), List.of()
        );
        MvcResult res = mockMvc.perform(post("/api/recipes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        return json.get("id").asLong();
    }
}

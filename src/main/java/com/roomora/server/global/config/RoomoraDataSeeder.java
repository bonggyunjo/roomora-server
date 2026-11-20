package com.roomora.server.global.config;

import com.roomora.server.domain.contest.*;
import com.roomora.server.domain.guide.GuideCategory;
import com.roomora.server.domain.guide.InteriorGuide;
import com.roomora.server.domain.guide.InteriorGuideRepository;
import com.roomora.server.domain.rag.RagIndexingService;
import com.roomora.server.domain.recipe.*;
import com.roomora.server.domain.user.AuthProvider;
import com.roomora.server.domain.user.User;
import com.roomora.server.domain.user.UserRepository;
import com.roomora.server.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class RoomoraDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final InteriorGuideRepository guideRepository;
    private final RecipePostRepository recipePostRepository;
    private final ContestRoundRepository contestRoundRepository;
    private final ContestPostRepository contestPostRepository;
    private final ContestRankingRedisRepository rankingRedisRepository;
    private final RagIndexingService ragIndexingService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (recipePostRepository.count() > 0 || guideRepository.count() > 0) {
            return;
        }
        log.info("Seeding initial Roomora guides, recipes, and active contest...");

        User curator = userRepository.findByEmailAndProviderAndDeletedAtIsNull("curator@roomora.local", AuthProvider.LOCAL)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("curator@roomora.local")
                        .password(passwordEncoder.encode("password123!"))
                        .nickname("루모라큐레이터")
                        .provider(AuthProvider.LOCAL)
                        .role(UserRole.ADMIN)
                        .build()));

        seedGuides();
        seedRecipes(curator);
        seedActiveContest(curator);

        int indexed = ragIndexingService.reindexAllGuidesAndRecipes();
        log.info("Roomora initial seed completed. Indexed {} documents into pgvector.", indexed);
    }

    private void seedGuides() {
        List<InteriorGuide> guides = List.of(
                InteriorGuide.builder()
                        .title("전세·월세 무타공 시공 필수 가이드")
                        .category(GuideCategory.NO_DRILL)
                        .styleTag("NATURAL")
                        .colorPalette("#F5EFE6,#D8C3A5,#8E8D8A")
                        .content("## 무타공 기본 원칙\n벽지 손상 없이 공간을 꾸미려면 압축봉 커튼, 레일형 스탠드 조명, 실리콘 논슬립 선반을 우선 활용하세요.\n## 주의사항\n석고보드 벽면에 꼭꼬핀을 사용할 때는 하중 2kg 이하 소품만 걸어야 도배 찢김을 방지할 수 있습니다.")
                        .build(),
                InteriorGuide.builder()
                        .title("북향 원룸을 따뜻하게 바꾸는 3000K 조명 전략")
                        .category(GuideCategory.LIGHTING)
                        .styleTag("WOOD")
                        .colorPalette("#FFE4B5,#C69C6D,#3E2723")
                        .content("## 색온도 선택\n북향 방은 자연광이 차갑기 때문에 6500K 주광색 형광등을 끄고 3000K 전구색 단스탠드와 장스탠드를 2~3곳 분산 배치하면 아늑함이 살아납니다.")
                        .build(),
                InteriorGuide.builder()
                        .title("6평 이하 원룸 동선·수납 최적화")
                        .category(GuideCategory.STORAGE)
                        .styleTag("MINIMAL")
                        .colorPalette("#FFFFFF,#E0E0E0,#424242")
                        .content("## 수납 원칙\n높이가 높은 책장 대신 허리 높이 이하의 모듈 수납장과 침대 하부 수납 박스를 쓰면 시야가 트여 방이 1.5배 넓어 보입니다.")
                        .build(),
                InteriorGuide.builder()
                        .title("우드 & 패브릭 내추럴 스타일 가이드")
                        .category(GuideCategory.STYLE)
                        .styleTag("WOOD")
                        .colorPalette("#EADBC8,#DAC0A3,#102C57")
                        .content("## 핵심 조합\n원목 사이드 테이블에 아이보리 린넨 커튼과 단모 러그를 매치하면 적은 예산으로도 완성도 높은 내추럴 무드를 낼 수 있습니다.")
                        .build()
        );
        guideRepository.saveAll(guides);
    }

    private void seedRecipes(User curator) {
        saveRecipe(curator, "전세 6평 북향 원룸, 45만 원 무타공 따뜻한 무드",
                "## 포인트\n못질이 안 되는 전세 원룸이라 압축봉 쉬폰 커튼과 3000K 단스탠드 2개로 분위기를 바꿨습니다.\n## 배치 팁\n침대 헤드 옆에 우드 협탁과 무드등을 두니 밤에 훨씬 포근합니다.",
                HousingType.ONE_ROOM, (short) 6, ContractType.JEONSE, true, 450000, StyleTag.WOOD,
                "https://images.unsplash.com/photo-1586023492125-27b2c045efd7?w=900&auto=format&fit=crop",
                List.of(
                        new ItemSeed("이케아 람판 단스탠드", "조명", 39000),
                        new ItemSeed("무타공 압축봉 쉬폰 커튼", "패브릭", 65000),
                        new ItemSeed("워셔블 단모 베이지 러그", "패브릭", 89000),
                        new ItemSeed("원목 3단 오픈 선반", "수납", 145000),
                        new ItemSeed("패브릭 포스터 & 트레이", "소품", 112000)
                ));

        saveRecipe(curator, "월세 7평 오피스텔 30만 원 미니멀 데스크테리어",
                "## 포인트\n화이트 데스크와 모니터 암(클램프형 무타공)으로 책상 위 공간을 넓혔습니다.",
                HousingType.OFFICETEL, (short) 7, ContractType.MONTHLY, true, 300000, StyleTag.MINIMAL,
                "https://images.unsplash.com/photo-1593062096033-9a26b09da705?w=900&auto=format&fit=crop",
                List.of(
                        new ItemSeed("클램프 무타공 모니터암", "수납", 49000),
                        new ItemSeed("모니터 라이트바 3000K-5000K", "조명", 45000),
                        new ItemSeed("화이트 1200 데스크", "가구", 130000),
                        new ItemSeed("펠트 데스크 매트 & 정리함", "소품", 76000)
                ));

        saveRecipe(curator, "신혼 18평 투룸 120만 원 모던 우드 거실",
                "## 포인트\n낮은 패브릭 소파와 월넛 거실장, 플로어 램프로 호텔 라운지 느낌을 냈습니다.",
                HousingType.TWO_ROOM, (short) 18, ContractType.JEONSE, true, 1200000, StyleTag.MODERN,
                "https://images.unsplash.com/photo-1616486338812-3dadae4b4ace?w=900&auto=format&fit=crop",
                List.of(
                        new ItemSeed("2인 패브릭 모듈 소파", "가구", 580000),
                        new ItemSeed("월넛 1600 거실장", "가구", 290000),
                        new ItemSeed("아치형 장스탠드 조명", "조명", 120000),
                        new ItemSeed("대형 거실 러그 & 쿠션", "패브릭", 210000)
                ));

        saveRecipe(curator, "5평 자취방 25만 원 가성비 코지 침실",
                "## 포인트\n저상형 매트리스 커버 교체와 스트링 라이트만으로 비용을 아꼈습니다.",
                HousingType.ONE_ROOM, (short) 5, ContractType.MONTHLY, true, 250000, StyleTag.NATURAL,
                "https://images.unsplash.com/photo-1617325247661-675ab4b64ae2?w=900&auto=format&fit=crop",
                List.of(
                        new ItemSeed("피치스킨 베딩 세트", "패브릭", 79000),
                        new ItemSeed("무선 충전 우드 무드등", "조명", 35000),
                        new ItemSeed("침대 밑 리빙박스 4종", "수납", 68000),
                        new ItemSeed("패브릭 가림막 커튼", "소품", 68000)
                ));
    }

    private void saveRecipe(User author, String title, String content, HousingType housingType,
                            short areaPyeong, ContractType contractType, boolean noDrill,
                            int budget, StyleTag styleTag, String imageUrl, List<ItemSeed> items) {
        RecipePost post = RecipePost.builder()
                .user(author)
                .title(title)
                .content(content)
                .housingType(housingType)
                .areaPyeong(areaPyeong)
                .contractType(contractType)
                .noDrill(noDrill)
                .budget(budget)
                .styleTag(styleTag)
                .thumbnailUrl(imageUrl)
                .build();
        post.replaceImages(List.of(imageUrl));
        post.replaceItems(items.stream()
                .map(i -> new RecipeItem(post, i.name(), i.category(), i.price(), null))
                .toList());
        recipePostRepository.save(post);
    }

    private void seedActiveContest(User curator) {
        if (contestRoundRepository.findFirstByStatusOrderByRoundNoDesc(ContestRoundStatus.ACTIVE).isPresent()) {
            return;
        }
        ContestRound round = contestRoundRepository.save(ContestRound.builder()
                .roundNo(1)
                .title("제1회 자취방 무타공 데스크테리어 콘테스트")
                .themeDescription("전세·월세 상관없이 못질 없이 완성한 나만의 책상·방 공간을 공유해 주세요!")
                .status(ContestRoundStatus.ACTIVE)
                .startAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .endAt(Instant.now().plus(6, ChronoUnit.DAYS))
                .build());

        ContestPost post = ContestPost.builder()
                .round(round)
                .user(curator)
                .title("3000K 간접조명으로 완성한 북향 원룸 책상")
                .content("모니터 라이트바와 우드 선반만으로 아늑한 작업 공간을 만들었습니다.")
                .thumbnailUrl("https://images.unsplash.com/photo-1593062096033-9a26b09da705?w=800")
                .build();
        post.replaceImages(List.of("https://images.unsplash.com/photo-1593062096033-9a26b09da705?w=1200"));
        ContestPost saved = contestPostRepository.save(post);
        rankingRedisRepository.initializeEntry(round.getId(), saved.getId());
    }

    private record ItemSeed(String name, String category, int price) {
    }
}

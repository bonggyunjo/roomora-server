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

    private static final List<String> INTERIOR_PHOTOS = List.of(
            "https://images.unsplash.com/photo-1586023492125-27b2c045efd7?w=900&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1593062096033-9a26b09da705?w=900&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1616486338812-3dadae4b4ace?w=900&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1617325247661-675ab4b64ae2?w=900&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1554995207-c18c203602cb?w=900&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1540518614846-7eded433c457?w=900&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1524758631624-e2822e304c36?w=900&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?w=900&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1513694203232-719a280e022f?w=900&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?w=900&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1567016432779-094069958ea5?w=900&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1493663284031-b7e3aefcae8e?w=900&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?w=900&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1484154218962-a197022b5858?w=900&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1538688525198-9b88f6f53126?w=900&auto=format&fit=crop"
    );

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

        User curator = ensureUser("curator@roomora.local", "루모라큐레이터", UserRole.ADMIN);
        User maker = ensureUser("maker@roomora.local", "북향자취러", UserRole.USER);
        User nordic = ensureUser("nordic@roomora.local", "노르딕홈", UserRole.USER);
        User cozy = ensureUser("cozy@roomora.local", "따뜻한방", UserRole.USER);
        User minimal = ensureUser("minimal@roomora.local", "미니멀라이프", UserRole.USER);
        List<User> authors = List.of(curator, maker, nordic, cozy, minimal);

        seedGuides();
        seedRecipes(authors);
        seedActiveContest(authors);

        int indexed = ragIndexingService.reindexAllGuidesAndRecipes();
        log.info("Roomora initial seed completed. Indexed {} documents into pgvector.", indexed);
    }

    private User ensureUser(String email, String nickname, UserRole role) {
        return userRepository.findByEmailAndProviderAndDeletedAtIsNull(email, AuthProvider.LOCAL)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .password(passwordEncoder.encode("password123!"))
                        .nickname(nickname)
                        .provider(AuthProvider.LOCAL)
                        .role(role)
                        .build()));
    }

    private void seedGuides() {
        List<InteriorGuide> guides = List.of(
                guide("전세·월세 무타공 시공 필수 가이드", GuideCategory.NO_DRILL, "NATURAL", "#F5EFE6,#D8C3A5,#8E8D8A",
                        "## 무타공 기본 원칙\n벽지 손상 없이 공간을 꾸미려면 압축봉 커튼, 레일형 스탠드 조명, 실리콘 논슬립 선반을 우선 활용하세요.\n## 주의사항\n석고보드 벽면에 꼭꼬핀을 사용할 때는 하중 2kg 이하 소품만 걸어야 도배 찢김을 방지할 수 있습니다."),
                guide("북향 원룸을 따뜻하게 바꾸는 3000K 조명 전략", GuideCategory.LIGHTING, "WOOD", "#FFE4B5,#C69C6D,#3E2723",
                        "## 색온도 선택\n북향 방은 자연광이 차갑기 때문에 6500K 주광색 형광등을 끄고 3000K 전구색 단스탠드와 장스탠드를 2~3곳 분산 배치하면 아늑함이 살아납니다."),
                guide("6평 이하 원룸 동선·수납 최적화", GuideCategory.STORAGE, "MINIMAL", "#FFFFFF,#E0E0E0,#424242",
                        "## 수납 원칙\n높이가 높은 책장 대신 허리 높이 이하의 모듈 수납장과 침대 하부 수납 박스를 쓰면 시야가 트여 방이 1.5배 넓어 보입니다."),
                guide("우드 & 패브릭 내추럴 스타일 가이드", GuideCategory.STYLE, "WOOD", "#EADBC8,#DAC0A3,#102C57",
                        "## 핵심 조합\n원목 사이드 테이블에 아이보리 린넨 커튼과 단모 러그를 매치하면 적은 예산으로도 완성도 높은 내추럴 무드를 낼 수 있습니다."),
                guide("압축봉·클램프 활용 무타공 데스크테리어", GuideCategory.NO_DRILL, "MODERN", "#27272A,#F4F4F5,#A16207",
                        "## 책상 공간 확보\n클램프식 모니터암과 책상 하부 무타공 멀티탭 트레이를 쓰면 전선 노출 없이 깔끔한 작업 환경을 만들 수 있습니다."),
                guide("오피스텔 복층·원룸 층고 살리는 수직 수납", GuideCategory.STORAGE, "NORDIC", "#F8FAFC,#CBD5E1,#475569",
                        "## 수직 활용\n행거 상단 선반과 도어 후크, 틈새 슬림 트롤리를 조합하면 바닥 면적을 차지하지 않고 수납량을 40% 늘릴 수 있습니다."),
                guide("간접조명·스트립 LED 무타공 설치 팁", GuideCategory.LIGHTING, "MODERN", "#FEF3C7,#F59E0B,#18181B",
                        "## 눈부심 방지\n침대 헤드 뒤나 책상 뒤쪽에 2700K~3000K USB LED 바를 마스킹 테이프 위에 부착하면 벽지 손상 없이 호텔식 간접등을 구현합니다."),
                guide("빈티지 소품과 러그로 만드는 아늑한 코너", GuideCategory.STYLE, "VINTAGE", "#78350F,#D97706,#FEF3C7",
                        "## 포인트 스타일링\n월넛 톤 사이드 테이블 위에 빈티지 머쉬룸 램프와 패턴 러그 하나만 두어도 공간 전체의 온도가 달라집니다."),
                guide("현관·주방 분리 가벽 파티션 활용법", GuideCategory.NO_DRILL, "MINIMAL", "#FAF9F6,#D4D4D8,#3F3F46",
                        "## 공간 분리\n원룸에서 현관과 침실이 바로 보일 때는 천장 지지대형 파티션이나 낮은 오픈 선반으로 시선을 가리면 수면 만족도가 높아집니다."),
                guide("인더스트리얼 & 블랙 스틸 홈카페 구성", GuideCategory.STYLE, "INDUSTRIAL", "#18181B,#52525B,#B45309",
                        "## 홈카페 무드\n무광 블랙 스틸 선반과 우드 상판, 에디슨 전구 펜던트 스탠드를 매치하면 작은 오피스텔도 감각적인 홈카페로 변신합니다.")
        );
        guideRepository.saveAll(guides);
    }

    private InteriorGuide guide(String title, GuideCategory category, String styleTag, String palette, String content) {
        return InteriorGuide.builder()
                .title(title)
                .category(category)
                .styleTag(styleTag)
                .colorPalette(palette)
                .content(content)
                .build();
    }

    private void seedRecipes(List<User> authors) {
        List<RecipeSeed> seeds = List.of(
                new RecipeSeed("전세 6평 북향 원룸, 45만 원 무타공 따뜻한 무드", HousingType.ONE_ROOM, 6, ContractType.JEONSE, true, 450000, StyleTag.WOOD,
                        "못질이 안 되는 전세 원룸이라 압축봉 쉬폰 커튼과 3000K 단스탠드 2개로 분위기를 바꿨습니다.",
                        List.of(item("이케아 람판 단스탠드", "조명", 39000), item("무타공 압축봉 쉬폰 커튼", "패브릭", 65000), item("워셔블 베이지 러그", "패브릭", 89000), item("원목 3단 오픈 선반", "수납", 145000), item("패브릭 포스터 & 트레이", "소품", 112000))),
                new RecipeSeed("월세 7평 오피스텔 30만 원 미니멀 데스크테리어", HousingType.OFFICETEL, 7, ContractType.MONTHLY, true, 300000, StyleTag.MINIMAL,
                        "화이트 데스크와 클램프형 무타공 모니터암으로 책상 위 공간을 넓혔습니다.",
                        List.of(item("클램프 무타공 모니터암", "수납", 49000), item("모니터 라이트바", "조명", 45000), item("화이트 1200 데스크", "가구", 130000), item("펠트 데스크 매트", "소품", 76000))),
                new RecipeSeed("신혼 18평 투룸 120만 원 모던 우드 거실", HousingType.TWO_ROOM, 18, ContractType.JEONSE, true, 1200000, StyleTag.MODERN,
                        "낮은 패브릭 소파와 월넛 거실장, 플로어 램프로 호텔 라운지 느낌을 냈습니다.",
                        List.of(item("2인 패브릭 모듈 소파", "가구", 580000), item("월넛 1600 거실장", "가구", 290000), item("아치형 장스탠드 조명", "조명", 120000), item("대형 거실 러그", "패브릭", 210000))),
                new RecipeSeed("5평 자취방 25만 원 가성비 코지 침실", HousingType.ONE_ROOM, 5, ContractType.MONTHLY, true, 250000, StyleTag.NATURAL,
                        "저상형 매트리스 커버 교체와 스트링 라이트만으로 비용을 아꼈습니다.",
                        List.of(item("피치스킨 베딩 세트", "패브릭", 79000), item("무선 우드 무드등", "조명", 35000), item("침대 밑 리빙박스 4종", "수납", 68000), item("패브릭 가림막 커튼", "소품", 68000))),
                new RecipeSeed("전세 8평 복층 오피스텔 65만 원 노르딕 홈카페", HousingType.OFFICETEL, 8, ContractType.JEONSE, true, 650000, StyleTag.NORDIC,
                        "계단 아래 자투리 공간에 원형 테이블과 라탄 체어를 두어 홈카페를 만들었습니다.",
                        List.of(item("800 원형 화이트 테이블", "가구", 180000), item("라탄 카페 체어 2개", "가구", 190000), item("무타공 팬던트 스탠드", "조명", 95000), item("린넨 테이블보 & 식기", "소품", 185000))),
                new RecipeSeed("4.5평 고시원 탈출 원룸 19만 원 초저예산 정리", HousingType.ONE_ROOM, 5, ContractType.MONTHLY, true, 190000, StyleTag.MINIMAL,
                        "최소 가구 배치와 3단 철제 트롤리 하나로 동선을 깔끔하게 정리했습니다.",
                        List.of(item("3단 이동식 트롤리", "수납", 38000), item("접이식 좌식 테이블", "가구", 42000), item("3000K 클립 스탠드", "조명", 28000), item("무타공 창문 단열 커튼", "패브릭", 82000))),
                new RecipeSeed("전세 12평 빌라 투룸 80만 원 빈티지 서재 겸 침실", HousingType.TWO_ROOM, 12, ContractType.JEONSE, true, 800000, StyleTag.VINTAGE,
                        "체리몰딩을 가리지 않고 오히려 월넛 가구와 매치해 따뜻한 빈티지 무드로 살렸습니다.",
                        List.of(item("월넛 1400 원목 책상", "가구", 310000), item("빈티지 머쉬룸 램프", "조명", 85000), item("페르시안 패턴 러그", "패브릭", 165000), item("모듈LP 수납장", "수납", 240000))),
                new RecipeSeed("자가 24평 아파트 250만 원 내추럴 우드 거실 리모델링", HousingType.APARTMENT, 24, ContractType.OWNED, false, 2500000, StyleTag.NATURAL,
                        "벽면 실링팬과 오크 원목 다이닝 테이블을 중심으로 가족 공용 공간을 꾸몄습니다.",
                        List.of(item("오크 4인 식탁 세트", "가구", 1150000), item("린넨 겉커튼 맞춤 시공", "패브릭", 450000), item("매입형 다운라이트 3000K", "조명", 320000), item("모듈형 월 시스템 선반", "수납", 580000))),
                new RecipeSeed("월세 6평 남향 원룸 38만 원 햇살 맛집 플랜테리어", HousingType.ONE_ROOM, 6, ContractType.MONTHLY, true, 380000, StyleTag.NATURAL,
                        "창가 햇살을 살려 반투명 리넨 커튼과 화분 스탠드를 배치했습니다.",
                        List.of(item("원목 2단 플랜트 스탠드", "가구", 68000), item("반투명 리넨 커튼", "패브릭", 72000), item("관엽식물 & 토분 세트", "소품", 120000), item("라탄 장스탠드", "조명", 120000))),
                new RecipeSeed("전세 9평 신축 오피스텔 55만 원 인더스트리얼 작업실", HousingType.OFFICETEL, 9, ContractType.JEONSE, true, 550000, StyleTag.INDUSTRIAL,
                        "블랙 스틸 프레임 책상과 에디슨 전구 조명으로 집중되는 1인 작업실을 만들었습니다.",
                        List.of(item("스틸 프레임 1400 책상", "가구", 210000), item("메탈 장스탠드", "조명", 89000), item("타공판 무타공 거치대", "수납", 65000), item("다크 그레이 단모 러그", "패브릭", 186000))),
                new RecipeSeed("6평 북서향 반지하 42만 원 습기·채광 보완 스타일링", HousingType.ONE_ROOM, 6, ContractType.MONTHLY, true, 420000, StyleTag.WOOD,
                        "화이트 시트지와 간접 LED 바, 제습 가전 수납으로 밝고 뽀송한 방을 만들었습니다.",
                        List.of(item("전구색 LED 간접바 2세트", "조명", 48000), item("통풍형 원목 깔판", "가구", 135000), item("방수 패브릭 러그", "패브릭", 95000), item("슬림 수납 트롤리", "수납", 142000))),
                new RecipeSeed("전세 15평 투룸 95만 원 미니멀 화이트 드레스룸", HousingType.TWO_ROOM, 15, ContractType.JEONSE, true, 950000, StyleTag.MINIMAL,
                        "작은방 하나를 왕자행거 무타공 시스템과 서랍장으로 드레스룸화했습니다.",
                        List.of(item("무타공 기둥형 시스템 행거", "수납", 280000), item("화이트 5단 서랍장 2개", "수납", 380000), item("전신 아치형 거울", "소품", 140000), item("천장 레일 스포트 조명", "조명", 150000))),
                new RecipeSeed("월세 7평 원룸 32만 원 침대·책상 존 분리 레이아웃", HousingType.ONE_ROOM, 7, ContractType.MONTHLY, true, 320000, StyleTag.MODERN,
                        "오픈 2x3 큐브 선반을 가벽처럼 세워 수면 공간과 업무 공간을 나눴습니다.",
                        List.of(item("2x3 오픈 파티션 선반", "수납", 119000), item("1인용 1000 책상", "가구", 95000), item("단스탠드 무드등", "조명", 39000), item("체크 패턴 이불 커버", "패브릭", 67000))),
                new RecipeSeed("자가 32평 아파트 280만 원 모던 호텔식 안방", HousingType.APARTMENT, 32, ContractType.OWNED, false, 2800000, StyleTag.MODERN,
                        "패브릭 헤드보드와 양옆 펜던트 조명으로 호텔 스위트룸 같은 침실을 완성했습니다.",
                        List.of(item("킹사이즈 패브릭 침대 프레임", "가구", 1350000), item("양측 월넛 협탁 2개", "가구", 420000), item("글로브 펜던트 조명 시공", "조명", 380000), item("암막 커튼 & 호텔 베딩", "패브릭", 650000))),
                new RecipeSeed("전세 10평 오피스텔 70만 원 북유럽 파스텔 거실", HousingType.OFFICETEL, 10, ContractType.JEONSE, true, 700000, StyleTag.NORDIC,
                        "세이지 그린 포인트 쿠션과 자작나무 합판 선반으로 산뜻한 느낌을 주었습니다.",
                        List.of(item("자작나무 4단 선반", "수납", 240000), item("1인용 라운지 체어", "가구", 230000), item("패브릭 쉐이드 플로어램프", "조명", 110000), item("노르딕 쿠션 & 블랭킷", "패브릭", 120000))),
                new RecipeSeed("5.5평 전세 원룸 48만 원 무타공 주방·현관 가림막", HousingType.ONE_ROOM, 6, ContractType.JEONSE, true, 480000, StyleTag.NATURAL,
                        "천장 압축형 원목 파티션으로 현관 찬바람과 시선을 동시에 차단했습니다.",
                        List.of(item("천장 고정 무타공 파티션", "가구", 165000), item("주방 틈새 수납장", "수납", 98000), item("아이보리 암막 커튼", "패브릭", 115000), item("센서형 LED 현관등", "조명", 102000))),
                new RecipeSeed("월세 8평 분리형 원룸 52만 원 1인 영화관 세팅", HousingType.ONE_ROOM, 8, ContractType.MONTHLY, true, 520000, StyleTag.MODERN,
                        "무타공 천장형 스크린 거치대와 빈백 소파로 주말 홈시네마를 만들었습니다.",
                        List.of(item("1인 빈백 소파 & 오토만", "가구", 175000), item("압축봉 80인치 스크린", "소품", 125000), item("암막 커튼 & 사이드 테이블", "패브릭", 145000), item("저조도 스틱 무드등", "조명", 75000))),
                new RecipeSeed("전세 14평 신혼 투룸 110만 원 우드 다이닝룸", HousingType.TWO_ROOM, 14, ContractType.JEONSE, true, 1100000, StyleTag.WOOD,
                        "4인용 애쉬 원목 식탁과 벤치 체어로 지인 초대가 즐거운 다이닝을 만들었습니다.",
                        List.of(item("애쉬 원목 1400 식탁 세트", "가구", 690000), item("유리 갓 장스탠드", "조명", 140000), item("원목 그릇장", "수납", 190000), item("린넨 테이블 러너", "패브릭", 80000))),
                new RecipeSeed("6평 원룸 28만 원 꼭꼬핀·블루택 벽꾸미기", HousingType.ONE_ROOM, 6, ContractType.MONTHLY, true, 280000, StyleTag.VINTAGE,
                        "못 자국 남지 않는 포스터 프레임과 마스킹 테이프로 아트월을 구성했습니다.",
                        List.of(item("초경량 아크릴 액자 4종", "소품", 64000), item("빈티지 패브릭 소파 커버", "패브릭", 89000), item("우드 협탁", "가구", 75000), item("호박 유리 단스탠드", "조명", 52000))),
                new RecipeSeed("전세 11평 오피스텔 75만 원 2인 재택근무 듀얼 데스크", HousingType.OFFICETEL, 11, ContractType.JEONSE, true, 750000, StyleTag.MINIMAL,
                        "1800 와이드 책상 하나에 모니터암 2개를 설치해 두 명이 함께 쓰는 서재를 만들었습니다.",
                        List.of(item("1800 화이트 롱 데스크", "가구", 260000), item("듀얼 클램프 모니터암 2개", "수납", 130000), item("눈부심 방지 데스크 램프 2개", "조명", 120000), item("메쉬 오피스 체어 2개", "가구", 240000))),
                new RecipeSeed("월세 5평 원룸 22만 원 침대 하부 수납 극대화", HousingType.ONE_ROOM, 5, ContractType.MONTHLY, true, 220000, StyleTag.NATURAL,
                        "높은 서랍형 수납 침대 커버와 투명 리빙박스로 계절 옷을 모두 정리했습니다.",
                        List.of(item("슬라이딩 침대 밑 수납함 6개", "수납", 78000), item("스커트형 베드 커버", "패브릭", 55000), item("접이식 사이드 테이블", "가구", 49000), item("터치식 무선 무드등", "조명", 38000))),
                new RecipeSeed("자가 20평 빌라 160만 원 노르딕 우드 거실", HousingType.TWO_ROOM, 20, ContractType.OWNED, false, 1600000, StyleTag.NORDIC,
                        "화이트 우드 톤으로 도배·몰딩을 정리하고 패브릭 소파로 포인트를 주었습니다.",
                        List.of(item("3인 아쿠아클린 소파", "가구", 890000), item("원목 커피 테이블", "가구", 240000), item("루이스폴센 스타일 펜던트", "조명", 190000), item("울 블렌드 거실 러그", "패브릭", 280000))),
                new RecipeSeed("전세 7평 북향 오피스텔 49만 원 따뜻함 채우기", HousingType.OFFICETEL, 7, ContractType.JEONSE, true, 490000, StyleTag.WOOD,
                        "차가운 대리석 바닥에 워셔블 러그를 깔고 우드 블라인드 느낌의 허니콤 쉐이드를 달았습니다.",
                        List.of(item("무타공 브라켓 허니콤 블라인드", "패브릭", 135000), item("대형 원형 우드 러그", "패브릭", 115000), item("3000K 플로어 장스탠드", "조명", 98000), item("라탄 수납 바스켓 3종", "수납", 142000))),
                new RecipeSeed("월세 6평 원룸 35만 원 게이밍·작업 겸용 데스크", HousingType.ONE_ROOM, 6, ContractType.MONTHLY, true, 350000, StyleTag.INDUSTRIAL,
                        "모니터 뒤 RGB/전구색 겸용 LED 바와 헤드셋 거치대로 밤 분위기를 살렸습니다.",
                        List.of(item("블랙 1200 게이밍 데스크", "가구", 145000), item("스마트 LED 라이트바", "조명", 65000), item("무타공 책상 하부 케이블 트레이", "수납", 32000), item("데스크 장패드 & 스탠드", "소품", 108000))),
                new RecipeSeed("전세 16평 투룸 135만 원 반려묘와 함께 사는 거실", HousingType.TWO_ROOM, 16, ContractType.JEONSE, true, 1350000, StyleTag.NATURAL,
                        "스크래치에 강한 모듈 소파와 무타공 캣타워 선반으로 고양이와 집사 모두 편한 공간입니다.",
                        List.of(item("스크래치 방지 패브릭 소파", "가구", 680000), item("압축봉 무타공 캣타워", "가구", 240000), item("원목 숨숨집 수납장", "수납", 230000), item("발수 단모 러그 & 조명", "패브릭", 200000))),
                new RecipeSeed("6.5평 원룸 40만 원 화이트&베이지 감성 침실", HousingType.ONE_ROOM, 7, ContractType.JEONSE, true, 400000, StyleTag.MINIMAL,
                        "모든 가전과 전선을 화이트 톤으로 통일하고 베이지 침구로 부드러움을 더했습니다.",
                        List.of(item("화이트 모듈 협탁", "가구", 115000), item("오트밀 린넨 침구 세트", "패브릭", 125000), item("버섯형 단스탠드", "조명", 68000), item("케이블 정리함 & 트레이", "수납", 92000))),
                new RecipeSeed("전세 8평 복층 58만 원 2층 아지트 침실 꾸미기", HousingType.OFFICETEL, 8, ContractType.JEONSE, true, 580000, StyleTag.WOOD,
                        "층고가 낮은 복층 2층에는 토퍼와 좌식 협탁, 무선 무드등으로 아늑함을 극대화했습니다.",
                        List.of(item("고밀도 메모리폼 토퍼", "가구", 210000), item("좌식 원목 티테이블", "가구", 89000), item("충전식 플리츠 무드등", "조명", 56000), item("복층 난간 수납 파우치 & 러그", "패브릭", 225000))),
                new RecipeSeed("월세 9평 원룸 60만 원 홈트·요가 존 확보 레이아웃", HousingType.ONE_ROOM, 9, ContractType.MONTHLY, true, 600000, StyleTag.MODERN,
                        "접이식 테이블과 벽밀착 수납으로 중앙에 요가매트 2장 넓이의 운동 공간을 비웠습니다.",
                        List.of(item("전신 거울 무타공 기대기형", "소품", 135000), item("슬림 4단 도어 수납장", "수납", 220000), item("접이식 게이트렉 식탁", "가구", 145000), item("코지 웜톤 장스탠드", "조명", 100000))),
                new RecipeSeed("자가 26평 아파트 220만 원 빈티지 서재 & LP룸", HousingType.APARTMENT, 26, ContractType.OWNED, false, 2200000, StyleTag.VINTAGE,
                        "월넛 책장과 턴테이블 수납장을 벽면 가득 채워 음악 감상 공간을 만들었습니다.",
                        List.of(item("월넛 5단 책장 2세트", "수납", 780000), item("턴테이블 전용 우드 캐비닛", "수납", 450000), item("가죽 1인 암체어", "가구", 620000), item("황동 플로어 램프", "조명", 350000))),
                new RecipeSeed("전세 6평 원룸 44만 원 주방 아일랜드 홈바 만들기", HousingType.ONE_ROOM, 6, ContractType.JEONSE, true, 440000, StyleTag.WOOD,
                        "조리 공간이 좁은 원룸에 수납형 아일랜드 식탁을 들여 요리와 식사를 한 번에 해결합니다.",
                        List.of(item("수납형 원목 아일랜드 테이블", "가구", 215000), item("바스툴 2개", "가구", 110000), item("싱크대 상부장 하부 LED 바", "조명", 35000), item("식기 건조대 & 트롤리", "수납", 80000))),
                new RecipeSeed("월세 7평 오피스텔 46만 원 호텔식 화이트 베딩", HousingType.OFFICETEL, 7, ContractType.MONTHLY, true, 460000, StyleTag.MINIMAL,
                        "80수 사틴 호텔 침구와 3000K 간접등 조합으로 퇴근 후 휴식 질을 높였습니다.",
                        List.of(item("80수 호텔식 순면 베딩", "패브릭", 165000), item("침대 헤드 무타공 LED 바", "조명", 45000), item("화이트 3단 모듈 협탁", "수납", 135000), item("차르르 쉬폰 커튼", "패브릭", 115000))),
                new RecipeSeed("전세 13평 투룸 88만 원 아이보리&브라운 거실", HousingType.TWO_ROOM, 13, ContractType.JEONSE, true, 880000, StyleTag.NATURAL,
                        "작은 거실에 2인 소파 대신 카우치형 1인 소파 2개를 마주 보게 두어 개방감을 줬습니다.",
                        List.of(item("패브릭 라운지 소파 2개", "가구", 460000), item("오크 원형 소파 테이블", "가구", 145000), item("장스탠드 & 쿠션 세트", "조명", 135000), item("아이보리 워셔블 러그", "패브릭", 140000))),
                new RecipeSeed("5평 남동향 원룸 29만 원 창가 데스크와 티타임", HousingType.ONE_ROOM, 5, ContractType.MONTHLY, true, 290000, StyleTag.NORDIC,
                        "창문을 바라보게 책상을 배치해 아침 햇살을 맞으며 작업하고 차를 마십니다.",
                        List.of(item("화이트 자작 1000 책상", "가구", 110000), item("패브릭 접이식 체어", "가구", 65000), item("노르딕 단스탠드", "조명", 48000), item("린넨 바란스 커튼", "패브릭", 67000))),
                new RecipeSeed("전세 10평 오피스텔 68만 원 인더스트리얼 바 테이블", HousingType.OFFICETEL, 10, ContractType.JEONSE, true, 680000, StyleTag.INDUSTRIAL,
                        "벽면에 철제 월선반(무타공 기둥형)을 세우고 하이 테이블로 카페 분위기를 냈습니다.",
                        List.of(item("기둥형 무타공 스틸 월선반", "수납", 220000), item("멀바우 상판 바 테이블", "가구", 240000), item("빈티지 철제 스툴 2개", "가구", 130000), item("스포트 클립 조명 2개", "조명", 90000))),
                new RecipeSeed("월세 6평 원룸 33만 원 겨울철 우풍 차단 코지 룸", HousingType.ONE_ROOM, 6, ContractType.MONTHLY, true, 330000, StyleTag.WOOD,
                        "삼중 방한 암막 커튼과 두툼한 극세사 러그로 난방비를 줄이고 온기를 더했습니다.",
                        List.of(item("삼중 방한 암막 커튼 + 압축봉", "패브릭", 98000), item("도톰 극세사 단모 러그", "패브릭", 85000), item("우드 3단 책장", "수납", 95000), item("2700K 호박등 스탠드", "조명", 52000))),
                new RecipeSeed("자가 28평 아파트 240만 원 미니멀 화이트 거실", HousingType.APARTMENT, 28, ContractType.OWNED, false, 2400000, StyleTag.MINIMAL,
                        "TV를 없애고 대형 무타공 거실장과 패브릭 소파, 프로젝터 중심의 거실로 바꿨습니다.",
                        List.of(item("모듈형 화이트 패브릭 소파", "가구", 1350000), item("로우형 거실 수납장", "수납", 520000), item("플로어 램프 & 실링 조명", "조명", 280000), item("미니멀 울 러그", "패브릭", 250000))),
                new RecipeSeed("전세 11평 투룸 79만 원 1인 크리에이터 촬영 스튜디오", HousingType.TWO_ROOM, 11, ContractType.JEONSE, true, 790000, StyleTag.MODERN,
                        "작은 방을 배경지 스탠드와 데스크 셋업, 키라이트까지 들어가는 스튜디오로 꾸몄습니다.",
                        List.of(item("1600 모던 블랙 데스크", "가구", 240000), item("무타공 배경천 스탠드 세트", "소품", 95000), item("소프트박스 + 3000K 보조등", "조명", 195000), item("수납형 캐비닛 2개", "수납", 260000))),
                new RecipeSeed("월세 8평 원룸 47만 원 라탄 & 린넨 발리 감성", HousingType.ONE_ROOM, 8, ContractType.MONTHLY, true, 470000, StyleTag.NATURAL,
                        "라탄 수납장과 야자수 잎 식물, 린넨 커튼으로 휴양지 숙소 같은 방을 만들었습니다.",
                        List.of(item("라탄 3단 서랍장", "수납", 185000), item("라탄 갓 장스탠드", "조명", 95000), item("네추럴 린넨 커튼", "패브릭", 88000), item("대형 화분 & 마크라메", "소품", 102000))),
                new RecipeSeed("전세 7평 오피스텔 51만 원 USM 느낌 모듈 가구 셋업", HousingType.OFFICETEL, 7, ContractType.JEONSE, true, 510000, StyleTag.MODERN,
                        "크롬 파이프 모듈 선반과 컬러 포인트 트레이로 경쾌한 모던 룸을 구성했습니다.",
                        List.of(item("2x2 크롬 모듈 선반", "수납", 215000), item("1인 모듈 체어", "가구", 145000), item("아르떼미데 스타일 단스탠드", "조명", 78000), item("컬러 러그 & 포스터", "패브릭", 72000))),
                new RecipeSeed("6평 반전세 원룸 36만 원 화장대 겸 파우더룸", HousingType.ONE_ROOM, 6, ContractType.JEONSE, true, 360000, StyleTag.WOOD,
                        "벽면 거치형 무타공 화장대 선반과 LED 거울로 좁은 방에도 파우더존을 만들었습니다.",
                        List.of(item("콘솔형 슬림 화장대", "가구", 145000), item("LED 터치 탁상 거울", "조명", 49000), item("벨벳 스툴", "가구", 68000), item("아크릴 코스메틱 정리함", "수납", 98000))),
                new RecipeSeed("월세 5.5평 원룸 26만 원 스트라이프 침구와 우드 포인트", HousingType.ONE_ROOM, 6, ContractType.MONTHLY, true, 260000, StyleTag.NORDIC,
                        "시원한 스트라이프 침구에 원목 트레이와 작은 화병으로 산뜻하게 꾸몄습니다.",
                        List.of(item("코튼 스트라이프 침구 세트", "패브릭", 89000), item("원목 베드 트레이", "가구", 45000), item("세라믹 화병 & 포스터", "소품", 58000), item("화이트 단스탠드", "조명", 68000))),
                new RecipeSeed("전세 17평 투룸 145만 원 부부 공동 서재 & 침실", HousingType.TWO_ROOM, 17, ContractType.JEONSE, true, 1450000, StyleTag.WOOD,
                        "큰 방에 침대와 2인 책상을 나란히 배치하고 패브릭 파티션으로 시선을 분리했습니다.",
                        List.of(item("원목 퀸 침대 프레임", "가구", 590000), item("1800 원목 2인 책상", "가구", 380000), item("린넨 패브릭 파티션", "패브릭", 190000), item("독서등 플로어 램프 2개", "조명", 290000))),
                new RecipeSeed("월세 9평 오피스텔 62만 원 빈티지 우드 레코드 코너", HousingType.OFFICETEL, 9, ContractType.MONTHLY, true, 620000, StyleTag.VINTAGE,
                        "창가 한쪽에 LP 랙과 빈티지 스피커, 패브릭 암체어를 두어 휴식 코너를 만들었습니다.",
                        List.of(item("빈티지 1인 패브릭 암체어", "가구", 240000), item("우드 LP 수납 선반", "수납", 145000), item("주름 갓 빈티지 스탠드", "조명", 85000), item("빈티지 자카드 러그", "패브릭", 150000))),
                new RecipeSeed("자가 22평 아파트 195만 원 모던 그레이 & 월넛 다이닝", HousingType.APARTMENT, 22, ContractType.OWNED, false, 1950000, StyleTag.MODERN,
                        "세라믹 상판 식탁과 월넛 의자, 라인 펜던트 조명으로 차분한 다이닝을 완성했습니다.",
                        List.of(item("4인 세라믹 다이닝 테이블", "가구", 920000), item("월넛 다이닝 체어 4개", "가구", 560000), item("라인형 펜던트 조명", "조명", 240000), item("그레이 린넨 커튼", "패브릭", 230000))),
                new RecipeSeed("전세 6.5평 원룸 43만 원 무타공 옷장 + 수납 벤치", HousingType.ONE_ROOM, 7, ContractType.JEONSE, true, 430000, StyleTag.MINIMAL,
                        "창가 밑에 수납형 벤치를 두어 앉는 공간과 이불 수납을 동시에 해결했습니다.",
                        List.of(item("패브릭 수납 벤치", "수납", 128000), item("커튼형 무타공 행거 커버", "패브릭", 95000), item("접이식 우드 테이블", "가구", 115000), item("웜화이트 단스탠드", "조명", 92000)))
        );

        for (int i = 0; i < seeds.size(); i++) {
            RecipeSeed s = seeds.get(i);
            User author = authors.get(i % authors.size());
            String photo = INTERIOR_PHOTOS.get(i % INTERIOR_PHOTOS.size());
            saveRecipe(author, s.title(), "## 공간 포인트\n" + s.summary(),
                    s.housingType(), (short) s.areaPyeong(), s.contractType(), s.noDrill(),
                    s.budget(), s.styleTag(), photo, s.items());
        }
    }

    private ItemSeed item(String name, String category, int price) {
        return new ItemSeed(name, category, price);
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

    private void seedActiveContest(List<User> authors) {
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

        List<ContestSeed> contestSeeds = List.of(
                new ContestSeed("3000K 간접조명으로 완성한 북향 원룸 책상",
                        "모니터 라이트바와 우드 선반만으로 아늑한 작업 공간을 만들었습니다.", INTERIOR_PHOTOS.get(1)),
                new ContestSeed("클램프 모니터암과 무타공 케이블 트레이 미니멀 데스크",
                        "전선 하나 보이지 않게 책상 하부로 정리해 6평 원룸이 훨씬 넓어 보입니다.", INTERIOR_PHOTOS.get(6)),
                new ContestSeed("창가 압축봉 린넨 커튼과 원목 책상 홈카페 존",
                        "주광색 형광등 대신 노란 램프 하나만 켜두면 카페에 온 기분이 납니다.", INTERIOR_PHOTOS.get(0)),
                new ContestSeed("타공판 거치대와 블랙 스틸 프레임 1인 작업실",
                        "못질 없이 책상 클램프 타공판에 자주 쓰는 헤드셋과 문구류를 정리했습니다.", INTERIOR_PHOTOS.get(10)),
                new ContestSeed("반려식물과 함께하는 햇살 창가 데스크테리어",
                        "모니터 옆 작은 토분과 우드 트레이로 눈의 피로를 덜어주는 책상입니다.", INTERIOR_PHOTOS.get(8))
        );

        for (int i = 0; i < contestSeeds.size(); i++) {
            ContestSeed cs = contestSeeds.get(i);
            User author = authors.get(i % authors.size());
            ContestPost post = ContestPost.builder()
                    .round(round)
                    .user(author)
                    .title(cs.title())
                    .content(cs.content())
                    .thumbnailUrl(cs.imageUrl())
                    .build();
            post.replaceImages(List.of(cs.imageUrl()));
            ContestPost saved = contestPostRepository.save(post);
            rankingRedisRepository.initializeEntry(round.getId(), saved.getId());
        }
    }

    private record ItemSeed(String name, String category, int price) {
    }

    private record RecipeSeed(String title, HousingType housingType, int areaPyeong, ContractType contractType,
                              boolean noDrill, int budget, StyleTag styleTag, String summary, List<ItemSeed> items) {
    }

    private record ContestSeed(String title, String content, String imageUrl) {
    }
}

package com.roomora.server.domain.rag;

import com.roomora.server.domain.rag.RagDocumentChunkRepository.SearchFilter;
import com.roomora.server.domain.rag.dto.RagDtos.RagFilterRequest;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RagQueryParser {

    private static final Pattern BUDGET_MANWON_PATTERN = Pattern.compile("(\\d+)\\s*만\\s*원?");
    private static final Pattern PYEONG_PATTERN = Pattern.compile("(\\d+)\\s*평");

    public SearchFilter parseFilter(String query, RagFilterRequest explicit) {
        String housingType = explicit != null ? explicit.housingType() : null;
        Boolean isNoDrill = explicit != null ? explicit.isNoDrill() : null;
        Integer maxBudget = explicit != null ? explicit.maxBudget() : null;
        Integer maxPyeong = explicit != null ? explicit.maxPyeong() : null;

        if (query != null) {
            if (housingType == null) {
                if (query.contains("원룸")) {
                    housingType = "ONE_ROOM";
                } else if (query.contains("투룸")) {
                    housingType = "TWO_ROOM";
                } else if (query.contains("오피스텔")) {
                    housingType = "OFFICETEL";
                } else if (query.contains("아파트")) {
                    housingType = "APARTMENT";
                }
            }
            if (isNoDrill == null && (query.contains("무타공") || query.contains("전세") || query.contains("월세"))) {
                isNoDrill = true;
            }
            if (maxBudget == null) {
                Matcher m = BUDGET_MANWON_PATTERN.matcher(query);
                if (m.find()) {
                    maxBudget = Integer.parseInt(m.group(1)) * 10_000;
                }
            }
            if (maxPyeong == null) {
                Matcher m = PYEONG_PATTERN.matcher(query);
                if (m.find()) {
                    maxPyeong = Integer.parseInt(m.group(1));
                }
            }
        }
        return new SearchFilter(housingType, isNoDrill, maxBudget, maxPyeong);
    }
}

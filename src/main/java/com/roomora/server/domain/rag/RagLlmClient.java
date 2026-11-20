package com.roomora.server.domain.rag;

import com.roomora.server.domain.rag.RagDocumentChunkRepository.RetrievedChunk;
import com.roomora.server.domain.rag.RagDocumentChunkRepository.SearchFilter;
import com.roomora.server.domain.rag.dto.RagDtos.*;

import java.util.List;

public interface RagLlmClient {

    GeneratedPlan generate(String userQuery, SearchFilter appliedFilter, List<RetrievedChunk> contexts);

    record GeneratedPlan(
            String summary,
            List<String> warnings,
            List<BudgetAllocation> budgetBreakdown,
            List<ChecklistDraftItem> checklistDraft
    ) {
    }
}

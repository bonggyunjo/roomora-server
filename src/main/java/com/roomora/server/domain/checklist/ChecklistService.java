package com.roomora.server.domain.checklist;

import com.roomora.server.domain.checklist.dto.ChecklistDtos.*;
import com.roomora.server.domain.user.User;
import com.roomora.server.domain.user.UserRepository;
import com.roomora.server.global.exception.BusinessException;
import com.roomora.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChecklistService {

    private final AiChecklistRepository checklistRepository;
    private final UserRepository userRepository;

    @Transactional
    public ChecklistDetailResponse create(Long userId, ChecklistCreateRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        AiChecklist checklist = AiChecklist.builder()
                .user(user)
                .title(request.title())
                .querySummary(request.querySummary())
                .targetBudget(request.targetBudget())
                .housingType(request.housingType())
                .isNoDrill(request.isNoDrill())
                .build();

        if (request.items() != null) {
            for (ChecklistItemCreateRequest itemReq : request.items()) {
                checklist.addItem(AiChecklistItem.builder()
                        .checklist(checklist)
                        .stepOrder(itemReq.stepOrder())
                        .category(itemReq.category())
                        .content(itemReq.content())
                        .estimatedCost(itemReq.estimatedCost() != null ? itemReq.estimatedCost() : 0)
                        .memo(itemReq.memo())
                        .build());
            }
        }

        AiChecklist saved = checklistRepository.save(checklist);
        return ChecklistDetailResponse.from(saved);
    }

    public List<ChecklistSummaryResponse> listMine(Long userId) {
        return checklistRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ChecklistSummaryResponse::from)
                .toList();
    }

    public ChecklistDetailResponse getDetail(Long checklistId, Long userId) {
        AiChecklist checklist = findOwnedChecklist(checklistId, userId);
        return ChecklistDetailResponse.from(checklist);
    }

    @Transactional
    public ChecklistDetailResponse addItem(Long checklistId, Long userId, ChecklistItemCreateRequest request) {
        AiChecklist checklist = findOwnedChecklist(checklistId, userId);
        checklist.addItem(AiChecklistItem.builder()
                .checklist(checklist)
                .stepOrder(request.stepOrder())
                .category(request.category())
                .content(request.content())
                .estimatedCost(request.estimatedCost() != null ? request.estimatedCost() : 0)
                .memo(request.memo())
                .build());
        checklistRepository.flush();
        return ChecklistDetailResponse.from(checklist);
    }

    @Transactional
    public ChecklistDetailResponse updateItem(Long checklistId, Long itemId, Long userId,
                                              ChecklistItemUpdateRequest request) {
        AiChecklist checklist = findOwnedChecklist(checklistId, userId);
        AiChecklistItem item = checklist.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CHECKLIST_ITEM_NOT_FOUND));

        item.update(request.isChecked(), request.memo(), request.content(), request.estimatedCost());
        return ChecklistDetailResponse.from(checklist);
    }

    @Transactional
    public ChecklistDetailResponse deleteItem(Long checklistId, Long itemId, Long userId) {
        AiChecklist checklist = findOwnedChecklist(checklistId, userId);
        boolean removed = checklist.getItems().removeIf(i -> i.getId().equals(itemId));
        if (!removed) {
            throw new BusinessException(ErrorCode.CHECKLIST_ITEM_NOT_FOUND);
        }
        return ChecklistDetailResponse.from(checklist);
    }

    @Transactional
    public void delete(Long checklistId, Long userId) {
        AiChecklist checklist = findOwnedChecklist(checklistId, userId);
        checklistRepository.delete(checklist);
    }

    private AiChecklist findOwnedChecklist(Long checklistId, Long userId) {
        AiChecklist checklist = checklistRepository.findById(checklistId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHECKLIST_NOT_FOUND));
        if (!checklist.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return checklist;
    }
}

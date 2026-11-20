package com.roomora.server.domain.checklist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiChecklistRepository extends JpaRepository<AiChecklist, Long> {

    List<AiChecklist> findByUserIdOrderByCreatedAtDesc(Long userId);
}

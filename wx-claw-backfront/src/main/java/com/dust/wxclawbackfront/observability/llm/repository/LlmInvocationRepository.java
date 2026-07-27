package com.dust.wxclawbackfront.observability.llm.repository;

import com.dust.wxclawbackfront.observability.llm.entity.LlmInvocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LlmInvocationRepository extends JpaRepository<LlmInvocation, String> {
    Optional<LlmInvocation> findByIdAndTenantId(String id, String tenantId);

    List<LlmInvocation> findAllByTenantIdAndConversationIdOrderByStartedAtAscSequenceNoAsc(
            String tenantId, String conversationId);

    Page<LlmInvocation> findAllByTenantIdOrderByStartedAtDesc(String tenantId, Pageable pageable);

    long countByTenantIdAndStartedAtGreaterThanEqual(String tenantId, LocalDateTime startedAt);

    long countByTenantIdAndStatusAndStartedAtGreaterThanEqual(String tenantId, String status, LocalDateTime startedAt);

    long countByStartedAtGreaterThanEqual(LocalDateTime startedAt);

    long countByStatusAndStartedAtGreaterThanEqual(String status, LocalDateTime startedAt);

    @Query("select coalesce(max(i.sequenceNo), 0) from LlmInvocation i " +
            "where i.tenantId = :tenantId and i.traceId = :traceId")
    Integer findMaxSequence(@Param("tenantId") String tenantId, @Param("traceId") String traceId);
}

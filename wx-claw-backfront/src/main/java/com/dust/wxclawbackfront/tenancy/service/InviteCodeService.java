package com.dust.wxclawbackfront.tenancy.service;

import com.dust.wxclawbackfront.admin.api.dto.AdminDtos.InviteCode;
import com.dust.wxclawbackfront.tenancy.entity.TenantInviteCode;
import com.dust.wxclawbackfront.tenancy.repository.TenantInviteCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 注册邀请码管理：生成、列表、停用，以及注册时的原子消费。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteCodeService {

    /** 去掉易混淆字符（0/O、1/I/L 等）的字符集。 */
    private static final char[] CODE_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 10;

    private final TenantInviteCodeRepository inviteCodeRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public List<InviteCode> list() {
        return inviteCodeRepository.findAll().stream()
                .sorted(Comparator.comparing(TenantInviteCode::getCreatedAt).reversed())
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public List<String> generate(int count, Integer quota, LocalDateTime expiresAt,
                                 String remark, String createdBy) {
        if (count < 1 || count > 50) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "单次生成数量需为 1-50",
                    HttpStatus.BAD_REQUEST);
        }
        if (quota != null && quota < 1) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "配额需大于 0（或不限）",
                    HttpStatus.BAD_REQUEST);
        }
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "过期时间不能早于当前时间",
                    HttpStatus.BAD_REQUEST);
        }

        List<String> codes = new ArrayList<>(count);
        while (codes.size() < count) {
            String code = randomCode();
            if (inviteCodeRepository.findByCode(code).isPresent()) {
                continue;
            }
            TenantInviteCode entity = new TenantInviteCode();
            entity.setCode(code);
            entity.setStatus("ACTIVE");
            entity.setQuota(quota);
            entity.setExpiresAt(expiresAt);
            entity.setRemark(remark == null || remark.isBlank() ? null : remark.trim());
            entity.setCreatedBy(createdBy);
            inviteCodeRepository.save(entity);
            codes.add(code);
        }
        log.info("生成注册邀请码 {} 个: createdBy={}", codes.size(), createdBy);
        return codes;
    }

    @Transactional
    public void disable(String code) {
        TenantInviteCode entity = inviteCodeRepository.findByCode(code)
                .orElseThrow(() -> new TenantRegistrationException("NOT_FOUND", "邀请码不存在",
                        HttpStatus.NOT_FOUND));
        entity.setStatus("DISABLED");
        inviteCodeRepository.save(entity);
    }

    /** 注册时原子消费一个名额；无效/过期/停用/超配额均返回 false。 */
    @Transactional
    public boolean consume(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String normalized = code.trim().toUpperCase();
        return inviteCodeRepository.consume(normalized, LocalDateTime.now()) == 1;
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS[secureRandom.nextInt(CODE_CHARS.length)]);
        }
        return sb.toString();
    }

    private InviteCode toDto(TenantInviteCode entity) {
        return new InviteCode(entity.getId(), entity.getCode(), entity.getStatus(), entity.getQuota(),
                entity.getUsedCount(), entity.getExpiresAt(), entity.getRemark(),
                entity.getCreatedBy(), entity.getCreatedAt());
    }
}

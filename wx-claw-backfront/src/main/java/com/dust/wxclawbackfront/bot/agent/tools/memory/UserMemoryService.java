package com.dust.wxclawbackfront.bot.agent.tools.memory;

import com.dust.wxclawbackfront.bot.dao.entity.UserLearning;
import com.dust.wxclawbackfront.bot.dao.entity.UserProfile;
import com.dust.wxclawbackfront.bot.dao.repository.UserLearningRepository;
import com.dust.wxclawbackfront.bot.dao.repository.UserProfileRepository;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户记忆服务
 * 管理用户画像和学习规则的读写
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserMemoryService {

    private final UserProfileRepository profileRepository;
    private final UserLearningRepository learningRepository;

    // ========== 用户画像 ==========

    @Transactional
    public void saveProfile(String userId, String category, String keyName, String keyValue, String source) {
        saveProfileWithConfidence(userId, category, keyName, keyValue, source, new BigDecimal("0.50"), null);
    }

    /**
     * 保存用户画像（带置信度与过期时间），供自动记忆抽取使用。
     * 高置信度覆盖低置信度；不存在则新建。
     */
    @Transactional
    public void saveProfileWithConfidence(String userId, String category, String keyName, String keyValue,
                                          String source, BigDecimal confidence, LocalDateTime expiresAt) {
        if (userId == null || userId.isBlank()) return;

        Optional<UserProfile> existing = profileRepository.findByTenantIdAndUserIdAndCategoryAndKeyName(
                tenantId(), userId, category, keyName);
        if (existing.isPresent()) {
            UserProfile profile = existing.get();
            BigDecimal incoming = confidence == null ? new BigDecimal("0.50") : confidence;
            BigDecimal current = profile.getConfidence() == null ? BigDecimal.ZERO : profile.getConfidence();
            if (incoming.compareTo(current) >= 0) {
                profile.setKeyValue(keyValue);
                profile.setSource(source);
                profile.setConfidence(incoming);
                profile.setExpiresAt(expiresAt);
                profileRepository.save(profile);
                log.info("更新用户画像: userId={}, {}={}, confidence={}", userId, keyName, keyValue, incoming);
            } else {
                log.debug("忽略低置信度画像覆盖: userId={}, key={}, incoming={}, current={}",
                        userId, keyName, incoming, current);
            }
        } else {
            UserProfile profile = new UserProfile();
            profile.setUserId(userId);
            profile.setCategory(category);
            profile.setKeyName(keyName);
            profile.setKeyValue(keyValue);
            profile.setSource(source);
            profile.setConfidence(confidence == null ? new BigDecimal("0.50") : confidence);
            profile.setExpiresAt(expiresAt);
            profileRepository.save(profile);
            log.info("新增用户画像: userId={}, {}={}, confidence={}",
                    userId, keyName, keyValue, confidence);
        }
    }

    public List<UserProfile> getProfiles(String userId) {
        if (userId == null || userId.isBlank()) return List.of();
        return profileRepository.findByTenantIdAndUserId(tenantId(), userId);
    }

    public List<UserProfile> getProfilesByCategory(String userId, String category) {
        if (userId == null || userId.isBlank()) return List.of();
        return profileRepository.findByTenantIdAndUserIdAndCategory(tenantId(), userId, category);
    }

    // ========== 学习规则 ==========

    @Transactional
    public void saveLearning(String userId, String trigger, String instruction) {
        if (userId == null || userId.isBlank()) return;

        UserLearning learning = new UserLearning();
        learning.setUserId(userId);
        learning.setTrigger(trigger);
        learning.setInstruction(instruction);
        learning.setActive(true);
        learningRepository.save(learning);
        log.info("新增学习规则: userId={}, trigger={}, instruction={}", userId, trigger, instruction);
    }

    public List<UserLearning> getActiveLearnings(String userId) {
        if (userId == null || userId.isBlank()) return List.of();
        return learningRepository.findByTenantIdAndUserIdAndActiveTrue(tenantId(), userId);
    }

    public List<UserLearning> getActiveLearningsByTrigger(String userId, String trigger) {
        if (userId == null || userId.isBlank()) return List.of();
        return learningRepository.findByTenantIdAndUserIdAndTriggerAndActiveTrue(tenantId(), userId, trigger);
    }

    @Transactional
    public boolean deactivateLearning(Long learningId) {
        Optional<UserLearning> opt = learningRepository.findByTenantIdAndId(tenantId(), learningId);
        if (opt.isPresent()) {
            UserLearning learning = opt.get();
            learning.setActive(false);
            learningRepository.save(learning);
            return true;
        }
        return false;
    }

    private static final String ROLE_PROMPT_CATEGORY = "role_prompt";
    private static final String ROLE_PROMPT_KEY = "description";

    // ========== 角色提示词 ==========

    /**
     * 保存用户角色提示词（让 AI 扮演指定角色）
     */
    @Transactional
    public void saveRolePrompt(String userId, String roleDescription) {
        if (userId == null || userId.isBlank()) return;
        if (roleDescription == null || roleDescription.isBlank()) {
            removeRolePrompt(userId);
            return;
        }
        saveProfile(userId, ROLE_PROMPT_CATEGORY, ROLE_PROMPT_KEY, roleDescription.trim(), "user_told");
        log.info("角色提示词已保存: userId={}, role={}", userId, roleDescription);
    }

    /**
     * 获取用户角色提示词
     */
    public String getRolePrompt(String userId) {
        if (userId == null || userId.isBlank()) return null;
        return profileRepository.findByTenantIdAndUserIdAndCategoryAndKeyName(
                        tenantId(), userId, ROLE_PROMPT_CATEGORY, ROLE_PROMPT_KEY)
                .map(UserProfile::getKeyValue)
                .orElse(null);
    }

    /**
     * 删除用户角色提示词
     */
    @Transactional
    public void removeRolePrompt(String userId) {
        if (userId == null || userId.isBlank()) return;
        profileRepository.findByTenantIdAndUserIdAndCategoryAndKeyName(
                        tenantId(), userId, ROLE_PROMPT_CATEGORY, ROLE_PROMPT_KEY)
                .ifPresent(profile -> {
                    profileRepository.delete(profile);
                    log.info("角色提示词已删除: userId={}", userId);
                });
    }

    // ========== System Prompt 构建 ==========

    /**
     * 构建用户记忆相关的 system prompt 片段
     */
    public String buildMemoryPrompt(String userId) {
        if (userId == null || userId.isBlank()) {
            log.debug("buildMemoryPrompt: userId 为空，跳过加载记忆");
            return "";
        }

        String rolePrompt = getRolePrompt(userId);
        List<UserProfile> allProfiles = getProfiles(userId);
        List<UserLearning> learnings = getActiveLearnings(userId);

        // 过滤掉角色提示词，避免在"用户画像"中重复
        List<UserProfile> profiles = allProfiles.stream()
                .filter(p -> !ROLE_PROMPT_CATEGORY.equals(p.getCategory()))
                .filter(p -> p.getExpiresAt() == null || p.getExpiresAt().isAfter(LocalDateTime.now()))
                .sorted(Comparator
                        .comparing((UserProfile p) -> p.getConfidence() == null ? BigDecimal.ZERO : p.getConfidence(),
                                Comparator.reverseOrder())
                        .thenComparing(p -> p.getUpdatedAt() == null ? LocalDateTime.MIN : p.getUpdatedAt(),
                                Comparator.reverseOrder()))
                .toList();

        log.info("buildMemoryPrompt: userId={}, profiles={}, learnings={}, hasRole={}",
                userId, profiles.size(), learnings.size(), rolePrompt != null);

        if (rolePrompt == null && profiles.isEmpty() && learnings.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n# 用户记忆\n\n");

        // 角色提示词放在最前面
        if (rolePrompt != null && !rolePrompt.isBlank()) {
            sb.append("## 角色设定\n\n");
            sb.append("用户为你设定了以下角色身份，请在对话中严格按此角色行为：\n\n");
            sb.append(rolePrompt).append("\n\n");
        }

        if (!profiles.isEmpty()) {
            sb.append("## 用户画像\n\n");
            sb.append("以下是该用户已知的信息，在回复时请参考：\n\n");
            for (UserProfile p : profiles) {
                sb.append("- ").append(p.getKeyName()).append(": ").append(p.getKeyValue());
                if ("user_told".equals(p.getSource())) {
                    sb.append(" （用户主动告知）");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!learnings.isEmpty()) {
            sb.append("## 用户学习规则\n\n");
            sb.append("以下是用户之前教给你的规则，请在对应场景中遵循：\n\n");
            for (UserLearning l : learnings) {
                sb.append("- [").append(l.getTrigger()).append("] ").append(l.getInstruction()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## 记忆更新指引\n\n");
        sb.append("【重要】当用户透露个人信息或要求你记住某些事情时，你必须调用工具来保存，而不仅仅是口头回复。\n\n");
        sb.append("### 何时调用 set_role_prompt 工具：\n");
        sb.append("- 用户明确要求你扮演某个角色，如\"扮演一个诗人\"、\"假装你是xxx\"、\"你是一个xxx\"\n");
        sb.append("- 用户要求你改变说话风格或身份，如\"用李白的风格说话\"、\"你是一个老师\"\n");
        sb.append("- 角色设定会持续影响后续所有对话，直到用户要求清除\n\n");
        sb.append("### 何时调用 remove_role_prompt 工具：\n");
        sb.append("- 用户说\"恢复默认\"、\"不用扮演了\"、\"取消角色设定\"\n\n");
        sb.append("### 何时调用 update_user_profile 工具：\n");
        sb.append("- 用户透露个人信息：城市、职业、偏好、习惯、作息等\n");
        sb.append("- 用户说\"我住在北京\"、\"我是程序员\"、\"我喜欢xxx\"等\n");
        sb.append("- 用户纠正你之前记住的错误信息\n\n");
        sb.append("### 何时调用 add_user_learning 工具：\n");
        sb.append("- 用户说\"以后xxx的时候要xxx\"\n");
        sb.append("- 用户说\"下次记住xxx\"、\"记住xxx\"\n");
        sb.append("- 用户说\"以后回复要xxx\"、\"总结时要xxx\"\n\n");
        sb.append("### 调用时机：\n");
        sb.append("- 在回复用户之前先调用工具保存记忆\n");
        sb.append("- 不要主动询问用户要记录什么，只在用户自然透露时记录\n");
        sb.append("- 如果不确定是否应该记录，宁可记录也不要遗漏\n\n");

        return sb.toString();
    }

    private String tenantId() {
        return TenantContextHolder.require().tenantId();
    }
}

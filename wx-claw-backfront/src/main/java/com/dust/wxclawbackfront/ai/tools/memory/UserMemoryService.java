package com.dust.wxclawbackfront.ai.tools.memory;

import com.dust.wxclawbackfront.ai.dao.entity.UserLearning;
import com.dust.wxclawbackfront.ai.dao.entity.UserProfile;
import com.dust.wxclawbackfront.ai.dao.repository.UserLearningRepository;
import com.dust.wxclawbackfront.ai.dao.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        if (userId == null || userId.isBlank()) return;

        Optional<UserProfile> existing = profileRepository.findByUserIdAndCategoryAndKeyName(userId, category, keyName);
        if (existing.isPresent()) {
            UserProfile profile = existing.get();
            profile.setKeyValue(keyValue);
            profile.setSource(source);
            profileRepository.save(profile);
            log.info("更新用户画像: userId={}, {}={}", userId, keyName, keyValue);
        } else {
            UserProfile profile = new UserProfile();
            profile.setUserId(userId);
            profile.setCategory(category);
            profile.setKeyName(keyName);
            profile.setKeyValue(keyValue);
            profile.setSource(source);
            profileRepository.save(profile);
            log.info("新增用户画像: userId={}, {}={}", userId, keyName, keyValue);
        }
    }

    public List<UserProfile> getProfiles(String userId) {
        if (userId == null || userId.isBlank()) return List.of();
        return profileRepository.findByUserId(userId);
    }

    public List<UserProfile> getProfilesByCategory(String userId, String category) {
        if (userId == null || userId.isBlank()) return List.of();
        return profileRepository.findByUserIdAndCategory(userId, category);
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
        return learningRepository.findByUserIdAndActiveTrue(userId);
    }

    public List<UserLearning> getActiveLearningsByTrigger(String userId, String trigger) {
        if (userId == null || userId.isBlank()) return List.of();
        return learningRepository.findByUserIdAndTriggerAndActiveTrue(userId, trigger);
    }

    @Transactional
    public boolean deactivateLearning(Long learningId) {
        Optional<UserLearning> opt = learningRepository.findById(learningId);
        if (opt.isPresent()) {
            UserLearning learning = opt.get();
            learning.setActive(false);
            learningRepository.save(learning);
            return true;
        }
        return false;
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

        List<UserProfile> profiles = getProfiles(userId);
        List<UserLearning> learnings = getActiveLearnings(userId);

        log.info("buildMemoryPrompt: userId={}, profiles={}, learnings={}", userId, profiles.size(), learnings.size());

        if (profiles.isEmpty() && learnings.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n# 用户记忆\n\n");

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
}

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
        if (userId == null || userId.isBlank()) return "";

        List<UserProfile> profiles = getProfiles(userId);
        List<UserLearning> learnings = getActiveLearnings(userId);

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
        sb.append("在对话中，如果用户透露了个人信息（如城市、职业、偏好、习惯等），请使用 update_user_profile 工具记录。\n");
        sb.append("如果用户说\"以后xxx的时候要xxx\"、\"下次记住xxx\"等学习性指令，请使用 add_user_learning 工具记录。\n");
        sb.append("不要主动询问用户要记录什么，只在用户自然透露时记录。\n");

        return sb.toString();
    }
}

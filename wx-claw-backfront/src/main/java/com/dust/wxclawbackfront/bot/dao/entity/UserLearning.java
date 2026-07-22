package com.dust.wxclawbackfront.bot.dao.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户学习规则实体
 * 存储用户"教"给 AI 的任务处理规则
 */
@Data
@Entity
@Table(name = "user_learning")
public class UserLearning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String userId;

    /**
     * 触发场景：summary / daily_report / reply / general 等
     */
    @Column(nullable = false, length = 50)
    private String trigger;

    /**
     * 用户的学习指令
     */
    @Column(nullable = false, length = 500)
    private String instruction;

    /**
     * 是否生效
     */
    @Column(nullable = false)
    private boolean active = true;

    @Column
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

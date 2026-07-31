package com.dust.wxclawbackfront.tenancy.repository;

import com.dust.wxclawbackfront.tenancy.entity.TenantBot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Bot 配置仓储；调用方必须明确传入租户条件或在返回后建立对应租户上下文。 */
public interface TenantBotRepository extends JpaRepository<TenantBot, Long> {
    /** 启动渠道运行实例时读取所有租户的有效 Bot，结果不可直接用于租户业务查询。 */
    List<TenantBot> findByChannelAndStatus(String channel, String status);

    /** 管理端按租户列出 Bot，避免把其他租户配置混入响应。 */
    List<TenantBot> findAllByTenantId(String tenantId);
}

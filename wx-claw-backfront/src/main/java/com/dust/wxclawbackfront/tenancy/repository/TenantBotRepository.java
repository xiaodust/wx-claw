package com.dust.wxclawbackfront.tenancy.repository;

import com.dust.wxclawbackfront.tenancy.entity.TenantBot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantBotRepository extends JpaRepository<TenantBot, Long> {
    List<TenantBot> findByChannelAndStatus(String channel, String status);
}

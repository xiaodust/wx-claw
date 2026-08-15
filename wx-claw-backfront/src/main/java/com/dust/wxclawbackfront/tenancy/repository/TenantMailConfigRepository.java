package com.dust.wxclawbackfront.tenancy.repository;

import com.dust.wxclawbackfront.tenancy.entity.TenantMailConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantMailConfigRepository extends JpaRepository<TenantMailConfig, String> {
}

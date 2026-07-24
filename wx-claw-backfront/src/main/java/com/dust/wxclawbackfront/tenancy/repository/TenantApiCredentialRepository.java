package com.dust.wxclawbackfront.tenancy.repository;

import com.dust.wxclawbackfront.tenancy.entity.TenantApiCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantApiCredentialRepository extends JpaRepository<TenantApiCredential, Long> {
    Optional<TenantApiCredential> findByCredentialId(String credentialId);
}

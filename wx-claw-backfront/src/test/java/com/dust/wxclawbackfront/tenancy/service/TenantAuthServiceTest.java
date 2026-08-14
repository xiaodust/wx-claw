package com.dust.wxclawbackfront.tenancy.service;

import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.AuthResult;
import com.dust.wxclawbackfront.tenancy.entity.Tenant;
import com.dust.wxclawbackfront.tenancy.entity.TenantAccount;
import com.dust.wxclawbackfront.tenancy.entity.TenantSession;
import com.dust.wxclawbackfront.tenancy.repository.TenantAccountRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantSessionRepository;
import com.dust.wxclawbackfront.tenancy.security.ApiSecretHasher;
import com.dust.wxclawbackfront.tenancy.security.PublicAuthRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantAuthServiceTest {

    private final TenantAccountRepository accountRepository = mock(TenantAccountRepository.class);
    private final TenantSessionRepository sessionRepository = mock(TenantSessionRepository.class);
    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final ApiSecretHasher secretHasher = mock(ApiSecretHasher.class);
    private final PublicAuthRateLimiter rateLimiter = mock(PublicAuthRateLimiter.class);
    private final TenantAuthService service = new TenantAuthService(
            accountRepository, sessionRepository, tenantRepository, secretHasher, rateLimiter);

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void loginIssuesSessionWithHashedTokenOnly() {
        TenantAccount account = account("tenant-1", "ops", "hash");
        Tenant tenant = tenant("tenant-1");
        when(accountRepository.findByUsername("ops")).thenReturn(Optional.of(account));
        when(secretHasher.matches("secret-1234", "hash")).thenReturn(true);
        when(tenantRepository.findByTenantId("tenant-1")).thenReturn(Optional.of(tenant));
        when(sessionRepository.save(any(TenantSession.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResult result = service.login("Ops", "secret-1234", "1.2.3.4");

        assertThat(result.sessionToken()).startsWith("sess_");
        assertThat(result.tenantCode()).isEqualTo("t1");
        ArgumentCaptor<TenantSession> sessionCaptor = ArgumentCaptor.forClass(TenantSession.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        TenantSession session = sessionCaptor.getValue();
        assertThat(session.getTokenHash()).isNotEqualTo(result.sessionToken());
        assertThat(session.getTokenHash()).hasSize(64);
        assertThat(session.getAccountId()).isEqualTo(account.getId());
        assertThat(session.getTenantId()).isEqualTo("tenant-1");
        assertThat(TenantContextHolder.getNullable()).isNull();
    }

    @Test
    void loginRejectsWrongPasswordWithGenericError() {
        TenantAccount account = account("tenant-1", "ops", "hash");
        when(accountRepository.findByUsername("ops")).thenReturn(Optional.of(account));
        when(secretHasher.matches("wrong-password", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login("ops", "wrong-password", "1.2.3.4"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> {
                    TenantRegistrationException tre = (TenantRegistrationException) ex;
                    assertThat(tre.errorCode()).isEqualTo("INVALID_CREDENTIALS");
                    assertThat(tre.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(tre.getMessage()).contains("用户名或密码错误");
                });
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void loginRejectsUnknownUserWithSameGenericError() {
        when(accountRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("ghost", "whatever123", "1.2.3.4"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> assertThat(((TenantRegistrationException) ex).errorCode())
                        .isEqualTo("INVALID_CREDENTIALS"));
        // 即使账号不存在也要执行一次密码哈希校验，抹平时序差异。
        verify(secretHasher).matches(anyString(), anyString());
    }

    @Test
    void loginRejectsDisabledAccount() {
        TenantAccount account = account("tenant-1", "ops", "hash");
        account.setStatus("DISABLED");
        when(accountRepository.findByUsername("ops")).thenReturn(Optional.of(account));
        when(secretHasher.matches("secret-1234", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login("ops", "secret-1234", "1.2.3.4"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> assertThat(((TenantRegistrationException) ex).errorCode())
                        .isEqualTo("INVALID_CREDENTIALS"));
    }

    @Test
    void authenticateSessionRestoresTenantContext() {
        TenantSession session = new TenantSession();
        session.setId(10L);
        session.setTenantId("tenant-1");
        session.setAccountId(1L);
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(sessionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(session));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account("tenant-1", "ops", "hash")));
        when(tenantRepository.findByTenantId("tenant-1")).thenReturn(Optional.of(tenant("tenant-1")));

        TenantContext context = service.authenticateSession("sess_some-token");

        assertThat(context).isNotNull();
        assertThat(context.tenantId()).isEqualTo("tenant-1");
        assertThat(context.scopes()).contains("userbot:read", "aiconfig:write");
        assertThat(context.scopes()).contains("account:read", "account:write");
        assertThat(context.roles()).contains("TENANT_ADMIN");
        assertThat(context.internalUserId()).isEqualTo("account:ops");
    }

    @Test
    void changePasswordUpdatesHashAndRevokesSessions() {
        TenantAccount account = account("tenant-1", "ops", "old-hash");
        when(accountRepository.findByUsername("ops")).thenReturn(Optional.of(account));
        when(secretHasher.matches("old-pass-123", "old-hash")).thenReturn(true);
        when(secretHasher.hash("new-pass-456")).thenReturn("new-hash");
        when(accountRepository.save(any(TenantAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        TenantContextHolder.set(new TenantContext("tenant-1", "REST", null, "account:ops", null,
                Set.of("TENANT_ADMIN"), Set.of(), "req"));

        service.changePassword("old-pass-123", "new-pass-456");

        assertThat(account.getPasswordHash()).isEqualTo("new-hash");
        verify(sessionRepository).deleteByAccountId(1L);
        assertThat(TenantContextHolder.getNullable()).isNotNull();
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        TenantAccount account = account("tenant-1", "ops", "old-hash");
        when(accountRepository.findByUsername("ops")).thenReturn(Optional.of(account));
        when(secretHasher.matches("wrong-pass", "old-hash")).thenReturn(false);
        TenantContextHolder.set(new TenantContext("tenant-1", "REST", null, "account:ops", null,
                Set.of(), Set.of(), "req"));

        assertThatThrownBy(() -> service.changePassword("wrong-pass", "new-pass-456"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> {
                    TenantRegistrationException tre = (TenantRegistrationException) ex;
                    assertThat(tre.errorCode()).isEqualTo("INVALID_CREDENTIALS");
                    assertThat(tre.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
        verify(sessionRepository, never()).deleteByAccountId(any());
    }

    @Test
    void changePasswordRejectsShortNewPassword() {
        TenantAccount account = account("tenant-1", "ops", "old-hash");
        when(accountRepository.findByUsername("ops")).thenReturn(Optional.of(account));
        when(secretHasher.matches("old-pass-123", "old-hash")).thenReturn(true);
        TenantContextHolder.set(new TenantContext("tenant-1", "REST", null, "account:ops", null,
                Set.of(), Set.of(), "req"));

        assertThatThrownBy(() -> service.changePassword("old-pass-123", "123"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> assertThat(((TenantRegistrationException) ex).errorCode())
                        .isEqualTo("VALIDATION_ERROR"));
    }

    @Test
    void authenticateSessionRejectsExpiredToken() {
        TenantSession session = new TenantSession();
        session.setId(10L);
        session.setTenantId("tenant-1");
        session.setAccountId(1L);
        session.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(sessionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(session));

        assertThat(service.authenticateSession("sess_some-token")).isNull();
    }

    @Test
    void authenticateSessionIgnoresNonSessionTokens() {
        assertThat(service.authenticateSession("tk_abc.123")).isNull();
        assertThat(service.authenticateSession(null)).isNull();
        verify(sessionRepository, never()).findByTokenHash(anyString());
    }

    private TenantAccount account(String tenantId, String username, String hash) {
        TenantAccount account = new TenantAccount();
        account.setId(1L);
        account.setTenantId(tenantId);
        account.setUsername(username);
        account.setPasswordHash(hash);
        account.setStatus("ACTIVE");
        return account;
    }

    private Tenant tenant(String tenantId) {
        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setTenantCode("t1");
        tenant.setTenantName("租户");
        tenant.setStatus("ACTIVE");
        return tenant;
    }
}

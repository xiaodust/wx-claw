package com.dust.wxclawbackfront.tenancy.service;

import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.AdminLoginResult;
import com.dust.wxclawbackfront.tenancy.entity.AdminAccount;
import com.dust.wxclawbackfront.tenancy.entity.AdminSession;
import com.dust.wxclawbackfront.tenancy.repository.AdminAccountRepository;
import com.dust.wxclawbackfront.tenancy.repository.AdminSessionRepository;
import com.dust.wxclawbackfront.tenancy.security.ApiSecretHasher;
import com.dust.wxclawbackfront.tenancy.security.PublicAuthRateLimiter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthServiceTest {

    private final AdminAccountRepository accountRepository = mock(AdminAccountRepository.class);
    private final AdminSessionRepository sessionRepository = mock(AdminSessionRepository.class);
    private final ApiSecretHasher secretHasher = mock(ApiSecretHasher.class);
    private final PublicAuthRateLimiter rateLimiter = mock(PublicAuthRateLimiter.class);
    private final AdminAuthService service = new AdminAuthService(
            accountRepository, sessionRepository, secretHasher, rateLimiter);

    @Test
    void loginIssuesAdminSessionWithHashedToken() {
        AdminAccount account = account("ops");
        when(accountRepository.findByUsername("ops")).thenReturn(Optional.of(account));
        when(secretHasher.matches("admin-pass-123", "hash")).thenReturn(true);
        when(sessionRepository.save(any(AdminSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.save(any(AdminAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminLoginResult result = service.login("Ops", "admin-pass-123", "1.2.3.4");

        assertThat(result.sessionToken()).startsWith("asess_");
        assertThat(result.username()).isEqualTo("ops");
        assertThat(result.role()).isEqualTo("SUPER_ADMIN");
        ArgumentCaptor<AdminSession> captor = ArgumentCaptor.forClass(AdminSession.class);
        verify(sessionRepository).save(captor.capture());
        AdminSession session = captor.getValue();
        assertThat(session.getTokenHash()).isNotEqualTo(result.sessionToken());
        assertThat(session.getTokenHash()).hasSize(64);
    }

    @Test
    void loginRejectsWrongPasswordWithGenericError() {
        AdminAccount account = account("ops");
        when(accountRepository.findByUsername("ops")).thenReturn(Optional.of(account));
        when(secretHasher.matches("wrong-pass", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login("ops", "wrong-pass", "1.2.3.4"))
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
    void loginRejectsUnknownUserWithSameError() {
        when(accountRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("ghost", "whatever-123", "1.2.3.4"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> assertThat(((TenantRegistrationException) ex).errorCode())
                        .isEqualTo("INVALID_CREDENTIALS"));
        verify(secretHasher).matches(anyString(), anyString());
    }

    @Test
    void authenticateSessionRestoresPlatformAdminContext() {
        AdminSession session = new AdminSession();
        session.setId(1L);
        session.setAdminAccountId(1L);
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(sessionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(session));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account("ops")));

        TenantContext context = service.authenticateSession("asess_some-token");

        assertThat(context).isNotNull();
        assertThat(context.tenantId()).isEqualTo("platform");
        assertThat(context.scopes()).contains("*");
        assertThat(context.roles()).contains("PLATFORM_ADMIN");
        assertThat(context.internalUserId()).isEqualTo("admin:ops");
    }

    @Test
    void authenticateSessionRejectsExpiredOrNonAdminTokens() {
        AdminSession expired = new AdminSession();
        expired.setId(1L);
        expired.setAdminAccountId(1L);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(sessionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThat(service.authenticateSession("asess_expired")).isNull();
        assertThat(service.authenticateSession("sess_tenant-token")).isNull();
        assertThat(service.authenticateSession(null)).isNull();
    }

    @Test
    void loginPropagatesRateLimit() {
        doThrow(new TenantRegistrationException("RATE_LIMITED", "太频繁", HttpStatus.TOO_MANY_REQUESTS))
                .when(rateLimiter).checkAdminLogin(anyString(), anyString());

        assertThatThrownBy(() -> service.login("ops", "admin-pass-123", "1.2.3.4"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> assertThat(((TenantRegistrationException) ex).status())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    private AdminAccount account(String username) {
        AdminAccount account = new AdminAccount();
        account.setId(1L);
        account.setUsername(username);
        account.setPasswordHash("hash");
        account.setRole("SUPER_ADMIN");
        account.setStatus("ACTIVE");
        return account;
    }
}

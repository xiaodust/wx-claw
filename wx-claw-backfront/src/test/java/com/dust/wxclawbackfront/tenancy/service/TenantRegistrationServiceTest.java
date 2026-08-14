package com.dust.wxclawbackfront.tenancy.service;

import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.RegisterTenantRequest;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.RegisteredTenant;
import com.dust.wxclawbackfront.tenancy.entity.Tenant;
import com.dust.wxclawbackfront.tenancy.entity.TenantApiCredential;
import com.dust.wxclawbackfront.tenancy.repository.TenantAccountRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantApiCredentialRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantRepository;
import com.dust.wxclawbackfront.tenancy.security.ApiSecretHasher;
import com.dust.wxclawbackfront.tenancy.security.PublicAuthRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantRegistrationServiceTest {

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final TenantApiCredentialRepository credentialRepository = mock(TenantApiCredentialRepository.class);
    private final TenantAccountRepository accountRepository = mock(TenantAccountRepository.class);
    private final TenantAuthService authService = mock(TenantAuthService.class);
    private final ApiSecretHasher secretHasher = mock(ApiSecretHasher.class);
    private final PublicAuthRateLimiter rateLimiter = mock(PublicAuthRateLimiter.class);
    private final InviteCodeService inviteCodeService = mock(InviteCodeService.class);
    private final TenantRegistrationService service = new TenantRegistrationService(
            tenantRepository, credentialRepository, accountRepository, authService, secretHasher,
            rateLimiter, inviteCodeService, false);
    private final TenantRegistrationService inviteRequiredService = new TenantRegistrationService(
            tenantRepository, credentialRepository, accountRepository, authService, secretHasher,
            rateLimiter, inviteCodeService, true);

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void registersActiveTenantWithConsoleScopedKeyAndAccountSession() {
        when(tenantRepository.findByTenantCode(anyString())).thenReturn(Optional.empty());
        when(credentialRepository.findByCredentialId(anyString())).thenReturn(Optional.empty());
        when(accountRepository.existsByUsername("ops")).thenReturn(false);
        when(secretHasher.hash(anyString())).thenAnswer(inv -> "hashed:" + inv.getArgument(0));
        when(authService.createAccountAndIssueSession(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new TenantAuthService.AccountIssue("ops", "sess_test", LocalDateTime.now().plusDays(7)));

        RegisteredTenant result = service.register(
                new RegisterTenantRequest("  测试租户  ", null, "ops@example.com", "Ops", "secret-1234", null),
                "1.2.3.4");

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        Tenant tenant = tenantCaptor.getValue();
        assertThat(tenant.getTenantName()).isEqualTo("测试租户");
        assertThat(tenant.getStatus()).isEqualTo("ACTIVE");
        assertThat(tenant.getPlanCode()).isEqualTo("FREE");
        assertThat(tenant.getTenantCode()).startsWith("t");

        ArgumentCaptor<TenantApiCredential> credentialCaptor = ArgumentCaptor.forClass(TenantApiCredential.class);
        verify(credentialRepository).save(credentialCaptor.capture());
        TenantApiCredential credential = credentialCaptor.getValue();
        assertThat(credential.getCredentialId()).startsWith("tk_");
        assertThat(credential.getSecretHash()).startsWith("hashed:");
        assertThat(credential.getScopes())
                .contains("userbot:read", "userbot:write", "conversation:read", "aiconfig:read", "aiconfig:write");
        assertThat(credential.getStatus()).isEqualTo("ACTIVE");

        assertThat(result.apiKey()).startsWith(credential.getCredentialId() + ".");
        assertThat(result.username()).isEqualTo("ops");
        assertThat(result.sessionToken()).isEqualTo("sess_test");
        verify(authService).createAccountAndIssueSession(anyString(), eq("ops"), eq("secret-1234"), eq("ops@example.com"));
        // 注册前后不残留请求线程上下文。
        assertThat(TenantContextHolder.getNullable()).isNull();
    }

    @Test
    void skipsAccountWhenUsernameNotProvided() {
        when(tenantRepository.findByTenantCode(anyString())).thenReturn(Optional.empty());
        when(credentialRepository.findByCredentialId(anyString())).thenReturn(Optional.empty());
        when(secretHasher.hash(anyString())).thenReturn("hash");

        RegisteredTenant result = service.register(
                new RegisterTenantRequest("老租户", null, null, null, null, null), "1.2.3.4");

        assertThat(result.username()).isNull();
        assertThat(result.sessionToken()).isNull();
        verify(authService, never()).createAccountAndIssueSession(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void usesProvidedTenantCodeWhenUnique() {
        when(tenantRepository.findByTenantCode("my-org")).thenReturn(Optional.empty());
        when(credentialRepository.findByCredentialId(anyString())).thenReturn(Optional.empty());
        when(secretHasher.hash(anyString())).thenReturn("hash");

        RegisteredTenant result = service.register(
                new RegisterTenantRequest("我的租户", "My-Org ", "a@b.com", null, null, null), "127.0.0.1");

        assertThat(result.tenantCode()).isEqualTo("my-org");
    }

    @Test
    void rejectsDuplicateTenantCodeWithConflict() {
        when(tenantRepository.findByTenantCode("taken")).thenReturn(Optional.of(new Tenant()));

        assertThatThrownBy(() -> service.register(
                new RegisterTenantRequest("租户", "taken", null, null, null, null), "127.0.0.1"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> {
                    TenantRegistrationException tre = (TenantRegistrationException) ex;
                    assertThat(tre.errorCode()).isEqualTo("CONFLICT");
                    assertThat(tre.status()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    void rejectsDuplicateUsernameWithConflict() {
        when(tenantRepository.findByTenantCode(anyString())).thenReturn(Optional.empty());
        when(accountRepository.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterTenantRequest("租户", null, null, "taken", "secret-1234", null), "127.0.0.1"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> {
                    TenantRegistrationException tre = (TenantRegistrationException) ex;
                    assertThat(tre.errorCode()).isEqualTo("CONFLICT");
                    assertThat(tre.status()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    void rejectsShortPassword() {
        assertThatThrownBy(() -> service.register(
                new RegisterTenantRequest("租户", null, null, "ops", "123", null), "127.0.0.1"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> assertThat(((TenantRegistrationException) ex).errorCode())
                        .isEqualTo("VALIDATION_ERROR"));
    }

    @Test
    void rejectsInvalidTenantCode() {
        assertThatThrownBy(() -> service.register(
                new RegisterTenantRequest("租户", "UPPER_123", null, null, null, null), "127.0.0.1"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> assertThat(((TenantRegistrationException) ex).errorCode())
                        .isEqualTo("VALIDATION_ERROR"));
    }

    @Test
    void rejectsInvalidEmail() {
        assertThatThrownBy(() -> service.register(
                new RegisterTenantRequest("租户", null, "not-an-email", null, null, null), "127.0.0.1"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> assertThat(((TenantRegistrationException) ex).errorCode())
                        .isEqualTo("VALIDATION_ERROR"));
    }

    @Test
    void rejectsBlankTenantName() {
        assertThatThrownBy(() -> service.register(
                new RegisterTenantRequest("   ", null, null, null, null, null), "127.0.0.1"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> assertThat(((TenantRegistrationException) ex).errorCode())
                        .isEqualTo("VALIDATION_ERROR"));
    }

    @Test
    void propagatesRateLimit() {
        doThrow(new TenantRegistrationException("RATE_LIMITED", "太频繁", HttpStatus.TOO_MANY_REQUESTS))
                .when(rateLimiter).checkRegistration(anyString(), any());

        assertThatThrownBy(() -> service.register(
                new RegisterTenantRequest("租户", null, null, null, null, null), "1.2.3.4"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> assertThat(((TenantRegistrationException) ex).status())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void consumesInviteCodeWhenRequired() {
        when(tenantRepository.findByTenantCode(anyString())).thenReturn(Optional.empty());
        when(credentialRepository.findByCredentialId(anyString())).thenReturn(Optional.empty());
        when(secretHasher.hash(anyString())).thenReturn("hash");
        when(inviteCodeService.consume("welcome-2026")).thenReturn(true);

        RegisteredTenant result = inviteRequiredService.register(
                new RegisterTenantRequest("租户", null, null, null, null, "welcome-2026"), "1.2.3.4");

        assertThat(result.tenantCode()).isNotBlank();
        verify(inviteCodeService).consume("welcome-2026");
    }

    @Test
    void rejectsInvalidInviteCodeWhenRequired() {
        when(inviteCodeService.consume(anyString())).thenReturn(false);

        assertThatThrownBy(() -> inviteRequiredService.register(
                new RegisterTenantRequest("租户", null, null, null, null, "BAD-CODE"), "1.2.3.4"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> {
                    TenantRegistrationException tre = (TenantRegistrationException) ex;
                    assertThat(tre.errorCode()).isEqualTo("INVALID_INVITE");
                    assertThat(tre.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void skipsInviteCheckWhenNotRequired() {
        when(tenantRepository.findByTenantCode(anyString())).thenReturn(Optional.empty());
        when(credentialRepository.findByCredentialId(anyString())).thenReturn(Optional.empty());
        when(secretHasher.hash(anyString())).thenReturn("hash");

        service.register(new RegisterTenantRequest("租户", null, null, null, null, null), "1.2.3.4");

        verify(inviteCodeService, never()).consume(anyString());
    }
}

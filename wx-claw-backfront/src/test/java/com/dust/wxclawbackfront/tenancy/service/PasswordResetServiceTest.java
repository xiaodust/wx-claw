package com.dust.wxclawbackfront.tenancy.service;

import com.dust.wxclawbackfront.bot.agent.tools.mail.MailHandler;
import com.dust.wxclawbackfront.bot.agent.tools.mail.MailSendResult;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.entity.TenantAccount;
import com.dust.wxclawbackfront.tenancy.entity.TenantPasswordReset;
import com.dust.wxclawbackfront.tenancy.repository.TenantAccountRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantPasswordResetRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantSessionRepository;
import com.dust.wxclawbackfront.tenancy.security.ApiSecretHasher;
import com.dust.wxclawbackfront.tenancy.security.PublicAuthRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class PasswordResetServiceTest {

    private final TenantAccountRepository accountRepository = mock(TenantAccountRepository.class);
    private final TenantPasswordResetRepository resetRepository = mock(TenantPasswordResetRepository.class);
    private final TenantSessionRepository sessionRepository = mock(TenantSessionRepository.class);
    private final ApiSecretHasher secretHasher = mock(ApiSecretHasher.class);
    private final PublicAuthRateLimiter rateLimiter = mock(PublicAuthRateLimiter.class);
    private final MailHandler mailHandler = mock(MailHandler.class);
    private final ObjectProvider<MailHandler> mailProvider = mock(ObjectProvider.class);
    private final PasswordResetService service = new PasswordResetService(
            accountRepository, resetRepository, sessionRepository, secretHasher, rateLimiter, mailProvider);

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void requestResetIssuesTokenAndSendsEmail() {
        TenantAccount account = account("tenant-1", "ops", "ops@example.com");
        when(accountRepository.findByUsername("ops")).thenReturn(Optional.of(account));
        when(mailProvider.getIfAvailable()).thenReturn(mailHandler);
        when(mailHandler.send(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(MailSendResult.success("ops@example.com", "WX-Claw 密码重置", "now"));
        when(resetRepository.save(any(TenantPasswordReset.class))).thenAnswer(inv -> inv.getArgument(0));

        service.requestReset("Ops", "1.2.3.4");

        ArgumentCaptor<TenantPasswordReset> captor = ArgumentCaptor.forClass(TenantPasswordReset.class);
        verify(resetRepository).save(captor.capture());
        TenantPasswordReset reset = captor.getValue();
        assertThat(reset.getTokenHash()).hasSize(64);
        assertThat(reset.getUsedAt()).isNull();
        assertThat(reset.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(reset.getTenantId()).isEqualTo("tenant-1");

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailHandler).send(eq("ops@example.com"), anyString(), linkCaptor.capture(), anyBoolean());
        assertThat(linkCaptor.getValue()).contains("/reset-password?token=pwreset_");
    }

    @Test
    void requestResetForUnknownAccountDoesNotSendEmail() {
        when(accountRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        service.requestReset("ghost", "1.2.3.4");

        verify(mailProvider, never()).getIfAvailable();
        verify(resetRepository, never()).save(any());
    }

    @Test
    void requestResetLogsLinkWhenMailUnavailable() {
        TenantAccount account = account("tenant-1", "ops", "ops@example.com");
        when(accountRepository.findByUsername("ops")).thenReturn(Optional.of(account));
        when(mailProvider.getIfAvailable()).thenReturn(null);
        when(resetRepository.save(any(TenantPasswordReset.class))).thenAnswer(inv -> inv.getArgument(0));

        service.requestReset("ops", "1.2.3.4");

        verify(resetRepository).save(any());
        // 不抛异常即可，邮件不可用时走日志兜底。
        assertThat(TenantContextHolder.getNullable()).isNull();
    }

    @Test
    void resetPasswordUpdatesHashRevokesSessionsAndMarksUsed() {
        TenantAccount account = account("tenant-1", "ops", "ops@example.com");
        TenantPasswordReset reset = new TenantPasswordReset();
        reset.setId(5L);
        reset.setAccountId(1L);
        reset.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(resetRepository.findByTokenHash(anyString())).thenReturn(Optional.of(reset));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(secretHasher.hash("new-secret-888")).thenReturn("new-hash");
        when(resetRepository.save(any(TenantPasswordReset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.save(any(TenantAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resetPassword("pwreset_some-token", "new-secret-888");

        assertThat(account.getPasswordHash()).isEqualTo("new-hash");
        assertThat(reset.getUsedAt()).isNotNull();
        verify(sessionRepository).deleteByAccountId(1L);
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        TenantPasswordReset reset = new TenantPasswordReset();
        reset.setId(5L);
        reset.setAccountId(1L);
        reset.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(resetRepository.findByTokenHash(anyString())).thenReturn(Optional.of(reset));

        assertThatThrownBy(() -> service.resetPassword("pwreset_some-token", "new-secret-888"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> {
                    TenantRegistrationException tre = (TenantRegistrationException) ex;
                    assertThat(tre.errorCode()).isEqualTo("INVALID_TOKEN");
                    assertThat(tre.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void resetPasswordRejectsUsedToken() {
        TenantPasswordReset reset = new TenantPasswordReset();
        reset.setId(5L);
        reset.setAccountId(1L);
        reset.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        reset.setUsedAt(LocalDateTime.now().minusMinutes(1));
        when(resetRepository.findByTokenHash(anyString())).thenReturn(Optional.of(reset));

        assertThatThrownBy(() -> service.resetPassword("pwreset_some-token", "new-secret-888"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> assertThat(((TenantRegistrationException) ex).errorCode())
                        .isEqualTo("INVALID_TOKEN"));
    }

    @Test
    void resetPasswordRejectsShortPassword() {
        assertThatThrownBy(() -> service.resetPassword("pwreset_some-token", "123"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> assertThat(((TenantRegistrationException) ex).errorCode())
                        .isEqualTo("VALIDATION_ERROR"));
    }

    @Test
    void requestResetPropagatesRateLimit() {
        doThrow(new TenantRegistrationException("RATE_LIMITED", "太频繁", HttpStatus.TOO_MANY_REQUESTS))
                .when(rateLimiter).checkPasswordReset(anyString(), anyString());

        assertThatThrownBy(() -> service.requestReset("ops", "1.2.3.4"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> assertThat(((TenantRegistrationException) ex).status())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    private TenantAccount account(String tenantId, String username, String email) {
        TenantAccount account = new TenantAccount();
        account.setId(1L);
        account.setTenantId(tenantId);
        account.setUsername(username);
        account.setContactEmail(email);
        account.setStatus("ACTIVE");
        return account;
    }
}

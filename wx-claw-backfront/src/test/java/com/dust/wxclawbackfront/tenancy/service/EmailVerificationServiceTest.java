package com.dust.wxclawbackfront.tenancy.service;

import com.dust.wxclawbackfront.bot.agent.tools.mail.MailHandler;
import com.dust.wxclawbackfront.bot.agent.tools.mail.MailSendResult;
import com.dust.wxclawbackfront.tenancy.entity.TenantEmailVerification;
import com.dust.wxclawbackfront.tenancy.repository.TenantEmailVerificationRepository;
import com.dust.wxclawbackfront.tenancy.security.PublicAuthRateLimiter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

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
class EmailVerificationServiceTest {

    private final TenantEmailVerificationRepository repository = mock(TenantEmailVerificationRepository.class);
    private final PublicAuthRateLimiter rateLimiter = mock(PublicAuthRateLimiter.class);
    private final MailHandler mailHandler = mock(MailHandler.class);
    private final ObjectProvider<MailHandler> mailProvider = mock(ObjectProvider.class);
    private final EmailVerificationService service = new EmailVerificationService(
            repository, rateLimiter, mailProvider);

    @Test
    void sendCodeStoresHashAndSendsEmail() {
        when(repository.findByEmailAndPurposeOrderByCreatedAtDesc("ops@example.com", "REGISTER"))
                .thenReturn(List.of());
        when(repository.save(any(TenantEmailVerification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mailProvider.getIfAvailable()).thenReturn(mailHandler);
        when(mailHandler.send(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(MailSendResult.success("ops@example.com", "x", "now"));

        service.sendCode("Ops@Example.COM", "register", "1.2.3.4");

        ArgumentCaptor<TenantEmailVerification> captor = ArgumentCaptor.forClass(TenantEmailVerification.class);
        verify(repository).save(captor.capture());
        TenantEmailVerification saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("ops@example.com");
        assertThat(saved.getPurpose()).isEqualTo("REGISTER");
        assertThat(saved.getCodeHash()).hasSize(64);
        assertThat(saved.getCodeHash()).doesNotContain("123456");
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailHandler).send(eq("ops@example.com"), anyString(), contentCaptor.capture(), anyBoolean());
        assertThat(contentCaptor.getValue()).contains("验证码");
    }

    @Test
    void sendCodeInvalidatesPreviousUnusedCodes() {
        TenantEmailVerification oldCode = new TenantEmailVerification();
        oldCode.setId(1L);
        oldCode.setUsedAt(null);
        when(repository.findByEmailAndPurposeOrderByCreatedAtDesc("a@b.com", "REGISTER"))
                .thenReturn(List.of(oldCode));
        when(repository.save(any(TenantEmailVerification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mailProvider.getIfAvailable()).thenReturn(mailHandler);
        when(mailHandler.send(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(MailSendResult.success("a@b.com", "x", "now"));

        service.sendCode("a@b.com", "REGISTER", "1.2.3.4");

        verify(repository).markUsed(eq(1L), any(LocalDateTime.class));
    }

    @Test
    void verifyCodeConsumesLatestUnusedCode() {
        TenantEmailVerification verification = new TenantEmailVerification();
        verification.setId(9L);
        verification.setEmail("a@b.com");
        verification.setPurpose("REGISTER");
        verification.setCodeHash(sha256("123456"));
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(repository.findByEmailAndPurposeOrderByCreatedAtDesc("a@b.com", "REGISTER"))
                .thenReturn(List.of(verification));
        when(repository.markUsed(eq(9L), any(LocalDateTime.class))).thenReturn(1);

        assertThat(service.verifyCode("a@b.com", "REGISTER", "123456")).isTrue();
        verify(repository).markUsed(eq(9L), any(LocalDateTime.class));
    }

    @Test
    void verifyCodeRejectsWrongExpiredOrUsedCode() {
        TenantEmailVerification verification = new TenantEmailVerification();
        verification.setId(9L);
        verification.setEmail("a@b.com");
        verification.setPurpose("REGISTER");
        verification.setCodeHash(sha256("123456"));
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(repository.findByEmailAndPurposeOrderByCreatedAtDesc("a@b.com", "REGISTER"))
                .thenReturn(List.of(verification));

        assertThat(service.verifyCode("a@b.com", "REGISTER", "999999")).isFalse();

        verification.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertThat(service.verifyCode("a@b.com", "REGISTER", "123456")).isFalse();

        verification.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        verification.setUsedAt(LocalDateTime.now());
        assertThat(service.verifyCode("a@b.com", "REGISTER", "123456")).isFalse();
        verify(repository, never()).markUsed(any(), any());
    }

    @Test
    void sendCodePropagatesRateLimit() {
        doThrow(new TenantRegistrationException("RATE_LIMITED", "太频繁", HttpStatus.TOO_MANY_REQUESTS))
                .when(rateLimiter).checkEmailCode(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.sendCode("a@b.com", "REGISTER", "1.2.3.4"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> assertThat(((TenantRegistrationException) ex).status())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    private String sha256(String value) {
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}

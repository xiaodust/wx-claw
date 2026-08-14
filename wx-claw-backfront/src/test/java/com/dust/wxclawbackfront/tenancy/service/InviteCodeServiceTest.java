package com.dust.wxclawbackfront.tenancy.service;

import com.dust.wxclawbackfront.admin.api.dto.AdminDtos.InviteCode;
import com.dust.wxclawbackfront.tenancy.entity.TenantInviteCode;
import com.dust.wxclawbackfront.tenancy.repository.TenantInviteCodeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InviteCodeServiceTest {

    private final TenantInviteCodeRepository repository = mock(TenantInviteCodeRepository.class);
    private final InviteCodeService service = new InviteCodeService(repository);

    @Test
    void generatesUniqueUppercaseCodes() {
        when(repository.findByCode(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(TenantInviteCode.class))).thenAnswer(inv -> inv.getArgument(0));

        List<String> codes = service.generate(5, 1, LocalDateTime.now().plusDays(7), "测试", "admin");

        assertThat(codes).hasSize(5);
        assertThat(codes).allMatch(code -> code.matches("^[A-Z2-9]{10}$"));
        assertThat(codes.stream().distinct()).hasSize(5);
        ArgumentCaptor<TenantInviteCode> captor = ArgumentCaptor.forClass(TenantInviteCode.class);
        verify(repository, org.mockito.Mockito.times(5)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(c -> c.getQuota() == 1 && "ACTIVE".equals(c.getStatus()));
    }

    @Test
    void rejectsInvalidCount() {
        assertThatThrownBy(() -> service.generate(0, null, null, null, "admin"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> assertThat(((TenantRegistrationException) ex).status())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.generate(51, null, null, null, "admin"))
                .isInstanceOf(TenantRegistrationException.class);
    }

    @Test
    void disablesExistingCode() {
        TenantInviteCode code = new TenantInviteCode();
        code.setCode("ABC1234567");
        code.setStatus("ACTIVE");
        when(repository.findByCode("ABC1234567")).thenReturn(Optional.of(code));
        when(repository.save(any(TenantInviteCode.class))).thenAnswer(inv -> inv.getArgument(0));

        service.disable("ABC1234567");

        assertThat(code.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void disableMissingCodeReturnsNotFound() {
        when(repository.findByCode(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disable("NOPE"))
                .isInstanceOf(TenantRegistrationException.class)
                .satisfies(ex -> assertThat(((TenantRegistrationException) ex).status())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void consumeDelegatesToAtomicUpdate() {
        when(repository.consume(eq("WELCOME"), any(LocalDateTime.class))).thenReturn(1);

        assertThat(service.consume("welcome")).isTrue();
        verify(repository).consume(eq("WELCOME"), any(LocalDateTime.class));

        when(repository.consume(eq("USED"), any(LocalDateTime.class))).thenReturn(0);
        assertThat(service.consume("used")).isFalse();
    }

    @Test
    void consumeRejectsBlankCode() {
        assertThat(service.consume(null)).isFalse();
        assertThat(service.consume("  ")).isFalse();
    }

    @Test
    void listsSortedByCreatedAtDesc() {
        TenantInviteCode older = new TenantInviteCode();
        older.setId(1L);
        older.setCode("OLD1234567");
        older.setCreatedAt(LocalDateTime.now().minusDays(1));
        TenantInviteCode newer = new TenantInviteCode();
        newer.setId(2L);
        newer.setCode("NEW1234567");
        newer.setCreatedAt(LocalDateTime.now());
        when(repository.findAll()).thenReturn(List.of(older, newer));

        List<InviteCode> result = service.list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).code()).isEqualTo("NEW1234567");
    }
}

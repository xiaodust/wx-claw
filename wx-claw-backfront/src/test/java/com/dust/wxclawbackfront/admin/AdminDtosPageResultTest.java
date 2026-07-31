package com.dust.wxclawbackfront.admin;

import com.dust.wxclawbackfront.admin.api.dto.AdminDtos;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminDtosPageResultTest {
    @Test
    void preservesStablePageFields() {
        PageImpl<String> page = new PageImpl<>(List.of("item"), PageRequest.of(2, 10), 35);

        AdminDtos.PageResult<String> result = AdminDtos.PageResult.from(page);

        assertEquals(List.of("item"), result.content());
        assertEquals(35, result.totalElements());
        assertEquals(4, result.totalPages());
        assertEquals(2, result.number());
        assertEquals(10, result.size());
    }
}

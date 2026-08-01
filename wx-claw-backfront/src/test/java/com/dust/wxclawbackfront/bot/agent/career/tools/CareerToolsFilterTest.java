package com.dust.wxclawbackfront.bot.agent.career.tools;

import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.dto.JobHelperDtos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CareerToolsFilterTest {
    @Test
    void normalizesCompoundInternshipKeywords() throws Exception {
        CareerTools tools = new CareerTools(null, null, null, null, null);
        Method filters = CareerTools.class.getDeclaredMethod("filters", List.class, List.class, List.class,
                List.class, Integer.class, Integer.class, Integer.class);
        filters.setAccessible(true);
        JobHelperDtos.JobFilters result = (JobHelperDtos.JobFilters) filters.invoke(tools,
                List.of(), List.of("Java实习"), List.of(), List.of(), null, null, null);

        assertThat(result.includeKeywords()).containsExactly("Java");
        assertThat(result.employmentTypes()).containsExactly("INTERNSHIP");
    }
}

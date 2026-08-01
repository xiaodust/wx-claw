package com.dust.wxclawbackfront.bot.agent.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanValidatorTest {
    private final PlanValidator validator = new PlanValidator(new ObjectMapper());

    @Test
    void rejectsForwardDependencyAndDuplicateSteps() {
        assertThat(validator.validate("{\"steps\":[{\"step\":1,\"tool\":\"chat\",\"params\":{},\"depends_on\":2}]} ").isValid()).isFalse();
        assertThat(validator.validate("{\"steps\":[{\"step\":1,\"tool\":\"chat\"},{\"step\":1,\"tool\":\"voice_synthesize\"}]} ").isValid()).isFalse();
    }

    @Test
    void acceptsOrderedDependency() {
        String json = "{\"steps\":[{\"step\":1,\"tool\":\"chat\",\"params\":{}},"
                + "{\"step\":2,\"tool\":\"voice_synthesize\",\"params\":{},\"depends_on\":1}]}";
        assertThat(validator.validate(json).isValid()).isTrue();
    }
}

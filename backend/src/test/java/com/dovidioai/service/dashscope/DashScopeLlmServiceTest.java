package com.dovidioai.service.dashscope;

import com.dovidioai.config.DashScopeProperties;
import com.dovidioai.exception.BusinessException;
import com.dovidioai.support.ExponentialBackoffRetry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DashScopeLlmServiceTest {

    private final DashScopeLlmService service = new DashScopeLlmService(
            new DashScopeProperties(),
            new ObjectMapper(),
            mock(ExponentialBackoffRetry.class)
    );

    @Test
    void shouldParseIncrementalTextChunk() {
        String data = """
                {"output":{"choices":[{"message":{"content":"你好"}}]}}
                """;

        assertThat(service.parseStreamData(data)).isEqualTo("你好");
    }

    @Test
    void shouldParseArrayContentChunk() {
        String data = """
                {"output":{"choices":[{"message":{"content":[{"text":"世界"}]}}]}}
                """;

        assertThat(service.parseStreamData(data)).isEqualTo("世界");
    }

    @Test
    void shouldIgnoreDoneMarker() {
        assertThat(service.parseStreamData("[DONE]")).isEmpty();
    }

    @Test
    void shouldExposeDashScopeError() {
        String data = """
                {"code":"InvalidApiKey","message":"invalid key"}
                """;

        assertThatThrownBy(() -> service.parseStreamData(data))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid key");
    }
}

package com.intenthub.infrastructure.llm;

import com.intenthub.domain.config.LlmPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TongyiLlmAdapterTest {
    @Test
    void inactiveGovernanceReturnsEmptyWithoutCallingRemote() {
        TongyiLlmAdapter adapter = new TongyiLlmAdapter(
                RestClient.builder(),
                new LlmGovernanceProperties(false, "", 3000, 0, 0.0, 0.70)
        );

        assertThat(adapter.recognize("text", "scene", policy(10.0))).isEmpty();
    }

    @Test
    void zeroPolicyBudgetReturnsEmptyWithoutCallingRemote() {
        TongyiLlmAdapter adapter = new TongyiLlmAdapter(
                RestClient.builder(),
                new LlmGovernanceProperties(true, "https://llm.example.test", 3000, 0, 10.0, 0.70)
        );

        assertThat(adapter.recognize("text", "scene", policy(0.0))).isEmpty();
    }

    @Test
    void returnsCandidateWhenGovernanceAndPolicyAllowSmallTrafficFallback() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://llm.example.test/recognize"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"intentCode":"ORDER_QUERY","confidence":0.78,"slots":{},"explanation":"llm hit"}
                        """, MediaType.APPLICATION_JSON));
        TongyiLlmAdapter adapter = new TongyiLlmAdapter(
                builder,
                new LlmGovernanceProperties(true, "https://llm.example.test", 3000, 0, 10.0, 0.70)
        );

        assertThat(adapter.recognize("text", "scene", policy(10.0)))
                .hasValueSatisfying(candidate -> {
                    assertThat(candidate.intentCode()).isEqualTo("ORDER_QUERY");
                    assertThat(candidate.confidence()).isEqualTo(0.78);
                    assertThat(candidate.explanation()).isEqualTo("llm hit");
                });
        server.verify();
    }

    @Test
    void retriesWithinGovernanceLimitThenFailsClosedToPolicyLayer() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://llm.example.test/recognize"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        server.expect(once(), requestTo("https://llm.example.test/recognize"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        TongyiLlmAdapter adapter = new TongyiLlmAdapter(
                builder,
                new LlmGovernanceProperties(true, "https://llm.example.test", 3000, 1, 10.0, 0.70)
        );

        assertThatThrownBy(() -> adapter.recognize("text", "scene", policy(10.0)))
                .isInstanceOf(RuntimeException.class);
        server.verify();
    }

    private LlmPolicy policy(double dailyBudget) {
        return new LlmPolicy(true, "spring-ai-alibaba", "qwen-plus", 3000, 1, dailyBudget, "REJECTED");
    }
}

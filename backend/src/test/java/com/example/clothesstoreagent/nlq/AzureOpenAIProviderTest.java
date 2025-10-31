package com.example.clothesstoreagent.nlq;

import com.example.clothesstoreagent.config.AppProps;
import com.example.clothesstoreagent.service.SchemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AzureOpenAIProviderTest {

    private AzureOpenAIProvider provider;
    private Method parseMethod;

    @BeforeEach
    void setUp() throws Exception {
        AppProps props = new AppProps();
        props.setAzureOpenaiEndpoint("https://example.com");
        props.setAzureOpenaiApiKey("key");
        props.setAzureOpenaiDeployment("deployment");

        SchemaService schema = mock(SchemaService.class);
        when(schema.getSchema()).thenReturn(Map.of());

        provider = new AzureOpenAIProvider(props, schema);
        parseMethod = AzureOpenAIProvider.class.getDeclaredMethod("parseDecision", Map.class);
        parseMethod.setAccessible(true);
    }

    @Test
    void parseDecisionExtractsFieldsAndIgnoresUnknownKeys() throws Exception {
    Map<String, Object> payload = Map.of(
        "decision", "execute",
        "question", " ",
        "missing", List.of("date_range", " "),
                "sql", "SELECT * FROM dbo.orders",
                "params", Map.of("limit", 5),
                "unexpected", "ignored"
        );

        NlqProvider.Decision decision = invokeParse(payload);

        assertThat(decision.decision).isEqualTo(NlqProvider.DecisionType.EXECUTE);
        assertThat(decision.sql).isEqualTo("SELECT * FROM dbo.orders");
        assertThat(decision.params).containsEntry("limit", 5);
        assertThat(decision.missing).containsExactly("date_range");
        assertThat(decision.question).isNull();
    }

    @Test
    void parseDecisionRequiresQuestionForClarify() {
        Map<String, Object> payload = Map.of(
                "decision", "clarify",
                "missing", List.of("date_range")
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> invokeParse(payload));
        assertThat(ex.getMessage()).contains("clarify");
    }

    private NlqProvider.Decision invokeParse(Map<String, Object> payload) throws Exception {
        try {
            return (NlqProvider.Decision) parseMethod.invoke(provider, payload);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception e) {
                throw e;
            }
            throw ex;
        }
    }
}

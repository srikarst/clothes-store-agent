package com.example.clothesstoreagent.api;

import com.example.clothesstoreagent.nlq.NlqProvider;
import com.example.clothesstoreagent.service.QueryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NlqControllerTest {

    @Test
    void clarifyDecisionDoesNotExecuteSql() {
        QueryService query = mock(QueryService.class);
        NlqProvider provider = prompt -> NlqProvider.Decision.clarify(
                "intent_clarify",
                "Could you share the date range?",
                List.of("date_range")
        );

        NlqController controller = new NlqController(provider, query);
        NlqController.NlqRequest req = new NlqController.NlqRequest();
        req.prompt = "sales for hoodies";

    ResponseEntity<Map<String, Object>> resp = controller.handle(req);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Object> body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body).containsEntry("decision", "clarify");
    assertThat(body).containsEntry("clarify", true);
    assertThat(body).containsEntry("question", "Could you share the date range?");
    assertThat(body).containsEntry("ran", false);
    assertThat(body).containsEntry("missing", List.of("date_range"));
        verifyNoInteractions(query);
    }

    @Test
    void executeDecisionRunsQueryWhenAllowed() {
        QueryService query = mock(QueryService.class);
        when(query.execute(eq("SELECT 1"), eq(Map.of("limit", 5)), any(), any()))
                .thenReturn(Map.of("rowCount", 0));

        NlqProvider provider = prompt -> NlqProvider.Decision.execute(
                "intent_execute",
                "SELECT 1",
                Map.of("limit", 5)
        );

        NlqController controller = new NlqController(provider, query);
        NlqController.NlqRequest req = new NlqController.NlqRequest();
        req.prompt = "top 5 products by revenue last month";
        req.execute = true;

    ResponseEntity<Map<String, Object>> resp = controller.handle(req);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Object> body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body).containsEntry("decision", "execute");
    assertThat(body).containsEntry("ran", true);
    assertThat(body).containsEntry("sql", "SELECT 1");
    assertThat(body).containsEntry("params", Map.of("limit", 5));
    assertThat(body).containsEntry("result", Map.of("rowCount", 0));
        verify(query).execute(eq("SELECT 1"), eq(Map.of("limit", 5)), any(), any());
    }

    @Test
    void rejectDecisionReturnsBadRequest() {
        QueryService query = mock(QueryService.class);
        NlqProvider provider = prompt -> NlqProvider.Decision.reject(
                "intent_reject",
                "I can't perform destructive operations."
        );

        NlqController controller = new NlqController(provider, query);
        NlqController.NlqRequest req = new NlqController.NlqRequest();
        req.prompt = "drop the customers table";

    ResponseEntity<Map<String, Object>> resp = controller.handle(req);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    Map<String, Object> body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body).containsEntry("decision", "reject");
    assertThat(body).containsEntry("error", "NLQ_REJECTED");
    assertThat(body).containsEntry("message", "I can't perform destructive operations.");
    assertThat(body).containsEntry("ran", false);
        verifyNoInteractions(query);
    }
}

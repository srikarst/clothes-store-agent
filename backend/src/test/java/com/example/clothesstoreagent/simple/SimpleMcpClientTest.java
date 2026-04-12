package com.example.clothesstoreagent.simple;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleMcpClientTest {

    private final SimpleMcpClient mcpClient = new SimpleMcpClient(new ObjectMapper());

    @Test
    void postPurchaseSkill_extractsFacts_andReturnsEligibility() {
        SimpleMcpClient.ExecutionResult result = mcpClient.executeSkill(
                "post_purchase_support",
                "I received it 12 days ago, it is unused and has tags. Can I return it?"
        );

        assertEquals("policy-mcp-server/check_return_eligibility", result.route());
        assertEquals(1, result.localTools().size());
        assertEquals(1, result.mcpCalls().size());
        assertTrue(result.localTools().get(0).output().contains("daysSinceDelivery=12"));
        assertTrue(result.mcpCalls().get(0).ok());
        assertTrue(result.mcpCalls().get(0).output().contains("eligible=true"));
    }

    @Test
    void postPurchaseSkill_returnsValidationError_whenDaysAreMissing() {
        SimpleMcpClient.ExecutionResult result = mcpClient.executeSkill(
                "post_purchase_support",
                "Can I return this jacket? It is unused and has tags."
        );

        assertEquals("policy-mcp-server/check_return_eligibility", result.route());
        assertEquals(1, result.mcpCalls().size());
        SimpleMcpClient.McpResult mcpResult = result.mcpCalls().get(0);
        assertFalse(mcpResult.ok());
        assertEquals("VALIDATION_ERROR", mcpResult.errorCode());
        assertTrue(mcpResult.output().contains("days since delivery"));
    }

    @Test
    void orderFulfillmentSkill_extractsFacts_andEstimatesEta() {
        SimpleMcpClient.ExecutionResult result = mcpClient.executeSkill(
                "order_fulfillment_support",
                "How fast can this ship express internationally?"
        );

        assertEquals("fulfillment-mcp-server/estimate_delivery_eta", result.route());
        assertEquals(1, result.localTools().size());
        assertEquals(1, result.mcpCalls().size());
        assertTrue(result.localTools().get(0).output().contains("destination=international"));
        assertTrue(result.mcpCalls().get(0).ok());
        assertTrue(result.mcpCalls().get(0).output().contains("eta=3-6"));
    }

    @Test
    void orderFulfillmentSkill_recommendsExpress_whenUrgencyIsHigh() {
        SimpleMcpClient.ExecutionResult result = mcpClient.executeSkill(
                "order_fulfillment_support",
                "Which shipping option do you recommend if I need it in 1 day?"
        );

        assertEquals("fulfillment-mcp-server/recommend_shipping_option", result.route());
        assertEquals(1, result.mcpCalls().size());
        SimpleMcpClient.McpResult mcpResult = result.mcpCalls().get(0);
        assertTrue(mcpResult.ok());
        assertTrue(mcpResult.output().contains("recommendation=express"));
    }
}


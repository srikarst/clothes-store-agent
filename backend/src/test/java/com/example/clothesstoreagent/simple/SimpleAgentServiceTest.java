package com.example.clothesstoreagent.simple;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleAgentServiceTest {

    @Test
    void chat_routesToPostPurchaseSkill() {
        SimpleAgentService service = newService();

        SimpleAgentService.ChatResponse response =
                service.chat("Can I return this hoodie after 10 days with tags?");

        assertEquals("post_purchase_support", response.skill());
        assertEquals("policy-mcp-server/check_return_eligibility", response.route());
        assertNotNull(response.localTools());
        assertNotNull(response.mcpCalls());
        assertFalse(response.mcpCalls().isEmpty());
    }

    @Test
    void chat_routesToOrderFulfillmentSkill() {
        SimpleAgentService service = newService();

        SimpleAgentService.ChatResponse response =
                service.chat("How fast can this ship internationally with express?");

        assertEquals("order_fulfillment_support", response.skill());
        assertEquals("fulfillment-mcp-server/estimate_delivery_eta", response.route());
        assertFalse(response.mcpCalls().isEmpty());
        assertTrue(response.mcpCalls().get(0).ok());
    }

    @Test
    void chat_fallsBackToDeterministicMessage_whenMcpValidationFails() {
        SimpleAgentService service = newService();

        SimpleAgentService.ChatResponse response =
                service.chat("Can I return this order?");

        assertEquals("post_purchase_support", response.skill());
        assertFalse(response.mcpCalls().isEmpty());
        assertFalse(response.mcpCalls().get(0).ok());
        assertTrue(response.assistantMessage().contains("failed"));
    }

    private static SimpleAgentService newService() {
        SimpleRagStore ragStore = new SimpleRagStore();
        SimpleMcpClient mcpClient = new SimpleMcpClient(new ObjectMapper());
        SimpleModelClient modelClient = new SimpleModelClient(
                new ObjectMapper(),
                "",
                "",
                "2024-02-15-preview",
                "",
                0.2
        );
        return new SimpleAgentService(ragStore, mcpClient, modelClient);
    }
}


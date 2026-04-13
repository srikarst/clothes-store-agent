package com.example.clothesstoreagent.simple;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalsHarnessGradersDemoTest {

    @Test
    void qualityGate_runsEvalsAgainstRealSimpleAgentService() {
        AssistantUnderTest assistant = new RealSimpleAgentAssistant(newService());

        List<EvalCase> evals = List.of(
                new EvalCase(
                        "return-intent",
                        "Can I return this hoodie after 10 days with tags?",
                        "post_purchase_support",
                        "policy-mcp-server/check_return_eligibility",
                        true
                ),
                new EvalCase(
                        "shipping-intent",
                        "How fast can this ship internationally with express?",
                        "order_fulfillment_support",
                        "fulfillment-mcp-server/estimate_delivery_eta",
                        true
                ),
                new EvalCase(
                        "general-intent",
                        "Hi there, what can you help with?",
                        "general_help",
                        "none",
                        false
                )
        );

        List<Grader> graders = List.of(
                new SkillMatchGrader(),
                new RouteMatchGrader(),
                new McpUsageGrader()
        );

        EvalHarness harness = new EvalHarness(assistant, graders);
        RunReport report = harness.run(evals);

        assertEquals(3, report.caseReports().size());
        assertEquals(1.0, report.overallScore());
        assertTrue(report.caseReports().stream().allMatch(CaseReport::passed));
    }

    interface AssistantUnderTest {
        AssistantOutput answer(String userMessage);
    }

    record AssistantOutput(
            String skill,
            String route,
            String response,
            int mcpCallCount
    ) {
    }

    record EvalCase(
            String id,
            String userInput,
            String expectedSkill,
            String expectedRoute,
            boolean shouldUseMcp
    ) {
    }

    record Grade(String graderName, double score, String reason) {
    }

    interface Grader {
        Grade grade(EvalCase evalCase, AssistantOutput output);
    }

    record CaseReport(
            EvalCase evalCase,
            AssistantOutput output,
            List<Grade> grades,
            double averageScore
    ) {
        boolean passed() {
            return averageScore >= 1.0;
        }
    }

    record RunReport(List<CaseReport> caseReports, double overallScore) {
    }

    static class EvalHarness {
        private final AssistantUnderTest assistant;
        private final List<Grader> graders;

        EvalHarness(AssistantUnderTest assistant, List<Grader> graders) {
            this.assistant = assistant;
            this.graders = graders;
        }

        RunReport run(List<EvalCase> evals) {
            List<CaseReport> caseReports = new ArrayList<>();

            for (EvalCase evalCase : evals) {
                AssistantOutput output = assistant.answer(evalCase.userInput());
                List<Grade> grades = new ArrayList<>();

                for (Grader grader : graders) {
                    grades.add(grader.grade(evalCase, output));
                }

                double avgScore = grades.stream()
                        .mapToDouble(Grade::score)
                        .average()
                        .orElse(0.0);

                caseReports.add(new CaseReport(evalCase, output, grades, avgScore));
            }

            double overallScore = caseReports.stream()
                    .mapToDouble(CaseReport::averageScore)
                    .average()
                    .orElse(0.0);

            return new RunReport(caseReports, overallScore);
        }
    }

    static class SkillMatchGrader implements Grader {
        @Override
        public Grade grade(EvalCase evalCase, AssistantOutput output) {
            boolean isMatch = evalCase.expectedSkill().equals(output.skill());
            return new Grade(
                    "skill_match",
                    isMatch ? 1.0 : 0.0,
                    isMatch ? "skill matched expected value" : "skill did not match expected value"
            );
        }
    }

    static class RouteMatchGrader implements Grader {
        @Override
        public Grade grade(EvalCase evalCase, AssistantOutput output) {
            boolean isMatch = evalCase.expectedRoute().equals(output.route());
            return new Grade(
                    "route_match",
                    isMatch ? 1.0 : 0.0,
                    isMatch ? "route matched expected value" : "route did not match expected value"
            );
        }
    }

    static class McpUsageGrader implements Grader {
        @Override
        public Grade grade(EvalCase evalCase, AssistantOutput output) {
            boolean hasMcpCalls = output.mcpCallCount() > 0;
            boolean isMatch = hasMcpCalls == evalCase.shouldUseMcp();
            return new Grade(
                    "mcp_usage",
                    isMatch ? 1.0 : 0.0,
                    isMatch ? "mcp usage matched expectation" : "mcp usage did not match expectation"
            );
        }
    }

    static class RealSimpleAgentAssistant implements AssistantUnderTest {
        private final SimpleAgentService service;

        RealSimpleAgentAssistant(SimpleAgentService service) {
            this.service = service;
        }

        @Override
        public AssistantOutput answer(String userMessage) {
            SimpleAgentService.ChatResponse response = service.chat(userMessage);
            int mcpCallCount = response.mcpCalls() == null ? 0 : response.mcpCalls().size();
            return new AssistantOutput(
                    response.skill(),
                    response.route(),
                    response.assistantMessage(),
                    mcpCallCount
            );
        }
    }

    private static SimpleAgentService newService() {
        ObjectMapper objectMapper = new ObjectMapper();
        AzureOpenAiEmbeddingClient embeddingClient = new AzureOpenAiEmbeddingClient(
                objectMapper,
                "",
                "",
                "2024-02-15-preview",
                "",
                5000
        );
        QdrantRagStore ragStore = new QdrantRagStore(
                objectMapper,
                embeddingClient,
                "",
                "",
                "",
                5000,
                3,
                0.25,
                "text",
                "source",
                true,
                true,
                12,
                12,
                8,
                0.45,
                0.25,
                0.30,
                "rag-docs",
                "md,txt",
                400,
                60,
                60000,
                1.5,
                0.75
        );
        SimpleMcpClient mcpClient = new SimpleMcpClient(objectMapper);
        SimpleModelClient modelClient = new SimpleModelClient(
                objectMapper,
                "",
                "",
                "2024-02-15-preview",
                "",
                0.2
        );
        return new SimpleAgentService(ragStore, mcpClient, modelClient);
    }
}

package com.example.clothesstoreagent.simple;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SimpleMcpClient {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    public McpResult runMathTool(String userMessage) {
        List<Double> numbers = extractNumbers(userMessage);
        if (numbers.size() < 2) {
            return new McpResult(
                    "demo-mcp-server",
                    "math_tool",
                    userMessage,
                    "Need at least two numbers for a math operation.",
                    false
            );
        }

        boolean multiply = containsAny(userMessage, "multiply", "times", "product");
        double result;
        String toolName;
        if (multiply) {
            result = numbers.get(0) * numbers.get(1);
            toolName = "multiply";
        } else {
            result = numbers.get(0) + numbers.get(1);
            toolName = "add";
        }

        return new McpResult(
                "demo-mcp-server",
                toolName,
                userMessage,
                formatNumber(result),
                true
        );
    }

    private List<Double> extractNumbers(String input) {
        List<Double> values = new ArrayList<>();
        if (input == null) {
            return values;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(input);
        while (matcher.find()) {
            values.add(Double.parseDouble(matcher.group()));
        }
        return values;
    }

    private boolean containsAny(String text, String... keywords) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String formatNumber(double value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    public record McpResult(
            String serverId,
            String toolName,
            String input,
            String output,
            boolean ok
    ) {
    }
}

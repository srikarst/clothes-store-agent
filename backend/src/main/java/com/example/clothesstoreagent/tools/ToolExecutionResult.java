package com.example.clothesstoreagent.tools;

import java.util.Map;

public record ToolExecutionResult(
        boolean ok,
        String content,
        Map<String, Object> data,
        String error
) {
    public static ToolExecutionResult ok(String content, Map<String, Object> data) {
        return new ToolExecutionResult(true, content != null ? content : "", data != null ? data : Map.of(), null);
    }

    public static ToolExecutionResult fail(String error, Map<String, Object> data) {
        String msg = error != null ? error : "Tool failed";
        return new ToolExecutionResult(false, msg, data != null ? data : Map.of(), msg);
    }
}





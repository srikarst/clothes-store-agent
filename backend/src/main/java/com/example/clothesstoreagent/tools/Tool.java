package com.example.clothesstoreagent.tools;

import java.util.Map;

public interface Tool {
    ToolSpec spec();

    ToolExecutionResult execute(Map<String, Object> args, ToolContext ctx);
}





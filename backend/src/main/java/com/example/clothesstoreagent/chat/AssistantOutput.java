package com.example.clothesstoreagent.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AssistantOutput {
    public String type; // "final" | "tool_call"
    public String assistantMessage;
    public ToolCall tool;
}





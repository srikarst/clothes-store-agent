package com.example.clothesstoreagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProps {
    private boolean readOnly = true;
    private List<String> disallowSqlKeywords = new ArrayList<>();
    private int defaultMaxRows = 200;
    private int defaultQueryTimeoutSeconds = 20;
    private List<String> allowTables = new ArrayList<>();

    private int schemaSamplesPerColumn = 5;

    private String nlqProvider = "rule";
    private String nlqMode = "judge-generator"; // "single-call" or "judge-generator"

    private String azureOpenaiEndpoint;
    private String azureOpenaiApiKey;
    private String azureOpenaiDeployment;
    private String azureOpenaiApiVersion = "2024-02-15-preview";

    private String awsRegion;
    private String bedrockModelId;

    private boolean historyEnabled = true;
    private int historyMaxTurns = 6;
    private int historyTtlMinutes = 1440;

    // Phase 0 chat shell (general assistant)
    private String chatSystemPrompt = "You are a helpful assistant for a clothes store application. Be concise.";
    private double chatDefaultTemperature = 0.2;

    // Phase 1 tools loop
    private int chatMaxSteps = 4;
    private int toolResultMaxChars = 3000;

    public boolean isReadOnly() { return readOnly; }
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }

    public List<String> getDisallowSqlKeywords() { return disallowSqlKeywords; }
    public void setDisallowSqlKeywords(List<String> disallowSqlKeywords) { this.disallowSqlKeywords = disallowSqlKeywords; }

    public int getDefaultMaxRows() { return defaultMaxRows; }
    public void setDefaultMaxRows(int defaultMaxRows) { this.defaultMaxRows = defaultMaxRows; }

    public int getDefaultQueryTimeoutSeconds() { return defaultQueryTimeoutSeconds; }
    public void setDefaultQueryTimeoutSeconds(int defaultQueryTimeoutSeconds) { this.defaultQueryTimeoutSeconds = defaultQueryTimeoutSeconds; }

    public List<String> getAllowTables() { return allowTables; }
    public void setAllowTables(List<String> allowTables) { this.allowTables = allowTables; }

    public int getSchemaSamplesPerColumn() { return schemaSamplesPerColumn; }
    public void setSchemaSamplesPerColumn(int schemaSamplesPerColumn) { this.schemaSamplesPerColumn = schemaSamplesPerColumn; }

    public String getNlqProvider() { return nlqProvider; }
    public void setNlqProvider(String nlqProvider) { this.nlqProvider = nlqProvider; }

    public String getNlqMode() { return nlqMode; }
    public void setNlqMode(String nlqMode) { this.nlqMode = nlqMode; }

    public String getAzureOpenaiEndpoint() { return azureOpenaiEndpoint; }
    public void setAzureOpenaiEndpoint(String v) { this.azureOpenaiEndpoint = v; }

    public String getAzureOpenaiApiKey() { return azureOpenaiApiKey; }
    public void setAzureOpenaiApiKey(String v) { this.azureOpenaiApiKey = v; }

    public String getAzureOpenaiDeployment() { return azureOpenaiDeployment; }
    public void setAzureOpenaiDeployment(String v) { this.azureOpenaiDeployment = v; }

    public String getAzureOpenaiApiVersion() { return azureOpenaiApiVersion; }
    public void setAzureOpenaiApiVersion(String v) { this.azureOpenaiApiVersion = v; }

    public String getAwsRegion() { return awsRegion; }
    public void setAwsRegion(String awsRegion) { this.awsRegion = awsRegion; }

    public String getBedrockModelId() { return bedrockModelId; }
    public void setBedrockModelId(String bedrockModelId) { this.bedrockModelId = bedrockModelId; }

    public boolean isHistoryEnabled() { return historyEnabled; }
    public void setHistoryEnabled(boolean historyEnabled) { this.historyEnabled = historyEnabled; }

    public int getHistoryMaxTurns() { return historyMaxTurns; }
    public void setHistoryMaxTurns(int historyMaxTurns) { this.historyMaxTurns = historyMaxTurns; }

    public int getHistoryTtlMinutes() { return historyTtlMinutes; }
    public void setHistoryTtlMinutes(int historyTtlMinutes) { this.historyTtlMinutes = historyTtlMinutes; }

    public String getChatSystemPrompt() { return chatSystemPrompt; }
    public void setChatSystemPrompt(String chatSystemPrompt) { this.chatSystemPrompt = chatSystemPrompt; }

    public double getChatDefaultTemperature() { return chatDefaultTemperature; }
    public void setChatDefaultTemperature(double chatDefaultTemperature) { this.chatDefaultTemperature = chatDefaultTemperature; }

    public int getChatMaxSteps() { return chatMaxSteps; }
    public void setChatMaxSteps(int chatMaxSteps) { this.chatMaxSteps = chatMaxSteps; }

    public int getToolResultMaxChars() { return toolResultMaxChars; }
    public void setToolResultMaxChars(int toolResultMaxChars) { this.toolResultMaxChars = toolResultMaxChars; }
}

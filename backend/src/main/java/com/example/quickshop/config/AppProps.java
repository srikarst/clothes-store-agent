package com.example.quickshop.config;

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

    // NEW: how many distinct sample values to collect per column for /api/schema
    private int schemaSamplesPerColumn = 5;

    // Which NLQ provider to use: rule | azure | aws
    private String nlqProvider = "rule";

    // Azure OpenAI (flat keys for simple binding)
    private String azureOpenaiEndpoint;     // e.g., https://your-resource.openai.azure.com
    private String azureOpenaiApiKey;       // from Azure portal / key vault
    private String azureOpenaiDeployment;   // your chat deployment name
    private String azureOpenaiApiVersion = "2024-02-15-preview";

    // (Optional) AWS Bedrock placeholders
    private String awsRegion;
    private String bedrockModelId;

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

    // NEW getters/setters
    public int getSchemaSamplesPerColumn() { return schemaSamplesPerColumn; }
    public void setSchemaSamplesPerColumn(int schemaSamplesPerColumn) { this.schemaSamplesPerColumn = schemaSamplesPerColumn; }

     public String getNlqProvider() { return nlqProvider; }
    public void setNlqProvider(String nlqProvider) { this.nlqProvider = nlqProvider; }

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
}

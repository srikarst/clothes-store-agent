package com.example.clothesstoreagent.config;

import com.example.clothesstoreagent.nlq.*;
import com.example.clothesstoreagent.service.SchemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NlqConfig {

    private static final Logger log = LoggerFactory.getLogger(NlqConfig.class);

    @Bean
    public NlqProvider nlqProvider(AppProps props, SchemaService schemaService) {
        String which = String.valueOf(props.getNlqProvider()).trim().toLowerCase();
        String mode = String.valueOf(props.getNlqMode()).trim().toLowerCase();
        
        log.info("Initializing NLQ provider: provider={} mode={}", which, mode);
        
        switch (which) {
            case "azure":
                // Check if Judge-Generator mode is enabled
                if ("judge-generator".equals(mode)) {
                    log.info("Using JudgeGeneratorAzureProvider (Judge → Generator + Repair)");
                    return new JudgeGeneratorAzureProvider(props, schemaService);
                } else {
                    log.info("Using AzureOpenAIProvider (single-call mode)");
                    return new AzureOpenAIProvider(props, schemaService);
                }
            case "aws":
            case "bedrock":
                log.info("Using AwsBedrockProvider");
                return new AwsBedrockProvider(props, schemaService);
            case "rule":
            default:
                log.info("Using RuleBasedProvider");
                return new RuleBasedProvider();
        }
    }

    @Bean
    public HistoryStore historyStore(AppProps props) {
        int ttlMinutes = props.getHistoryTtlMinutes();
        int maxTurns = props.getHistoryMaxTurns();
        log.info("Initializing InMemoryHistoryStore: ttlMinutes={}, maxTurns={}", ttlMinutes, maxTurns);
        return new InMemoryHistoryStore(ttlMinutes, maxTurns);
    }
}

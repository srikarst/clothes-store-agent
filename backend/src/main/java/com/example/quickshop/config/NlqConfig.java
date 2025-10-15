package com.example.quickshop.config;

import com.example.quickshop.nlq.*;
import com.example.quickshop.service.SchemaService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NlqConfig {

    @Bean
    public NlqProvider nlqProvider(AppProps props, SchemaService schemaService) {
        String which = String.valueOf(props.getNlqProvider()).trim().toLowerCase();
        switch (which) {
            case "azure":
                return new AzureOpenAIProvider(props, schemaService);
            case "aws":
            case "bedrock":
                return new AwsBedrockProvider(props, schemaService);
            case "rule":
            default:
                return new RuleBasedProvider();
        }
    }
}

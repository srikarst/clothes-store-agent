package com.example.clothesstoreagent.config;

import com.example.clothesstoreagent.nlq.*;
import com.example.clothesstoreagent.service.SchemaService;
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

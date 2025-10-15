package com.example.clothesstoreagent.nlq;

import com.example.clothesstoreagent.config.AppProps;
import com.example.clothesstoreagent.service.SchemaService;

public class AwsBedrockProvider implements NlqProvider {
    @SuppressWarnings("unused")
    private final AppProps props;
    @SuppressWarnings("unused")
    private final SchemaService schema;

    public AwsBedrockProvider(AppProps props, SchemaService schema) {
        this.props = props;
        this.schema = schema;
    }

    @Override
    public Plan compile(String prompt) {
        throw new IllegalStateException("Bedrock provider not implemented yet. Set app.nlqProvider=rule or azure.");
    }
}

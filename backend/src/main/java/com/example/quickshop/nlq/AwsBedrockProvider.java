package com.example.quickshop.nlq;

import com.example.quickshop.config.AppProps;
import com.example.quickshop.service.SchemaService;

public class AwsBedrockProvider implements NlqProvider {
    private final AppProps props;
    private final SchemaService schema;

    public AwsBedrockProvider(AppProps props, SchemaService schema) {
        this.props = props; this.schema = schema;
    }

    @Override
    public Plan compile(String prompt) {
        throw new IllegalStateException("Bedrock provider not implemented yet. Set app.nlqProvider=rule or azure.");
    }
}

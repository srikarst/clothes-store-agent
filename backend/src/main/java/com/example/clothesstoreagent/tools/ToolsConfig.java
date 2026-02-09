package com.example.clothesstoreagent.tools;

import com.example.clothesstoreagent.config.AppProps;
import com.example.clothesstoreagent.mcp.McpServerManager;
import com.example.clothesstoreagent.nlq.NlqProvider;
import com.example.clothesstoreagent.service.QueryService;
import com.example.clothesstoreagent.service.SchemaService;
import com.example.clothesstoreagent.tools.impl.DbQuerySelectTool;
import com.example.clothesstoreagent.tools.impl.DbSchemaCompactTool;
import com.example.clothesstoreagent.tools.impl.NlqText2SqlPlanTool;
import com.example.clothesstoreagent.tools.mcp.McpToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ToolsConfig {

    @Bean
    public Tool dbSchemaCompactTool(SchemaService schemaService, AppProps props) {
        return new DbSchemaCompactTool(schemaService, props);
    }

    @Bean
    public Tool dbQuerySelectTool(QueryService queryService, ObjectMapper om) {
        return new DbQuerySelectTool(queryService, om);
    }

    @Bean
    public Tool nlqText2SqlPlanTool(NlqProvider nlqProvider, ObjectMapper om) {
        return new NlqText2SqlPlanTool(nlqProvider, om);
    }

    @Bean
    public ToolRegistry toolRegistry(List<Tool> localTools,
                                     McpServerManager mcpServerManager,
                                     AppProps props,
                                     ObjectMapper om) {
        ToolRegistry local = new LocalToolRegistry(localTools);
        ToolRegistry mcp = new McpToolRegistry(mcpServerManager, props, om);
        return new CompositeToolRegistry(List.of(local, mcp));
    }
}





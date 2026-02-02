package com.example.clothesstoreagent.chat;

import com.example.clothesstoreagent.config.AppProps;
import com.example.clothesstoreagent.memory.ConversationStore;
import com.example.clothesstoreagent.memory.InMemoryConversationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatConfig.class);

    @Bean
    public ChatModel chatModel(AppProps props) {
        log.info("Initializing ChatModel: AzureOpenAiChatModel deployment={}", props.getAzureOpenaiDeployment());
        return new AzureOpenAiChatModel(props);
    }

    @Bean
    public ConversationStore conversationStore(AppProps props) {
        int ttlMinutes = props.getHistoryTtlMinutes();
        int maxTurns = Math.max(props.getHistoryMaxTurns(), ChatOrchestrator.MAX_TURNS_UPPER_CAP);
        log.info("Initializing InMemoryConversationStore: ttlMinutes={}, maxTurns={}", ttlMinutes, maxTurns);
        return new InMemoryConversationStore(ttlMinutes, maxTurns);
    }

    @Bean
    public ChatOrchestrator chatOrchestrator(AppProps props, ConversationStore store, ChatModel model) {
        return new ChatOrchestrator(props, store, model);
    }
}




package com.example.clothesstoreagent.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationIdResolverTest {

    @Test
    void headerTakesPrecedence() {
        ConversationIdResolver r = new ConversationIdResolver();
        ConversationIdResolver.Result out = r.resolve("h1", "q1", "b1");
        assertThat(out.conversationId()).isEqualTo("h1");
        assertThat(out.generated()).isFalse();
    }

    @Test
    void queryParamSecond() {
        ConversationIdResolver r = new ConversationIdResolver();
        ConversationIdResolver.Result out = r.resolve("  ", "q1", "b1");
        assertThat(out.conversationId()).isEqualTo("q1");
        assertThat(out.generated()).isFalse();
    }

    @Test
    void bodyThird() {
        ConversationIdResolver r = new ConversationIdResolver();
        ConversationIdResolver.Result out = r.resolve(null, " ", "b1");
        assertThat(out.conversationId()).isEqualTo("b1");
        assertThat(out.generated()).isFalse();
    }

    @Test
    void generatesIfMissing() {
        ConversationIdResolver r = new ConversationIdResolver();
        ConversationIdResolver.Result out = r.resolve(null, null, null);
        assertThat(out.conversationId()).isNotBlank();
        assertThat(out.generated()).isTrue();
    }
}




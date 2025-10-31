package com.example.clothesstoreagent.nlq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface NlqProvider {

    enum DecisionType {
        EXECUTE,
        CLARIFY,
        REJECT
    }

    final class Decision {
        public final String intent;
        public final DecisionType decision;
        public final String question;
        public final List<String> missing;
        public final String sql;
        public final Map<String, Object> params;

        public Decision(String intent,
                         DecisionType decision,
                         String question,
                         List<String> missing,
                         String sql,
                         Map<String, Object> params) {
            this.intent = intent;
            this.decision = decision;
            this.question = question;
            if (missing != null) {
                this.missing = Collections.unmodifiableList(new ArrayList<>(missing));
            } else {
                this.missing = List.of();
            }
            this.sql = sql;
            if (params != null) {
                this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
            } else {
                this.params = Map.of();
            }
        }

        public static Decision execute(String intent, String sql, Map<String, Object> params) {
            return new Decision(intent, DecisionType.EXECUTE, null, List.of(), sql, params);
        }

        public static Decision clarify(String intent, String question, List<String> missing) {
            return new Decision(intent, DecisionType.CLARIFY, question, missing, null, Map.of());
        }

        public static Decision reject(String intent, String question) {
            return new Decision(intent, DecisionType.REJECT, question, List.of(), null, Map.of());
        }

        public String decisionKey() {
            return decision.name().toLowerCase();
        }
    }

    Decision compile(String prompt);

    default List<String> suggestions() {
        return List.of(
            "top 5 products by revenue last month",
            "revenue by product",
            "daily revenue last 7 days",
            "new vs returning customers between 2025-10-02 and 2025-10-09"
        );
    }
}

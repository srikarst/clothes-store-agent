package com.example.clothesstoreagent.simple;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class SimpleRagStore {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "from",
            "your", "you", "are", "into", "about", "what", "how"
    );

    private final List<RagDocument> docs = List.of(
            new RagDocument(
                    "returns",
                    "Returns are accepted within 30 days for unused items with tags, and refunds are issued to the original payment method."
            ),
            new RagDocument(
                    "shipping",
                    "Standard shipping takes 3-5 business days and express shipping takes 1-2 business days."
            ),
            new RagDocument(
                    "catalog",
                    "Popular categories include denim jeans, casual shirts, jackets, and seasonal dresses."
            ),
            new RagDocument(
                    "sizing",
                    "Size guidance: if between sizes, choose one size up for relaxed fit and one size down for fitted style."
            ),
            new RagDocument(
                    "support",
                    "Support team is available from 9am to 6pm Monday to Friday for product and order issues."
            )
    );

    public List<String> retrieve(String query, int topK) {
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<ScoredDoc> scored = new ArrayList<>();
        for (RagDocument doc : docs) {
            Set<String> docTokens = tokenize(doc.text());
            int overlap = 0;
            for (String token : queryTokens) {
                if (docTokens.contains(token)) {
                    overlap++;
                }
            }
            double score = overlap;
            if (query.toLowerCase(Locale.ROOT).contains(doc.id())) {
                score += 0.5;
            }
            if (score > 0) {
                scored.add(new ScoredDoc(doc, score));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredDoc::score).reversed());

        int limit = Math.max(0, topK);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < scored.size() && i < limit; i++) {
            out.add(scored.get(i).doc().text());
        }
        return out;
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        String[] raw = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String token : raw) {
            String canonical = canonicalToken(token);
            if (canonical.length() >= 3 && !STOP_WORDS.contains(canonical)) {
                tokens.add(canonical);
            }
        }
        return tokens;
    }

    private String canonicalToken(String token) {
        if (token == null) {
            return "";
        }
        if (token.endsWith("s") && token.length() > 3) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private record RagDocument(String id, String text) {
    }

    private record ScoredDoc(RagDocument doc, double score) {
    }
}

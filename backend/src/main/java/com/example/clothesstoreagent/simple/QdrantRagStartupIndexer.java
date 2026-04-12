package com.example.clothesstoreagent.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class QdrantRagStartupIndexer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(QdrantRagStartupIndexer.class);

    private final QdrantRagIndexer ragIndexer;
    private final boolean ingestOnStartup;
    private final boolean failStartupOnError;

    public QdrantRagStartupIndexer(
            QdrantRagIndexer ragIndexer,
            @Value("${APP_RAG_INGEST_ON_STARTUP:false}") boolean ingestOnStartup,
            @Value("${APP_RAG_INGEST_FAIL_STARTUP_ON_ERROR:false}") boolean failStartupOnError
    ) {
        this.ragIndexer = ragIndexer;
        this.ingestOnStartup = ingestOnStartup;
        this.failStartupOnError = failStartupOnError;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!ingestOnStartup) {
            return;
        }

        QdrantRagIndexer.IndexingReport report = ragIndexer.indexFromConfiguredSource();
        if (report.ok()) {
            logger.info(
                    "RAG indexing complete. docsDiscovered={}, docsIndexed={}, chunksCreated={}, chunksIndexed={}, embeddingFailures={}, latencyMs={}",
                    report.docsDiscovered(),
                    report.docsIndexed(),
                    report.chunksCreated(),
                    report.chunksIndexed(),
                    report.embeddingFailures(),
                    report.latencyMs()
            );
            return;
        }

        logger.warn("RAG indexing failed on startup: {}", report.message());
        if (failStartupOnError) {
            throw new IllegalStateException("Startup indexing failed: " + report.message());
        }
    }
}

package net.flectone.bot.module.rag;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.module.Bot;
import net.flectone.bot.module.rag.client.RAGClient;
import net.flectone.bot.processing.SystemVariableResolver;
import net.flectone.bot.util.file.FileFacade;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class RagBot implements Bot {

    private final Map<Long, String> userThreads = new ConcurrentHashMap<>();
    private final FileFacade fileFacade;
    private final SystemVariableResolver systemVariableResolver;
    private final Logger logger;

    private RAGClient ragClient;

    @Override
    public void startup() {
        Integration.Rag config = getConfig();
        if (!isConfigValid(config)) {
            logger.warn("RAG configuration is incomplete. RAG client will not be initialized.");
            return;
        }

        String baseUrl = systemVariableResolver.substituteEnvVars(config.baseUrl());
        String workspaceSlug = systemVariableResolver.substituteEnvVars(config.workspaceSlug());
        String apiKey = systemVariableResolver.substituteEnvVars(config.apiKey());

        ragClient = new RAGClient(baseUrl, workspaceSlug, apiKey, logger);
        ragClient.startup();
    }

    @Override
    public void shutdown() {
        if (ragClient != null) {
            ragClient.shutdown();
        }
    }

    public CompletableFuture<String> askQuestion(Long userId, String query) {
        return askQuestion(userId, query, List.of());
    }

    public CompletableFuture<String> askQuestion(Long userId, String query, List<String> attachmentUrls) {
        if (!isInitialized()) {
            return CompletableFuture.failedFuture(new IllegalStateException("RAG client not initialized"));
        }

        return getOrCreateThread(userId)
                .thenCompose(threadSlug -> ragClient.askQuestionInThread(threadSlug, query, attachmentUrls));
    }

    public void streamQuestion(
            Long userId,
            String query,
            List<String> attachments,
            Consumer<String> onChunk,
            Runnable onDone,
            Consumer<String> onError
    ) {
        if (!isInitialized()) {
            onError.accept("RAG not initialized");
            return;
        }

        getOrCreateThread(userId)
                .thenAccept(thread -> ragClient.streamChatInThread(thread, query, attachments, onChunk, onDone, onError))
                .exceptionally(throwable -> {
                    onError.accept(throwable.getMessage());
                    return null;
                });
    }

    public CompletableFuture<Void> resetThread(Long userId) {
        if (!isInitialized()) {
            return CompletableFuture.failedFuture(new IllegalStateException("RAG client not initialized"));
        }

        String threadSlug = userThreads.remove(userId);
        if (threadSlug == null) {
            return CompletableFuture.completedFuture(null);
        }

        return ragClient.deleteThread(threadSlug)
                .exceptionally(throwable -> {
                    logger.warn("Failed to delete thread {} for user {}: {}", threadSlug, userId, throwable.getMessage());
                    return null;
                });
    }

    public boolean isInitialized() {
        return ragClient != null;
    }

    private CompletableFuture<String> getOrCreateThread(Long userId) {
        String existingThread = userThreads.get(userId);
        if (existingThread != null) {
            return CompletableFuture.completedFuture(existingThread);
        }

        String threadName = "user_" + userId;

        return ragClient.createThread(1, threadName)
                .exceptionally(throwable -> {
                    logger.warn("Thread creation failed, assuming it already exists: {}", threadName);
                    return threadName;
                })
                .thenApply(threadSlug -> {
                    userThreads.put(userId, threadSlug);
                    logger.info("Created new thread '{}' for user {}", threadSlug, userId);
                    return threadSlug;
                });
    }

    private Integration.Rag getConfig() {
        return fileFacade.integration().rag();
    }

    private boolean isConfigValid(Integration.Rag config) {
        return StringUtils.isNotEmpty(config.baseUrl()) && StringUtils.isNotEmpty(config.workspaceSlug());
    }
}
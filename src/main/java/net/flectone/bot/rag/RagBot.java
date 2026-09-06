package net.flectone.bot.rag;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.core.Bot;
import net.flectone.bot.file.FileFacade;
import net.flectone.bot.util.SystemVariableResolver;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class RagBot implements Bot {

    private static final String THREAD_PREFIX = "user_";

    private final Cache<Long, String> threads = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.DAYS)
            .maximumSize(10_000)
            .build();

    private final FileFacade fileFacade;
    private final SystemVariableResolver systemVariableResolver;
    private final Gson gson;
    private final Logger logger;

    private @Nullable RagClient client;

    @Override
    public String name() {
        return "RAG";
    }

    @Override
    public void startup() {
        Integration.Rag config = fileFacade.rag();
        if (config == null || StringUtils.isEmpty(config.baseUrl()) || StringUtils.isEmpty(config.workspaceSlug())) {
            logger.info("RAG is not configured, the ai answers stay off");
            return;
        }

        client = new RagClient(
                systemVariableResolver.substitute(config.baseUrl()),
                systemVariableResolver.substitute(config.workspaceSlug()),
                systemVariableResolver.substitute(config.apiKey()),
                gson,
                logger
        );
    }

    @Override
    public void shutdown() {
        if (client == null) return;

        client.shutdown();
        client = null;
    }

    @Override
    public boolean isEnabled() {
        return client != null;
    }

    public void ask(long userId,
                    String query,
                    List<String> attachments,
                    Consumer<String> onChunk,
                    Runnable onDone,
                    Consumer<String> onError) {

        RagClient ragClient = client;
        if (ragClient == null) {
            onError.accept("RAG is not configured");
            return;
        }

        thread(ragClient, userId)
                .thenAccept(thread -> ragClient.stream(thread, query, attachments, onChunk, onDone, onError))
                .exceptionally(throwable -> {
                    logger.warn("Failed to open a RAG thread for {}", userId, throwable);
                    onError.accept(String.valueOf(throwable.getMessage()));
                    return null;
                });
    }

    public CompletableFuture<Void> reset(long userId) {
        RagClient ragClient = client;
        String thread = threads.getIfPresent(userId);

        threads.invalidate(userId);

        if (ragClient == null || thread == null) {
            return CompletableFuture.completedFuture(null);
        }

        return ragClient.deleteThread(thread);
    }

    private CompletableFuture<String> thread(RagClient ragClient, long userId) {
        String existing = threads.getIfPresent(userId);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }

        String name = THREAD_PREFIX + userId;

        return ragClient.createThread(name)
                .exceptionally(throwable -> name)
                .thenApply(slug -> {
                    threads.put(userId, slug);
                    return slug;
                });
    }

}

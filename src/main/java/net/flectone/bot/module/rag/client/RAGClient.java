package net.flectone.bot.module.rag.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import okio.BufferedSource;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class RAGClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Map<String, String> EXT_TO_MIME = Map.ofEntries(
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("markdown", "text/markdown"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("doc", "application/msword"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp")
    );

    private static final int MIN_CHUNK_SIZE = 150;
    private static final int CONNECT_TIMEOUT = 10;
    private static final int READ_TIMEOUT = 120;
    private static final int WRITE_TIMEOUT = 30;
    private static final int MAX_REPETITION_COUNT = 3;
    private static final int REPETITION_CHECK_LENGTH = 100;

    private static final String ATTACHMENT_SEPARATOR = "\n\n--- %s ---\n";
    private static final String THINK_TAG = "</think>";

    private final OkHttpClient client;
    private final Gson gson;
    private final String baseUrl;
    private final String workspaceSlug;
    private final Logger logger;

    public RAGClient(String baseUrl, String workspaceSlug, String apiKey, Logger logger) {
        this.baseUrl = baseUrl;
        this.workspaceSlug = workspaceSlug;
        this.logger = logger;
        this.gson = new Gson();
        this.client = buildHttpClient(apiKey);
    }

    public void startup() {
        logger.info("RAG Client started for workspace: {}", workspaceSlug);
    }

    public void shutdown() {
        logger.info("RAG Client stopped");
    }

    public CompletableFuture<String> createThread(int userId, String threadName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject requestBody = buildThreadCreationRequest(userId, threadName);
                String url = buildUrl("/api/v1/workspace/%s/thread/new", workspaceSlug);
                Request request = buildJsonRequest(url, requestBody);

                try (Response response = client.newCall(request).execute()) {
                    validateResponse(response, "create thread");
                    return extractThreadSlug(response);
                }
            } catch (Exception e) {
                logger.error("Thread creation failed", e);
                throw new RuntimeException("Thread creation failed: " + e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<Void> deleteThread(String threadSlug) {
        return CompletableFuture.runAsync(() -> {
            try {
                String url = buildUrl("/api/v1/workspace/%s/thread/%s", workspaceSlug, threadSlug);
                Request request = new Request.Builder()
                        .url(url)
                        .delete()
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        logger.warn("Failed to delete thread {}: {}", threadSlug, response.code());
                    }
                }
            } catch (Exception e) {
                logger.error("Thread deletion failed for: {}", threadSlug, e);
                throw new RuntimeException("Thread deletion failed: " + e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<String> askQuestionInThread(String threadSlug, String query) {
        return askQuestionInThread(threadSlug, query, List.of());
    }

    public CompletableFuture<String> askQuestionInThread(String threadSlug, String query, List<String> attachmentUrls) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject requestBody = buildChatRequest(query, attachmentUrls);
                String url = buildUrl("/api/v1/workspace/%s/thread/%s/chat", workspaceSlug, threadSlug);
                Request request = buildJsonRequest(url, requestBody);

                try (Response response = client.newCall(request).execute()) {
                    validateResponse(response, "chat");
                    return extractTextResponse(response);
                }
            } catch (Exception e) {
                logger.error("RAG request failed for thread: {}", threadSlug, e);
                throw new RuntimeException("RAG request failed: " + e.getMessage(), e);
            }
        });
    }

    public void streamChatInThread(
            String threadSlug,
            String query,
            List<String> attachmentUrls,
            Consumer<String> onChunk,
            Runnable onDone,
            Consumer<String> onError
    ) {
        try {
            JsonObject requestBody = buildChatRequest(query, attachmentUrls);
            String url = buildUrl("/api/v1/workspace/%s/thread/%s/stream-chat", workspaceSlug, threadSlug);
            Request request = buildStreamRequest(url, requestBody);

            client.newCall(request).enqueue(new StreamCallback(onChunk, onDone, onError));
        } catch (Exception e) {
            onError.accept(e.getMessage());
        }
    }

    private OkHttpClient buildHttpClient(String apiKey) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);

        if (apiKey != null && !apiKey.isBlank()) {
            builder.addInterceptor(chain -> {
                Request original = chain.request();
                Request request = original.newBuilder()
                        .header("Authorization", "Bearer " + apiKey)
                        .build();
                return chain.proceed(request);
            });
        }

        return builder.build();
    }

    private JsonObject buildThreadCreationRequest(int userId, String threadName) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userId);
        requestBody.addProperty("name", threadName);
        requestBody.addProperty("slug", threadName);
        return requestBody;
    }

    private JsonObject buildChatRequest(String query, List<String> attachmentUrls) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("mode", "chat");

        if (query != null && !query.isBlank()) {
            requestBody.addProperty("message", query);
        }

        if (attachmentUrls != null && !attachmentUrls.isEmpty()) {
            processAttachments(requestBody, query, attachmentUrls);
        }

        return requestBody;
    }

    private void processAttachments(JsonObject requestBody, String query, List<String> attachmentUrls) {
        JsonArray attachments = new JsonArray();
        StringBuilder messageBuilder = new StringBuilder(query);

        for (String url : attachmentUrls) {
            try {
                Attachment attachment = downloadAttachment(url);
                if (attachment.isImage()) {
                    attachments.add(attachment.toJson());
                } else {
                    messageBuilder.append(String.format(ATTACHMENT_SEPARATOR, attachment.name()))
                            .append(attachment.text());
                }
            } catch (Exception e) {
                logger.error("Failed to process attachment: {}", url, e);
            }
        }

        if (!attachments.isEmpty()) {
            requestBody.add("attachments", attachments);
        }

        if (!messageBuilder.toString().equals(query)) {
            requestBody.addProperty("message", messageBuilder.toString());
        }
    }

    private Attachment downloadAttachment(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download attachment, HTTP " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response body for attachment");
            }

            byte[] data = body.bytes();
            if (data.length == 0) {
                throw new IOException("Zero-length attachment data");
            }

            String mime = guessMime(url, response.header("Content-Type"));
            String name = extractFileName(url);
            String base64 = Base64.getEncoder().encodeToString(data);

            return new Attachment(name, mime, base64, data);
        }
    }

    private Request buildJsonRequest(String url, JsonObject requestBody) {
        return new Request.Builder()
                .url(url)
                .post(RequestBody.create(gson.toJson(requestBody), JSON))
                .addHeader("Content-Type", "application/json")
                .build();
    }

    private Request buildStreamRequest(String url, JsonObject requestBody) {
        return new Request.Builder()
                .url(url)
                .post(RequestBody.create(gson.toJson(requestBody), JSON))
                .addHeader("Accept", "text/event-stream")
                .build();
    }

    private String buildUrl(String template, Object... args) {
        return baseUrl + String.format(template, args);
    }

    private void validateResponse(Response response, String operation) throws IOException {
        if (!response.isSuccessful()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            logger.error("Failed to {}: {} - {}", operation, response.code(), responseBody);
            throw new IOException("Failed to " + operation + ": " + response.code());
        }
    }

    private String extractThreadSlug(Response response) throws IOException {
        String responseBody = response.body() != null ? response.body().string() : "";
        JsonObject json = gson.fromJson(responseBody, JsonObject.class);

        if (!json.has("thread") || json.get("thread").isJsonNull()) {
            throw new IOException("Thread creation response missing 'thread' object");
        }

        return json.getAsJsonObject("thread").get("slug").getAsString();
    }

    private String extractTextResponse(Response response) throws IOException {
        String responseBody = response.body() != null ? response.body().string() : "";
        JsonObject json = gson.fromJson(responseBody, JsonObject.class);

        if (json.has("error")) {
            throw new IOException("AnythingLLM API returned error: " + json.get("error").getAsString());
        }

        if (!json.has("textResponse")) {
            logger.warn("Unexpected response format: {}", responseBody);
            return "Не удалось обработать ответ от языковой модели";
        }

        return cleanTextResponse(json.get("textResponse").getAsString());
    }

    private String cleanTextResponse(String textResponse) {
        int thinkEndIndex = textResponse.lastIndexOf(THINK_TAG);
        return thinkEndIndex != -1
                ? textResponse.substring(thinkEndIndex + THINK_TAG.length()).trim()
                : textResponse;
    }

    private String extractFileName(String url) {
        int queryStart = url.indexOf('?');
        String cleanUrl = queryStart > 0 ? url.substring(0, queryStart) : url;
        int lastSlash = cleanUrl.lastIndexOf('/');
        return lastSlash >= 0 ? cleanUrl.substring(lastSlash + 1) : "attachment";
    }

    private String guessMime(String url, String responseMime) {
        if (responseMime != null && !responseMime.isBlank()) {
            return responseMime.split(";")[0].trim();
        }

        String name = extractFileName(url);
        int dot = name.lastIndexOf('.');

        if (dot >= 0 && dot + 1 < name.length()) {
            String ext = name.substring(dot + 1).toLowerCase();
            return EXT_TO_MIME.getOrDefault(ext, "application/octet-stream");
        }

        return "application/octet-stream";
    }

    private record Attachment(String name, String mime, String base64, byte[] data) {

        public boolean isImage() {
            return mime.startsWith("image/");
        }

        public String text() {
            return new String(data, StandardCharsets.UTF_8);
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("name", name);
            json.addProperty("mime", mime);
            json.addProperty("contentString", "data:" + mime + ";base64," + base64);
            return json;
        }

    }

    private class StreamCallback implements Callback {

        private final Consumer<String> onChunk;
        private final Runnable onDone;
        private final Consumer<String> onError;

        public StreamCallback(Consumer<String> onChunk, Runnable onDone, Consumer<String> onError) {
            this.onChunk = onChunk;
            this.onDone = onDone;
            this.onError = onError;
        }

        @Override
        public void onFailure(@NotNull Call call, @NotNull IOException e) {
            onError.accept(e.getMessage());
        }

        @Override
        public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
            if (!response.isSuccessful() || response.body() == null) {
                onError.accept("HTTP " + response.code());
                return;
            }

            processStream(response.body().source());
        }

        private void processStream(BufferedSource source) throws IOException {
            StringBuilder buffer = new StringBuilder();
            RepetitionDetector detector = new RepetitionDetector();

            String line;
            while ((line = source.readUtf8Line()) != null) {
                if (!line.startsWith("data:")) continue;

                String json = line.substring(5).trim();
                JsonObject jsonObject = gson.fromJson(json, JsonObject.class);

                if (jsonObject.has("textResponse")) {
                    String chunk = jsonObject.get("textResponse").getAsString();

                    if (detector.isLooping(chunk)) {
                        logger.warn("Repetition loop detected in stream, stopping");
                        onError.accept("Model appears to be in a repetition loop. Please reset the conversation.");
                        return;
                    }

                    buffer.append(chunk);

                    if (buffer.length() >= MIN_CHUNK_SIZE) {
                        onChunk.accept(buffer.toString());
                        buffer.setLength(0);
                    }
                }

                if (jsonObject.has("close") && jsonObject.get("close").getAsBoolean()) {
                    break;
                }
            }

            if (!buffer.isEmpty()) {
                onChunk.accept(buffer.toString());
            }

            onDone.run();
        }

    }

    private static class RepetitionDetector {

        private final AtomicInteger repetitionCount = new AtomicInteger(0);
        private String lastSegment = "";

        boolean isLooping(String newChunk) {
            if (newChunk.length() < REPETITION_CHECK_LENGTH) {
                return false;
            }

            String currentSegment = newChunk.substring(Math.max(0, newChunk.length() - REPETITION_CHECK_LENGTH));

            if (currentSegment.equals(lastSegment)) {
                return repetitionCount.incrementAndGet() >= MAX_REPETITION_COUNT;
            } else {
                repetitionCount.set(0);
                lastSegment = currentSegment;
            }

            return false;
        }

    }
}
package net.flectone.bot.rag;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class RagClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Map<String, String> MIME_BY_EXTENSION = Map.ofEntries(
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("markdown", "text/markdown"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("yml", "text/plain"),
            Map.entry("yaml", "text/plain"),
            Map.entry("log", "text/plain"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("doc", "application/msword"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp")
    );

    private static final String DEFAULT_MIME = "application/octet-stream";
    private static final String THINK_TAG = "</think>";
    private static final String DATA_PREFIX = "data:";
    private static final String ATTACHMENT_SEPARATOR = "\n\n--- %s ---\n";

    private static final int CONNECT_TIMEOUT = 10;
    private static final int READ_TIMEOUT = 120;
    private static final int WRITE_TIMEOUT = 30;
    private static final long MAX_ATTACHMENT_BYTES = 8L * 1024 * 1024;

    private final OkHttpClient client;
    private final OkHttpClient downloadClient;
    private final Gson gson;
    private final String baseUrl;
    private final String workspaceSlug;
    private final Logger logger;

    public RagClient(String baseUrl, String workspaceSlug, String apiKey, Gson gson, Logger logger) {
        this.baseUrl = StringUtils.removeEnd(baseUrl, "/");
        this.workspaceSlug = workspaceSlug;
        this.gson = gson;
        this.logger = logger;
        this.downloadClient = buildClient(null);
        this.client = buildClient(apiKey);
    }

    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
        downloadClient.dispatcher().executorService().shutdown();
        downloadClient.connectionPool().evictAll();
    }

    public CompletableFuture<String> createThread(String name) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("slug", name);

        return CompletableFuture.supplyAsync(() -> {
            Request request = jsonRequest(url("/api/v1/workspace/%s/thread/new", workspaceSlug), body);

            try (Response response = client.newCall(request).execute()) {
                JsonObject json = readJson(response, "create thread");

                JsonObject thread = json.getAsJsonObject("thread");
                if (thread == null || !thread.has("slug")) {
                    throw new IllegalStateException("Thread creation answered without a slug");
                }

                return thread.get("slug").getAsString();
            } catch (IOException e) {
                throw new IllegalStateException("Thread creation failed", e);
            }
        });
    }

    public CompletableFuture<Void> deleteThread(String threadSlug) {
        return CompletableFuture.runAsync(() -> {
            Request request = new Request.Builder()
                    .url(url("/api/v1/workspace/%s/thread/%s", workspaceSlug, threadSlug))
                    .delete()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warn("Failed to delete thread {}: {}", threadSlug, response.code());
                }
            } catch (IOException e) {
                logger.warn("Failed to delete thread {}", threadSlug, e);
            }
        });
    }

    public void stream(String threadSlug,
                       String query,
                       List<String> attachmentUrls,
                       Consumer<String> onChunk,
                       Runnable onDone,
                       Consumer<String> onError) {

        Request request;
        try {
            request = new Request.Builder()
                    .url(url("/api/v1/workspace/%s/thread/%s/stream-chat", workspaceSlug, threadSlug))
                    .post(RequestBody.create(gson.toJson(chatRequest(query, attachmentUrls)), JSON))
                    .addHeader("Accept", "text/event-stream")
                    .build();
        } catch (Exception e) {
            onError.accept(String.valueOf(e.getMessage()));
            return;
        }

        client.newCall(request).enqueue(new StreamCallback(onChunk, onDone, onError));
    }

    private OkHttpClient buildClient(String apiKey) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);

        if (StringUtils.isNotBlank(apiKey)) {
            builder.addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                    .header("Authorization", "Bearer " + apiKey)
                    .build()
            ));
        }

        return builder.build();
    }

    private JsonObject chatRequest(String query, List<String> attachmentUrls) {
        JsonObject body = new JsonObject();
        body.addProperty("mode", "chat");

        StringBuilder message = new StringBuilder(StringUtils.defaultString(query));
        JsonArray attachments = new JsonArray();

        if (attachmentUrls != null) {
            for (String url : attachmentUrls) {
                appendAttachment(url, message, attachments);
            }
        }

        if (!attachments.isEmpty()) {
            body.add("attachments", attachments);
        }

        body.addProperty("message", message.toString());
        return body;
    }

    private void appendAttachment(String url, StringBuilder message, JsonArray attachments) {
        try {
            Attachment attachment = download(url);

            if (attachment.isImage()) {
                attachments.add(attachment.toJson());
                return;
            }

            message.append(String.format(ATTACHMENT_SEPARATOR, attachment.name())).append(attachment.text());
        } catch (Exception e) {
            logger.warn("Failed to read attachment {}", url, e);
        }
    }

    private Attachment download(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();

        try (Response response = downloadClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Attachment answered " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Attachment is empty");
            }

            if (body.contentLength() > MAX_ATTACHMENT_BYTES) {
                throw new IOException("Attachment is larger than " + MAX_ATTACHMENT_BYTES + " bytes");
            }

            byte[] data = body.bytes();
            if (data.length == 0) {
                throw new IOException("Attachment is empty");
            }

            return new Attachment(fileName(url), mime(url, response.header("Content-Type")), data);
        }
    }

    private Request jsonRequest(String url, JsonObject body) {
        return new Request.Builder()
                .url(url)
                .post(RequestBody.create(gson.toJson(body), JSON))
                .build();
    }

    private JsonObject readJson(Response response, String operation) throws IOException {
        ResponseBody body = response.body();
        String content = body == null ? StringUtils.EMPTY : body.string();

        if (!response.isSuccessful()) {
            throw new IOException("Failed to " + operation + ": " + response.code() + " " + content);
        }

        JsonObject json = gson.fromJson(content, JsonObject.class);
        if (json == null) {
            throw new IOException("Failed to " + operation + ": answer was not json");
        }

        return json;
    }

    private String url(String template, Object... arguments) {
        return baseUrl + String.format(template, arguments);
    }

    private String fileName(String url) {
        String path = StringUtils.substringBefore(url, "?");
        String name = StringUtils.substringAfterLast(path, "/");

        return name.isEmpty() ? "attachment" : name;
    }

    private String mime(String url, String contentType) {
        if (StringUtils.isNotBlank(contentType)) {
            return StringUtils.substringBefore(contentType, ";").trim();
        }

        String extension = StringUtils.substringAfterLast(fileName(url), ".").toLowerCase(java.util.Locale.ROOT);
        return MIME_BY_EXTENSION.getOrDefault(extension, DEFAULT_MIME);
    }

    private record Attachment(String name, String mime, byte[] data) {

        boolean isImage() {
            return mime.startsWith("image/");
        }

        String text() {
            return new String(data, StandardCharsets.UTF_8);
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("name", name);
            json.addProperty("mime", mime);
            json.addProperty("contentString", "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(data));

            return json;
        }

    }

    private class StreamCallback implements Callback {

        private final Consumer<String> onChunk;
        private final Runnable onDone;
        private final Consumer<String> onError;

        StreamCallback(Consumer<String> onChunk, Runnable onDone, Consumer<String> onError) {
            this.onChunk = onChunk;
            this.onDone = onDone;
            this.onError = onError;
        }

        @Override
        public void onFailure(@NotNull Call call, @NotNull IOException e) {
            onError.accept(String.valueOf(e.getMessage()));
        }

        @Override
        public void onResponse(@NotNull Call call, @NotNull Response response) {
            try (Response ignored = response) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    onError.accept("HTTP " + response.code());
                    return;
                }

                read(body.source());
                onDone.run();
            } catch (Exception e) {
                logger.warn("Streaming answer failed", e);
                onError.accept(String.valueOf(e.getMessage()));
            }
        }

        private void read(BufferedSource source) throws IOException {
            String line;
            boolean thinking = false;

            while ((line = source.readUtf8Line()) != null) {
                if (!line.startsWith(DATA_PREFIX)) continue;

                String payload = line.substring(DATA_PREFIX.length()).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) continue;

                JsonObject json = parse(payload);
                if (json == null) continue;

//                if (json.has("error") && !json.get("error").isJsonNull()) {
//                    onError.accept(json.get("error").getAsString());
//                    return;
//                }

                String chunk = chunk(json);
                if (StringUtils.isNotEmpty(chunk)) {
                    thinking |= chunk.contains("<think>");
                    if (thinking) {
                        int end = chunk.indexOf(THINK_TAG);
                        if (end == -1) continue;

                        chunk = chunk.substring(end + THINK_TAG.length());
                        thinking = false;
                    }

                    onChunk.accept(chunk);
                }

                if (json.has("close") && json.get("close").getAsBoolean()) return;
            }
        }

        private String chunk(JsonObject json) {
            if (!json.has("textResponse") || json.get("textResponse").isJsonNull()) return StringUtils.EMPTY;

            try {
                return json.get("textResponse").getAsString();
            } catch (Exception e) {
                return String.valueOf(json.get("textResponse"));
            }
        }

        private JsonObject parse(String payload) {
            try {
                return gson.fromJson(payload, JsonObject.class);
            } catch (JsonParseException e) {
                logger.debug("Skipped a malformed stream line: {}", payload);
                return null;
            }
        }

    }

}

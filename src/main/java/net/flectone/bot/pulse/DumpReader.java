package net.flectone.bot.pulse;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.file.FileFacade;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class DumpReader {

    private static final String KEY_PLACEHOLDER = "<key>";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient;
    private final Gson gson;
    private final FileFacade fileFacade;
    private final Logger logger;

    public Optional<PasteLink> findLink(String content) {
        Integration.Discord.Forum.Paste paste = config();
        if (paste == null || StringUtils.isEmpty(content) || StringUtils.isEmpty(paste.linkPattern())) {
            return Optional.empty();
        }

        try {
            Matcher matcher = Pattern.compile(paste.linkPattern()).matcher(content);
            if (matcher.groupCount() == 0) {
                logger.warn("Forum paste link_pattern needs a capturing group for the key: {}", paste.linkPattern());
                return Optional.empty();
            }

            if (!matcher.find()) return Optional.empty();

            String key = matcher.group(1);
            return StringUtils.isEmpty(key) ? Optional.empty() : Optional.of(new PasteLink(key, matcher.group()));
        } catch (PatternSyntaxException e) {
            logger.warn("Forum paste link_pattern is not a valid regex: {}", paste.linkPattern(), e);
            return Optional.empty();
        }
    }

    public Optional<PulseDump> read(String key) {
        Integration.Discord.Forum.Paste paste = config();
        if (paste == null || StringUtils.isEmpty(paste.rawUrl())) return Optional.empty();

        String url = StringUtils.replace(paste.rawUrl(), KEY_PLACEHOLDER, key);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Accept", "text/plain, application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.info("Paste {} answered {}", url, response.statusCode());
                return Optional.empty();
            }

            PulseDump dump = gson.fromJson(response.body(), PulseDump.class);
            if (dump == null || !dump.isPulseDump()) {
                logger.info("Paste {} holds no FlectonePulse dump", url);
                return Optional.empty();
            }

            return Optional.of(dump);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (JsonParseException e) {
            logger.info("Paste {} is not valid json", url);
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("Failed to read paste {}", url, e);
            return Optional.empty();
        }
    }

    private Integration.Discord.Forum.@org.jspecify.annotations.Nullable Paste config() {
        Integration.Discord.Forum forum = fileFacade.forum();
        return forum == null ? null : forum.paste();
    }

}

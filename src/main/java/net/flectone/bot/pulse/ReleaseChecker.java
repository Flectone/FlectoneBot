package net.flectone.bot.pulse;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.file.FileFacade;
import net.flectone.bot.util.VersionComparator;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ReleaseChecker {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long DEFAULT_CACHE_MINUTES = 30;

    private final AtomicReference<CachedRelease> cache = new AtomicReference<>();

    private final HttpClient httpClient;
    private final Gson gson;
    private final FileFacade fileFacade;
    private final VersionComparator versionComparator;
    private final Logger logger;

    public Optional<String> latestVersion() {
        Integration.Discord.Forum.Release release = config();
        if (release == null || StringUtils.isEmpty(release.apiUrl())) return Optional.empty();

        CachedRelease cached = cache.get();
        if (cached != null && !cached.isExpired(cacheDuration(release))) {
            return Optional.ofNullable(cached.version());
        }

        Optional<String> version = request(release.apiUrl());
        cache.set(new CachedRelease(version.orElse(null), Instant.now()));

        return version;
    }

    public VersionStatus status(String dumpVersion) {
        if (StringUtils.isEmpty(dumpVersion)) return VersionStatus.UNKNOWN;

        Optional<String> latest = latestVersion();
        if (latest.isEmpty()) return VersionStatus.UNKNOWN;

        int result = versionComparator.compare(dumpVersion, latest.get());
        if (result < 0) return VersionStatus.OUTDATED;
        if (versionComparator.isSnapshot(dumpVersion)) return VersionStatus.SNAPSHOT;

        return result > 0 ? VersionStatus.AHEAD : VersionStatus.CURRENT;
    }

    private Optional<String> request(String apiUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.warn("Release check answered {} for {}", response.statusCode(), apiUrl);
                return Optional.empty();
            }

            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            if (json == null || !json.has("tag_name")) return Optional.empty();

            String tag = json.get("tag_name").getAsString();
            return StringUtils.isEmpty(tag) ? Optional.empty() : Optional.of(StringUtils.removeStart(tag, "v"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("Release check failed for {}", apiUrl, e);
            return Optional.empty();
        }
    }

    private Duration cacheDuration(Integration.Discord.Forum.Release release) {
        long minutes = release.cacheMinutes() == null || release.cacheMinutes() <= 0
                ? DEFAULT_CACHE_MINUTES
                : release.cacheMinutes();

        return Duration.ofMinutes(minutes);
    }

    private Integration.Discord.Forum.@org.jspecify.annotations.Nullable Release config() {
        Integration.Discord.Forum forum = fileFacade.forum();
        return forum == null ? null : forum.release();
    }

    private record CachedRelease(String version, Instant checkedAt) {

        boolean isExpired(Duration duration) {
            return checkedAt.plus(duration).isBefore(Instant.now());
        }

    }

}

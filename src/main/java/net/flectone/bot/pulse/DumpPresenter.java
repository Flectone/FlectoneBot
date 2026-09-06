package net.flectone.bot.pulse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.file.FileFacade;
import net.flectone.bot.util.Placeholders;
import net.flectone.bot.util.Texts;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class DumpPresenter {

    private static final DateTimeFormatter CREATED_AT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private static final int LIST_LIMIT = 900;
    private static final int GROUP_PAD = 12;
    private static final String EMPTY_VALUE = "—";

    private final ReleaseChecker releaseChecker;
    private final FileFacade fileFacade;

    public Placeholders present(PulseDump dump, PasteLink link) {
        Placeholders placeholders = Placeholders.create()
                .put("paste_key", link.key())
                .put("paste_url", link.url())
                .put("server_uuid", dump.serverUUID())
                .put("server_core", dump.serverCore())
                .put("server_version", dump.serverVersion())
                .put("os_name", dump.osName())
                .put("os_version", dump.osVersion())
                .put("os_architecture", dump.osArchitecture())
                .put("java_version", dump.javaVersion())
                .put("cpu_cores", Objects.toString(dump.cpuCores(), "?"))
                .put("total_ram", formatRam(dump.totalRAM()))
                .put("location", dump.location())
                .put("project_version", dump.projectVersion())
                .put("project_language", dump.projectLanguage())
                .put("online_mode", dump.onlineMode())
                .put("proxy_mode", dump.proxyMode())
                .put("database_mode", dump.databaseMode())
                .put("player_count", Objects.toString(dump.playerCount(), "?"));

        Instant createdAt = parseCreatedAt(dump.createdAt());
        placeholders.put("created_at", createdAt == null ? dump.createdAt() : CREATED_AT.format(createdAt));
        placeholders.put("created_at_epoch", createdAt == null ? "0" : createdAt.getEpochSecond());

        return placeholders
                .putAll(presentModules(dump.modulesOrEmpty()))
                .putAll(presentVersion(dump.projectVersion()));
    }

    public VersionStatus status(PulseDump dump) {
        return releaseChecker.status(dump.projectVersion());
    }

    private Placeholders presentVersion(String projectVersion) {
        VersionStatus status = releaseChecker.status(projectVersion);
        String latest = releaseChecker.latestVersion().orElse("?");

        Placeholders placeholders = Placeholders.create()
                .put("latest_version", latest)
                .put("version_status", StringUtils.EMPTY);

        Integration.Discord.Forum.Release release = release();
        if (release == null) return placeholders;

        String text = switch (status) {
            case OUTDATED -> release.outdated();
            case CURRENT -> release.current();
            case AHEAD -> release.ahead();
            case SNAPSHOT -> release.snapshot();
            case UNKNOWN -> release.unknown();
        };

        return placeholders.put("version_status", Placeholders.create()
                .put("latest_version", latest)
                .put("project_version", projectVersion)
                .apply(StringUtils.defaultString(text))
        );
    }

    private Placeholders presentModules(Map<String, String> modules) {
        List<String> enabled = new ArrayList<>();
        List<String> disabled = new ArrayList<>();
        Map<String, int[]> groups = new LinkedHashMap<>();

        modules.forEach((name, value) -> {
            boolean isEnabled = Boolean.parseBoolean(value);
            (isEnabled ? enabled : disabled).add(ModuleNames.shorten(name));

            int[] counts = groups.computeIfAbsent(ModuleNames.group(name), group -> new int[2]);
            if (isEnabled) {
                counts[0]++;
            }

            counts[1]++;
        });

        return Placeholders.create()
                .put("modules_summary", summary(groups))
                .put("modules_enabled", list(enabled))
                .put("modules_disabled", list(disabled))
                .put("modules_enabled_count", enabled.size())
                .put("modules_disabled_count", disabled.size())
                .put("modules_count", modules.size());
    }

    private String summary(Map<String, int[]> groups) {
        if (groups.isEmpty()) return EMPTY_VALUE;

        List<String> lines = new ArrayList<>(groups.size());
        groups.forEach((group, counts) -> lines.add(String.format(Locale.ROOT, "%-" + GROUP_PAD + "s %d/%d", group, counts[0], counts[1])));

        return Texts.joinLimited(lines, "\n", LIST_LIMIT);
    }

    private String list(List<String> names) {
        if (names.isEmpty()) return EMPTY_VALUE;

        return Texts.joinLimited(names, ", ", LIST_LIMIT);
    }

    private String formatRam(Long totalRAM) {
        if (totalRAM == null || totalRAM <= 0) return "?";

        return String.format(Locale.ROOT, "%.1f GB", totalRAM / 1024d / 1024d / 1024d);
    }

    private Instant parseCreatedAt(String createdAt) {
        if (StringUtils.isEmpty(createdAt)) return null;

        try {
            return Instant.parse(createdAt);
        } catch (Exception e) {
            return null;
        }
    }

    private Integration.Discord.Forum.Release release() {
        Integration.Discord.Forum forum = fileFacade.forum();
        return forum == null ? null : forum.release();
    }

}

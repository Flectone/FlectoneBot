package net.flectone.bot.pulse;

import java.util.Map;

public record PulseDump(
        String serverUUID,
        String serverCore,
        String serverVersion,
        String osName,
        String osVersion,
        String osArchitecture,
        String javaVersion,
        Integer cpuCores,
        Long totalRAM,
        String location,
        String projectVersion,
        String projectLanguage,
        String onlineMode,
        String proxyMode,
        String databaseMode,
        Integer playerCount,
        Map<String, String> modules,
        String createdAt
) {

    public boolean isPulseDump() {
        return projectVersion != null && !projectVersion.isBlank();
    }

    public Map<String, String> modulesOrEmpty() {
        return modules == null ? Map.of() : modules;
    }

}

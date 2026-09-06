package net.flectone.bot.pulse;

public enum VersionStatus {

    OUTDATED,
    CURRENT,
    AHEAD,
    SNAPSHOT,
    UNKNOWN;

    public boolean needsUpdate() {
        return this == OUTDATED;
    }

}

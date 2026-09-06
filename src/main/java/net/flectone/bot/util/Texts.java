package net.flectone.bot.util;

import org.apache.commons.lang3.StringUtils;

public final class Texts {

    private static final String ELLIPSIS = "…";

    private Texts() {
    }

    public static String limit(String text, int limit) {
        if (StringUtils.isEmpty(text) || text.length() <= limit) return StringUtils.defaultString(text);

        return text.substring(0, Math.max(0, limit - ELLIPSIS.length())) + ELLIPSIS;
    }

    public static String joinLimited(Iterable<String> values, String separator, int limit) {
        StringBuilder joined = new StringBuilder();
        int skipped = 0;

        for (String value : values) {
            if (StringUtils.isEmpty(value)) continue;

            int length = joined.isEmpty() ? value.length() : joined.length() + separator.length() + value.length();
            if (length > limit) {
                skipped++;
                continue;
            }

            if (!joined.isEmpty()) {
                joined.append(separator);
            }

            joined.append(value);
        }

        if (skipped > 0) {
            joined.append(joined.isEmpty() ? StringUtils.EMPTY : " ").append(ELLIPSIS).append(" +").append(skipped);
        }

        return joined.toString();
    }

}

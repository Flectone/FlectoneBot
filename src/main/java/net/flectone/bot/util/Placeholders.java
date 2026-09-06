package net.flectone.bot.util;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

public final class Placeholders {

    private final Map<String, String> values = new LinkedHashMap<>();

    private Placeholders() {
    }

    public static Placeholders create() {
        return new Placeholders();
    }

    public Placeholders put(String key, Object value) {
        values.put(key, value == null ? StringUtils.EMPTY : String.valueOf(value));
        return this;
    }

    public Placeholders putAll(Placeholders other) {
        values.putAll(other.values);
        return this;
    }

    public String apply(String text) {
        if (StringUtils.isEmpty(text) || values.isEmpty()) return StringUtils.defaultString(text);

        String[] keys = values.keySet().stream().map(key -> "<" + key + ">").toArray(String[]::new);
        String[] replacements = values.values().toArray(String[]::new);

        return StringUtils.replaceEach(text, keys, replacements);
    }

    public UnaryOperator<String> formatter() {
        return this::apply;
    }

}

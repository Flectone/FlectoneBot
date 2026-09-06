package net.flectone.bot.pulse;

import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

public final class ModuleNames {

    private static final String LEGACY_GROUP = "module";
    private static final String PACKAGE_MARKER = ".module.";

    private ModuleNames() {
    }

    public static boolean isLegacy(String name) {
        return name.indexOf('.') != -1 || !name.equals(name.toUpperCase(Locale.ROOT));
    }

    public static String shorten(String name) {
        if (StringUtils.isEmpty(name)) return StringUtils.EMPTY;
        if (!isLegacy(name)) return name.toLowerCase(Locale.ROOT);

        String className = StringUtils.substringAfterLast(name, ".");
        if (className.isEmpty()) {
            className = name;
        }

        String stripped = StringUtils.removeEnd(className, "Impl");
        stripped = StringUtils.removeEnd(stripped, "Module");
        if (stripped.isEmpty()) {
            stripped = className;
        }

        return toSnakeCase(stripped);
    }

    public static String group(String name) {
        if (StringUtils.isEmpty(name)) return LEGACY_GROUP;

        if (!isLegacy(name)) {
            int separator = name.indexOf('_');
            return (separator == -1 ? name : name.substring(0, separator)).toLowerCase(Locale.ROOT);
        }

        String packagePath = StringUtils.substringAfter(name, PACKAGE_MARKER);
        if (packagePath.isEmpty()) return LEGACY_GROUP;

        String group = StringUtils.substringBefore(packagePath, ".");
        return group.isEmpty() ? LEGACY_GROUP : group.toLowerCase(Locale.ROOT);
    }

    private static String toSnakeCase(String className) {
        StringBuilder snake = new StringBuilder(className.length() + 8);

        for (int i = 0; i < className.length(); i++) {
            char symbol = className.charAt(i);

            if (Character.isUpperCase(symbol) && !snake.isEmpty()) {
                snake.append('_');
            }

            snake.append(Character.toLowerCase(symbol));
        }

        return snake.toString();
    }

}

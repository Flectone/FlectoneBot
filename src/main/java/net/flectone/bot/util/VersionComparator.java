package net.flectone.bot.util;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class VersionComparator {

    public boolean isSnapshot(String version) {
        return StringUtils.containsIgnoreCase(version, "-SNAPSHOT");
    }

    public int compare(String first, String second) {
        int[] firstNumbers = numbers(first);
        int[] secondNumbers = numbers(second);

        for (int i = 0; i < Math.max(firstNumbers.length, secondNumbers.length); i++) {
            int result = Integer.compare(number(firstNumbers, i), number(secondNumbers, i));
            if (result != 0) return result;
        }

        return Boolean.compare(!isSnapshot(first), !isSnapshot(second));
    }

    public boolean isOlderThan(String first, String second) {
        return compare(first, second) < 0;
    }

    public String release(String version) {
        if (StringUtils.isEmpty(version)) return StringUtils.EMPTY;

        int suffix = version.indexOf('-');
        return suffix == -1 ? version : version.substring(0, suffix);
    }

    private int[] numbers(String version) {
        String release = release(version).replaceFirst("^[vV]", StringUtils.EMPTY);
        if (release.isEmpty()) return new int[0];

        String[] parts = release.split("\\.");

        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            numbers[i] = parseNumber(parts[i]);
        }

        return numbers;
    }

    private int parseNumber(String part) {
        String digits = part.replaceAll("\\D.*$", StringUtils.EMPTY);

        try {
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int number(int[] numbers, int index) {
        return index < numbers.length ? numbers[index] : 0;
    }

}

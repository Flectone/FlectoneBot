package net.flectone.bot.util;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class SystemVariableResolver {

    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)(?::([^}]*))?}");

    public String substitute(String text) {
        if (StringUtils.isEmpty(text)) return StringUtils.defaultString(text);

        Matcher matcher = VARIABLE.matcher(text);

        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = System.getenv(matcher.group(1));
            if (value == null) {
                value = StringUtils.defaultString(matcher.group(2));
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }

        return matcher.appendTail(result).toString();
    }

}

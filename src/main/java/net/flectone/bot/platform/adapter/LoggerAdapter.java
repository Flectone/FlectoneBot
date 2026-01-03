package net.flectone.bot.platform.adapter;

import com.alessiodp.libby.logging.LogLevel;
import com.alessiodp.libby.logging.adapters.LogAdapter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class LoggerAdapter implements LogAdapter {

    private final Logger logger;

    @Override
    public void log(LogLevel logLevel, String s) {
        logger.log(Level.getLevel(logLevel.name()), s);
    }

    @Override
    public void log(LogLevel logLevel, String s, Throwable throwable) {
        logger.log(Level.getLevel(logLevel.name()), s, throwable);
    }

}

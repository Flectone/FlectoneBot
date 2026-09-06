package net.flectone.bot;

import com.alessiodp.libby.LibraryManager;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import net.flectone.bot.platform.adapter.LoggerAdapter;
import net.flectone.bot.platform.resolver.LibraryResolver;
import org.apache.logging.log4j.Logger;
import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.*;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

public class BotModule extends AbstractModule {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    private final Logger logger;
    private final Path projectPath;
    private final LoggerAdapter loggerAdapter;
    private final LibraryManager libraryManager;
    private final LibraryResolver libraryResolver;

    public BotModule(Logger logger,
                     Path projectPath,
                     LoggerAdapter loggerAdapter,
                     LibraryManager libraryManager,
                     LibraryResolver libraryResolver) {
        this.logger = logger;
        this.projectPath = projectPath;
        this.loggerAdapter = loggerAdapter;
        this.libraryManager = libraryManager;
        this.libraryResolver = libraryResolver;
    }

    @Override
    protected void configure() {
        bind(Logger.class).toInstance(logger);

        bind(Path.class).annotatedWith(Names.named("projectPath")).toInstance(projectPath);
        bind(Path.class).annotatedWith(Names.named("backupPath")).toInstance(projectPath.resolve("backups"));

        bind(LoggerAdapter.class).toInstance(loggerAdapter);
        bind(LibraryManager.class).toInstance(libraryManager);
        bind(LibraryResolver.class).toInstance(libraryResolver);
    }

    @Provides
    @Singleton
    public Gson provideGson() {
        return new Gson();
    }

    @Provides
    @Singleton
    public HttpClient provideHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Provides
    @Singleton
    public ObjectMapper provideYamlMapper() {
        return YAMLMapper.builder(
                        YAMLFactory.builder()
                                .loadSettings(LoadSettings.builder()
                                        .setBufferSize(8192) // increase string limit
                                        .setAllowDuplicateKeys(true) // fix duplicate keys
                                        .build()
                                )
                                .build()
                )
                // mapper
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY) // disable auto sorting
                .disable(MapperFeature.DETECT_PARAMETER_NAMES) // [databind#5314]
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS) // fix enum names
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES) // fix keys typed in a wrong case
                .enable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS) // fix custom classes deserialization
                // deserialization
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES) // jackson 2.x value
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS) // jackson 2.x value
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY) // convert single value to array
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT) // fix empty null string
                // serialization
                .enable(SerializationFeature.INDENT_OUTPUT) // indent output for values
                .disable(YAMLWriteFeature.SPLIT_LINES) // fix split long values
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER) // fix header
                .disable(YAMLWriteFeature.USE_NATIVE_TYPE_ID) // fix type id like !!java.util.Hashmap
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                // enum
                .disable(EnumFeature.READ_ENUMS_USING_TO_STRING) // jackson 2.x value
                .disable(EnumFeature.WRITE_ENUMS_USING_TO_STRING) // jackson 2.x value
                // fix nulls
                .changeDefaultPropertyInclusion(config -> JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL)) // show only non-null values
                .changeDefaultNullHandling(config -> JsonSetter.Value.forValueNulls(Nulls.SKIP)) // skip null values deserialization
                .withConfigOverride(String.class, o -> o.setNullHandling(JsonSetter.Value.forContentNulls(Nulls.AS_EMPTY))) // fix null string
                // no such override for collections, jackson cannot build an empty List for a missing key
                .addHandler(new DeserializationProblemHandler() {

                    @Override
                    public Object handleWeirdStringValue(DeserializationContext context, Class<?> type, String value, String failureMessage) {
                        logger.warn("Value '{}' is not valid for '{}', using the default one", value, type.getSimpleName());
                        return fallback(type);
                    }

                    @Override
                    public Object handleWeirdNumberValue(DeserializationContext context, Class<?> type, Number value, String failureMessage) {
                        logger.warn("Value '{}' is not valid for '{}', using the default one", value, type.getSimpleName());
                        return fallback(type);
                    }

                    @Override
                    public Object handleUnexpectedToken(DeserializationContext context, JavaType type, JsonToken token, JsonParser parser, String failureMessage) {
                        logger.warn("Expected {} but found '{}', using the default one", type, token);
                        parser.skipChildren();
                        return fallback(type.getRawClass());
                    }

                    @Override
                    public boolean handleUnknownProperty(DeserializationContext context, JsonParser parser, ValueDeserializer<?> deserializer, Object target, String property) {
                        logger.warn("Unknown key '{}', maybe a typo, ignoring it", property);
                        parser.skipChildren();
                        return true;
                    }

                })
                .addModule(new SimpleModule()
                        .addDeserializer(String.class, new ValueDeserializer<>() {

                            @Override
                            public String deserialize(JsonParser p, DeserializationContext ctxt) {
                                return p.currentToken() == JsonToken.VALUE_NULL ? "" : p.getString();
                            }

                            @Override
                            public String getNullValue(DeserializationContext ctxt) {
                                return "";
                            }

                            @Override
                            public String getAbsentValue(DeserializationContext ctxt) {
                                // a missing key is not an empty value, keep it null so the merger restores the default
                                return null;
                            }

                        })
                        .addDeserializer(Boolean.class, new ValueDeserializer<>() {

                            @Override
                            public Boolean deserialize(JsonParser p, DeserializationContext ctxt) {
                                if (p.currentToken() == JsonToken.VALUE_TRUE) return Boolean.TRUE;
                                if (p.currentToken() == JsonToken.VALUE_FALSE) return Boolean.FALSE;
                                if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) return p.getIntValue() != 0;

                                String value = String.valueOf(p.getString()).trim().toLowerCase(Locale.ROOT);
                                return switch (value) {
                                    case "true", "yes", "y", "on", "enable", "enabled" -> Boolean.TRUE;
                                    case "false", "no", "n", "off", "disable", "disabled" -> Boolean.FALSE;
                                    default -> {
                                        logger.warn("Value '{}' is not a true/false one, using the default one", value);
                                        yield null;
                                    }
                                };
                            }

                        })
                )
                .build();
    }

    // config records use boxed types, so null means "not set" and the merger puts the default back
    // primitives cannot express that
    private Object fallback(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return Boolean.FALSE;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0d;
        if (type == float.class) return 0.0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;

        return 0;
    }

}

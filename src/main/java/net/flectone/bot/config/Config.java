package net.flectone.bot.config;

import lombok.Builder;
import lombok.With;

@With
@Builder(toBuilder = true)
public record Config(

        String version,

        String language,

        Database database

) {

    @With
    @Builder(toBuilder = true)
    public record Database(Boolean ignoreExistingDriver,
                           Type type,
                           String name,
                           String host,
                           String port,
                           String user,
                           String password,
                           String parameters,
                           String prefix) {

        public enum Type {
            POSTGRESQL,
            H2,
            SQLITE,
            MYSQL,
            MARIADB
        }
    }

}

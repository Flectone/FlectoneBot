package net.flectone.bot.file;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FilePath {

    CONFIG("config.yml"),
    INTEGRATION("integration.yml");

    private final String path;

}

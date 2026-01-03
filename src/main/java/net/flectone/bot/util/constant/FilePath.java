package net.flectone.bot.util.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FilePath {

    CONFIG("config.yml"),
    INTEGRATION("integration.yml");

    private final String path;

}

package net.flectone.bot.config.merger;

import net.flectone.bot.config.Config;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapstructMergerConfig.class)
public interface ConfigMerger {

    Config merge(@MappingTarget Config.ConfigBuilder target, Config source);

}

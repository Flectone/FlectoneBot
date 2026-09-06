package net.flectone.bot.core;

public interface Bot {

    String name();

    void startup();

    void shutdown();

    boolean isEnabled();

}

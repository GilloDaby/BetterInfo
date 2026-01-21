package com.gillodaby.betterinfo;

import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class BetterInfoPlugin extends JavaPlugin {

    private BetterInfoService service;

    public BetterInfoPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    public void setup() {
    }

    @Override
    public void start() {
        BetterInfoConfig config = BetterInfoConfig.load(getDataDirectory());
        service = new BetterInfoService(config);

        CommandManager commandManager = CommandManager.get();
        commandManager.register(new InfoCommand(service, config));
        InfoShortcutRegistry shortcutRegistry = new InfoShortcutRegistry(commandManager, service);
        service.attachShortcutRegistry(shortcutRegistry);

        EventBus bus = HytaleServer.get().getEventBus();
        bus.registerGlobal(PlayerReadyEvent.class, service::handlePlayerReady);

        service.start();
        int infoLineCount = config.pages().stream().mapToInt(page -> page.lines().size()).sum();
        System.out.println("[BetterInfo] Started with " + infoLineCount + " info lines.");
    }

    @Override
    protected void shutdown() {
        if (service != null) {
            service.stop();
        }
    }
}

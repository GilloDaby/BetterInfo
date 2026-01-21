package com.gillodaby.betterinfo;

import com.hypixel.hytale.server.core.command.system.CommandManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class InfoShortcutRegistry {

    private final CommandManager commandManager;
    private final BetterInfoService service;
    private final Map<String, InfoShortcutCommand> commands = new HashMap<>();

    InfoShortcutRegistry(CommandManager commandManager, BetterInfoService service) {
        this.commandManager = commandManager;
        this.service = service;
    }

    void refreshShortcuts(List<BetterInfoConfig.InfoPage> pages) {
        if (pages == null) {
            commands.values().forEach(InfoShortcutCommand::disable);
            return;
        }
        Set<String> active = new HashSet<>();
        for (BetterInfoConfig.InfoPage page : pages) {
            if (page == null) {
                continue;
            }
            String code = sanitize(page.commandCode());
            if (code.isEmpty() || code.equals("info")) {
                continue;
            }
            active.add(code);
            InfoShortcutCommand command = commands.computeIfAbsent(code, name -> {
                InfoShortcutCommand created = new InfoShortcutCommand(name, service);
                commandManager.register(created);
                return created;
            });
            command.setPageCode(page.commandCode(), page.title());
        }
        for (Map.Entry<String, InfoShortcutCommand> entry : commands.entrySet()) {
            if (!active.contains(entry.getKey())) {
                entry.getValue().disable();
            }
        }
    }

    private String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String normalized = trimmed.replaceAll("\\s+", "");
        normalized = normalized.replaceAll("[^A-Za-z0-9_]", "");
        if (normalized.isEmpty()) {
            return "";
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}

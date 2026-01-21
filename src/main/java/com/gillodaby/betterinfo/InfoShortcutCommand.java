package com.gillodaby.betterinfo;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.util.concurrent.CompletableFuture;

final class InfoShortcutCommand extends AbstractCommand {

    private final BetterInfoService service;
    private final String commandName;
    private volatile String pageCode;
    private volatile String pageTitle;
    private volatile boolean enabled;

    InfoShortcutCommand(String commandName, BetterInfoService service) {
        super(commandName, "Open a Better Info page");
        this.commandName = commandName;
        this.service = service;
        this.enabled = false;
    }

    void setPageCode(String pageCode, String pageTitle) {
        this.pageCode = pageCode;
        this.pageTitle = pageTitle != null ? pageTitle : "";
        this.enabled = true;
    }

    void disable() {
        this.enabled = false;
        this.pageTitle = null;
        this.pageCode = null;
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        if (!ctx.isPlayer()) {
            ctx.sendMessage(service.text("Only players can view the info UI."));
            return CompletableFuture.completedFuture(null);
        }
        Player player = ctx.senderAs(Player.class);
        if (player == null) {
            ctx.sendMessage(service.text("Player not found for this command sender."));
            return CompletableFuture.completedFuture(null);
        }
        String permission = "betterinfo." + commandName;
        if (!player.hasPermission(permission) && !player.hasPermission("betterinfo.admin")) {
            ctx.sendMessage(service.text("[BetterInfo] Missing permission: " + permission));
            return CompletableFuture.completedFuture(null);
        }
        if (!enabled || pageCode == null || pageCode.isEmpty()) {
            ctx.sendMessage(service.text("No info page is currently linked to /" + commandName + "."));
            return CompletableFuture.completedFuture(null);
        }
        boolean ok = service.showInfoPage(player, pageCode);
        if (!ok) {
            ctx.sendMessage(service.text("No info page found for shortcut /" + commandName + "."));
        }
        return CompletableFuture.completedFuture(null);
    }
}

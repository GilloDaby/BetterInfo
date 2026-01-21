package com.gillodaby.betterinfo;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.util.concurrent.CompletableFuture;

/**
 * /info
 * /info reload
 * /info list
 * /info set <index> <text...>
 * /info add <text...>
 * /info remove <index>
 * /info save
 */
final class InfoCommand extends AbstractCommand {

    private final BetterInfoService service;
    private final BetterInfoConfig config;
    private final com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg<Integer> setIndexArg;
    private final com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg<Integer> removeIndexArg;

    InfoCommand(BetterInfoService service, BetterInfoConfig config) {
        super("info", "View and edit Better Info pages");
        this.service = service;
        this.config = config;
        setAllowsExtraArguments(true);

        // reload
        AbstractCommand reload = new AbstractCommand("reload", "Reload info config") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleReload(ctx);
            }
        };
        addSubCommand(reload);

        // list
        AbstractCommand list = new AbstractCommand("list", "List info lines") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleList(ctx);
            }
        };
        addSubCommand(list);

        String indexRangeDescription = "info line index (1-" + BetterInfoHud.MAX_LINES + ")";

        // set <index> <text...>
        AbstractCommand set = new AbstractCommand("set", "Set an info line") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleSet(ctx);
            }
        };
        this.setIndexArg = set.withRequiredArg("index", indexRangeDescription, ArgTypes.INTEGER);
        set.setAllowsExtraArguments(true);
        addSubCommand(set);

        // add <text...>
        AbstractCommand add = new AbstractCommand("add", "Append an info line") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleAdd(ctx);
            }
        };
        add.setAllowsExtraArguments(true);
        addSubCommand(add);

        AbstractCommand editor = new AbstractCommand("editor", "Open the info editor UI") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleEditor(ctx);
            }
        };
        addSubCommand(editor);

        // remove <index>
        AbstractCommand remove = new AbstractCommand("remove", "Remove an info line") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleRemove(ctx);
            }
        };
        this.removeIndexArg = remove.withRequiredArg("index", indexRangeDescription, ArgTypes.INTEGER);
        addSubCommand(remove);

        // save
        AbstractCommand save = new AbstractCommand("save", "Save info to config.yaml") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleSave(ctx);
            }
        };
        addSubCommand(save);

        // help
        AbstractCommand help = new AbstractCommand("help", "Show info command help") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleHelp(ctx);
            }
        };
        addSubCommand(help);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        return handleOpen(ctx);
    }

    private CompletableFuture<Void> handleOpen(CommandContext ctx) {
        if (!ctx.isPlayer()) {
            ctx.sendMessage(service.text("Only players can view the info UI."));
            return CompletableFuture.completedFuture(null);
        }
        Player player = ctx.senderAs(Player.class);
        if (player == null) {
            ctx.sendMessage(service.text("Player not found for this command sender."));
            return CompletableFuture.completedFuture(null);
        }
        String code = parsePageCode(ctx.getInputString());
        if (!code.isEmpty()) {
            String permission = "betterinfo." + code;
            if (!requireAnyPermission(ctx, permission, "betterinfo.admin")) {
                return CompletableFuture.completedFuture(null);
            }
            boolean ok = service.showInfoPage(player, code);
            if (!ok) {
                ctx.sendMessage(service.text("No info page found for code '" + code + "'."));
            }
            return CompletableFuture.completedFuture(null);
        }
        if (!requireAnyPermission(ctx, "betterinfo.info", "betterinfo.admin")) {
            return CompletableFuture.completedFuture(null);
        }
        service.showInfoPage(player);
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleReload(CommandContext ctx) {
        if (!requireAnyPermission(ctx, "betterinfo.reload", "betterinfo.admin")) {
            return CompletableFuture.completedFuture(null);
        }
        service.reloadConfig();
        ctx.sendMessage(service.text("[BetterInfo] Reloaded config."));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleList(CommandContext ctx) {
        if (!requireAnyPermission(ctx, "betterinfo.list", "betterinfo.admin")) {
            return CompletableFuture.completedFuture(null);
        }
        StringBuilder sb = new StringBuilder("Info lines (" + service.infoLines().size() + "):");
        int i = 1;
        for (String line : service.infoLines()) {
            sb.append("\n").append(i).append(": ").append(line == null ? "" : line);
            i++;
        }
        ctx.sendMessage(service.text(sb.toString()));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleSet(CommandContext ctx) {
        if (!requireAnyPermission(ctx, "betterinfo.set", "betterinfo.admin")) {
            return CompletableFuture.completedFuture(null);
        }
        Integer idx = ctx.get(setIndexArg);
        if (idx == null || idx < 1 || idx > BetterInfoHud.MAX_LINES) {
            ctx.sendMessage(service.text("Index must be between 1 and " + BetterInfoHud.MAX_LINES));
            return CompletableFuture.completedFuture(null);
        }
        String value = parseTextAfter(ctx.getInputString(), 3);
        if (value.isEmpty()) {
            ctx.sendMessage(service.text("Usage: /info set <index> <text>"));
            return CompletableFuture.completedFuture(null);
        }
        service.setInfoLine(idx - 1, value);
        ctx.sendMessage(service.text("Set info line " + idx + " to: " + value));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleAdd(CommandContext ctx) {
        if (!requireAnyPermission(ctx, "betterinfo.add", "betterinfo.admin")) {
            return CompletableFuture.completedFuture(null);
        }
        String value = parseTextAfter(ctx.getInputString(), 2);
        if (value.isEmpty()) {
            ctx.sendMessage(service.text("Usage: /info add <text>"));
            return CompletableFuture.completedFuture(null);
        }
        boolean ok = service.addInfoLine(value);
        if (!ok) {
            ctx.sendMessage(service.text("Cannot add more than " + BetterInfoHud.MAX_LINES + " info lines."));
            return CompletableFuture.completedFuture(null);
        }
        ctx.sendMessage(service.text("Added info line: " + value));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleEditor(CommandContext ctx) {
        if (!ctx.isPlayer()) {
            ctx.sendMessage(service.text("Only players can open the info editor."));
            return CompletableFuture.completedFuture(null);
        }
        Player player = ctx.senderAs(Player.class);
        if (player == null) {
            ctx.sendMessage(service.text("Player not found for this command sender."));
            return CompletableFuture.completedFuture(null);
        }
        if (!requireAnyPermission(ctx, "betterinfo.editor", "betterinfo.admin")) {
            return CompletableFuture.completedFuture(null);
        }
        service.openEditor(player);
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleRemove(CommandContext ctx) {
        if (!requireAnyPermission(ctx, "betterinfo.remove", "betterinfo.admin")) {
            return CompletableFuture.completedFuture(null);
        }
        Integer idx = ctx.get(removeIndexArg);
        if (idx == null || idx < 1 || idx > BetterInfoHud.MAX_LINES) {
            ctx.sendMessage(service.text("Index must be between 1 and " + BetterInfoHud.MAX_LINES));
            return CompletableFuture.completedFuture(null);
        }
        boolean ok = service.removeInfoLine(idx - 1);
        if (!ok) {
            ctx.sendMessage(service.text("No info line at index " + idx));
            return CompletableFuture.completedFuture(null);
        }
        ctx.sendMessage(service.text("Removed info line " + idx));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleSave(CommandContext ctx) {
        if (!requireAnyPermission(ctx, "betterinfo.save", "betterinfo.admin")) {
            return CompletableFuture.completedFuture(null);
        }
        service.saveConfig();
        ctx.sendMessage(service.text("Saved info to config.yaml."));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleHelp(CommandContext ctx) {
        if (!requireAnyPermission(ctx, "betterinfo.help", "betterinfo.admin")) {
            return CompletableFuture.completedFuture(null);
        }
        String help = String.join("\n",
            "/info",
            "/info <code>",
            "/info reload",
            "/info list",
            "/info set <index> <text>",
            "/info add <text>",
            "/info remove <index>",
            "/info editor",
            "/info save"
        );
        ctx.sendMessage(service.text(help));
        return CompletableFuture.completedFuture(null);
    }

    private boolean requireAnyPermission(CommandContext ctx, String primaryPermission, String... additionalPermissions) {
        if (!ctx.isPlayer()) {
            return true;
        }
        Player player = ctx.senderAs(Player.class);
        if (player == null) {
            ctx.sendMessage(service.text("Player not found for this command sender."));
            return false;
        }
        if (player.hasPermission(primaryPermission)) {
            return true;
        }
        for (String permission : additionalPermissions) {
            if (permission != null && !permission.isBlank() && player.hasPermission(permission)) {
                return true;
            }
        }
        ctx.sendMessage(service.text("[BetterInfo] Missing permission: " + primaryPermission));
        return false;
    }

    private String parseTextAfter(String input, int skipTokens) {
        if (input == null || input.isEmpty()) return "";
        String[] tokens = input.split(" ");
        if (tokens.length <= skipTokens) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = skipTokens; i < tokens.length; i++) {
            if (i > skipTokens) sb.append(" ");
            sb.append(tokens[i]);
        }
        String raw = sb.toString().trim();
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            raw = raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    private String parsePageCode(String input) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length < 2) {
            return "";
        }
        String value = tokens[1].trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        return value.toLowerCase();
    }
}

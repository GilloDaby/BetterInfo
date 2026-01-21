package com.gillodaby.betterinfo;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class BetterInfoService {

    private static final String DEFAULT_TITLE_COLOR = "#f8efe1";
    private static final String DEFAULT_LINE_COLOR = "#f0e7da";

    private final ScheduledExecutorService executor;
    private final Path acknowledgedPath;
    private final Set<UUID> acknowledged = Collections.synchronizedSet(new HashSet<>());
    private BetterInfoConfig config;
    private final List<String> mutableInfoLines = new ArrayList<>();
    private final List<BetterInfoConfig.InfoPage> pages = new ArrayList<>();
    private int editorPageIndex;
    private InfoShortcutRegistry shortcutRegistry;

    BetterInfoService(BetterInfoConfig config) {
        this.config = config;
        ensurePagesLoaded(config);
        this.acknowledgedPath = config.dataDir().resolve("acknowledged.txt");
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "BetterInfo-Worker");
            thread.setDaemon(true);
            return thread;
        });
        loadAcknowledged();
        this.editorPageIndex = 0;
    }

    void start() {
    }
    void attachShortcutRegistry(InfoShortcutRegistry registry) {
        this.shortcutRegistry = registry;
        notifyShortcutRegistry();
    }


    void stop() {
        executor.shutdownNow();
    }

    void handlePlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        ensureInfoLines();
        PlayerRef ref = player.getPlayerRef();
        if (ref == null || ref.getUuid() == null) {
            return;
        }
        if (!config.firstJoinPopupEnabled()) {
            return;
        }
        UUID id = ref.getUuid();
        if (!acknowledged.contains(id)) {
            acknowledged.add(id);
            persistAcknowledged();
            executor.schedule(() -> showInfoPage(player), 1200, TimeUnit.MILLISECONDS);
        }
    }

    void showInfoPage(Player player) {
        showInfoPage(player, 0);
    }

    void queueShowInfoPage(Player player, int pageIndex) {
        if (player == null) {
            return;
        }
        int target = Math.max(0, Math.min(BetterInfoConfig.MAX_PAGES - 1, pageIndex));
        executor.schedule(() -> showInfoPage(player, target), 200, TimeUnit.MILLISECONDS);
    }

    boolean showInfoPage(Player player, String pageCode) {
        if (pageCode == null || pageCode.isEmpty()) {
            showInfoPage(player);
            return true;
        }
        int index = findPageIndexByCode(pageCode);
        if (index < 0) {
            return false;
        }
        showInfoPage(player, index);
        return true;
    }

    void showInfoPage(Player player, int pageIndex) {
        if (player == null) {
            return;
        }
        PlayerRef ref = player.getPlayerRef();
        if (ref == null || ref.getUuid() == null || ref.getReference() == null || ref.getReference().getStore() == null) {
            return;
        }
        PageManager pageManager = player.getPageManager();
        if (pageManager == null) {
            return;
        }
        InfoView view = buildView(pageIndex);
        BetterInfoPage page = new BetterInfoPage(ref, player, this, view, config);
        pageManager.openCustomPage(ref.getReference(), ref.getReference().getStore(), page);
        System.out.println("[BetterInfo] Opened info page " + (pageIndex + 1) + " for " + safePlayerName(player));
    }

    void openEditor(Player player) {
        if (player == null) {
            return;
        }
        PlayerRef ref = player.getPlayerRef();
        if (ref == null || ref.getUuid() == null || ref.getReference() == null || ref.getReference().getStore() == null) {
            return;
        }
        PageManager pageManager = player.getPageManager();
        if (pageManager == null) {
            return;
        }
        InfoEditorPage editorPage = new InfoEditorPage(ref, this, config, snapshotPages(), editorPageIndex);
        pageManager.openCustomPage(ref.getReference(), ref.getReference().getStore(), editorPage);
    }

    List<String> infoLines() {
        return List.copyOf(mutableInfoLines);
    }

    void setInfoLine(int index, String text) {
        ensureSize(index + 1);
        mutableInfoLines.set(index, text);
        updatePageZeroLines();
    }

    boolean addInfoLine(String text) {
        int max = Math.min(BetterInfoHud.MAX_LINES, config.maxLines());
        if (mutableInfoLines.size() >= max) {
            return false;
        }
        mutableInfoLines.add(text);
        updatePageZeroLines();
        return true;
    }

    boolean removeInfoLine(int index) {
        if (index < 0 || index >= mutableInfoLines.size()) {
            return false;
        }
        mutableInfoLines.remove(index);
        updatePageZeroLines();
        return true;
    }

    void saveConfig() {
        List<BetterInfoConfig.InfoPage> snapshot = new ArrayList<>(pages);
        BetterInfoConfig updated = config.withPages(snapshot);
        config = updated;
        pages.clear();
        pages.addAll(updated.pages());
        ensurePageCapacity();
        syncMutableInfoLinesFromPageZero();
        BetterInfoConfig.persist(updated);
        notifyShortcutRegistry();
    }

    void reloadConfig() {
        this.config = BetterInfoConfig.load(config.dataDir());
        ensurePagesLoaded(config);
        notifyShortcutRegistry();
    }

    BetterInfoConfig currentConfig() {
        return config;
    }

    List<BetterInfoConfig.InfoPage> snapshotPages() {
        return new ArrayList<>(pages);
    }

    void applyEditorUpdate(int currentPageIndex, List<BetterInfoConfig.InfoPage> updatedPages) {
        this.editorPageIndex = Math.max(0, Math.min(BetterInfoConfig.MAX_PAGES - 1, currentPageIndex));
        pages.clear();
        if (updatedPages != null) {
            pages.addAll(updatedPages);
        }
        ensurePageCapacity();
        syncMutableInfoLinesFromPageZero();
        BetterInfoConfig updated = config.withPages(new ArrayList<>(pages));
        config = updated;
        pages.clear();
        pages.addAll(updated.pages());
        ensurePageCapacity();
        syncMutableInfoLinesFromPageZero();
        notifyShortcutRegistry();
    }

    com.hypixel.hytale.server.core.Message text(String raw) {
        return com.hypixel.hytale.server.core.Message.raw(raw);
    }

    private InfoView buildView(int pageIndex) {
        BetterInfoConfig.InfoPage current = pageAt(pageIndex);
        if (current == null) {
            InfoView.Line titleParts = parseDisplayLine("", DEFAULT_TITLE_COLOR);
            return new InfoView(
                titleParts.text(),
                titleParts.colorHex(),
                    Collections.emptyList(),
                    BetterInfoConfig.DEFAULT_HEADER_HINT,
                    BetterInfoConfig.DEFAULT_BUTTON_TEXT,
                    BetterInfoConfig.DEFAULT_FOOTER_TEXT,
                    pageIndex,
                    -1,
                    ""
            );
        }
        List<InfoView.Line> result = new ArrayList<>();
        int max = Math.min(config.maxLines(), BetterInfoHud.MAX_LINES);
        for (String line : current.lines()) {
            if (line == null) {
                continue;
            }
            result.add(parseDisplayLine(line));
            if (result.size() >= max) {
                break;
            }
        }
        String header = current.headerHint() != null && !current.headerHint().isEmpty()
                ? current.headerHint()
                : BetterInfoConfig.DEFAULT_HEADER_HINT;
        String buttonText = current.buttonText() != null && !current.buttonText().isEmpty()
                ? current.buttonText()
                : BetterInfoConfig.DEFAULT_BUTTON_TEXT;
        String footerText = resolveFooterText(current);
        int nextPage = current.nextPageIndex();
        InfoView.Line titleParts = parseDisplayLine(current.title(), DEFAULT_TITLE_COLOR);
        return new InfoView(
            titleParts.text(),
            titleParts.colorHex(),
                List.copyOf(result),
                header,
                buttonText,
                footerText,
                pageIndex,
                nextPage,
                current.commandCode()
        );
    }

    private InfoView.Line parseDisplayLine(String raw) {
        return parseDisplayLine(raw, DEFAULT_LINE_COLOR);
    }

    private InfoView.Line parseDisplayLine(String raw, String fallbackColor) {
        String defaultColor = (fallbackColor != null && !fallbackColor.isEmpty()) ? fallbackColor : DEFAULT_LINE_COLOR;
        if (raw == null) {
            return new InfoView.Line("", defaultColor);
        }
        String text = raw;
        String color = defaultColor;
        if (raw.startsWith("[") && raw.length() > 8) {
            int close = raw.indexOf(']');
            if (close > 1) {
                String maybeColor = raw.substring(1, close);
                String sanitized = sanitizeColor(maybeColor);
                if (!sanitized.isEmpty()) {
                    color = sanitized.startsWith("#") ? sanitized : "#" + sanitized;
                    text = raw.substring(close + 1);
                }
            }
        }
        return new InfoView.Line(text, color);
    }

    private String sanitizeColor(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.length() != 6) {
            return "";
        }
        for (char c : trimmed.toCharArray()) {
            if (Character.digit(c, 16) < 0) {
                return "";
            }
        }
        return "#" + trimmed.toLowerCase();
    }

    private String resolveFooterText(BetterInfoConfig.InfoPage page) {
        if (page.footerText() != null && !page.footerText().isEmpty()) {
            return page.footerText();
        }
        String code = page.commandCode();
        if (code != null && !code.isEmpty()) {
            return "You can reopen this page anytime with /" + code;
        }
        return BetterInfoConfig.DEFAULT_FOOTER_TEXT;
    }

    private void ensureInfoLines() {
        if (mutableInfoLines.isEmpty()) {
            syncMutableInfoLinesFromPageZero();
        }
    }

    private void ensureSize(int size) {
        while (mutableInfoLines.size() < size && mutableInfoLines.size() < BetterInfoHud.MAX_LINES) {
            mutableInfoLines.add("");
        }
    }

    private void updatePageZeroLines() {
        if (pages.isEmpty()) {
            pages.add(BetterInfoConfig.InfoPage.empty(1));
        }
        BetterInfoConfig.InfoPage current = pages.get(0);
        pages.set(0, current.withLines(new ArrayList<>(mutableInfoLines)));
    }

    private void ensurePagesLoaded(BetterInfoConfig config) {
        pages.clear();
        pages.addAll(config.pages());
        ensurePageCapacity();
        syncMutableInfoLinesFromPageZero();
    }

    private void ensurePageCapacity() {
        while (pages.size() < BetterInfoConfig.MAX_PAGES) {
            pages.add(BetterInfoConfig.InfoPage.empty(pages.size() + 1));
        }
    }

    private void notifyShortcutRegistry() {
        if (shortcutRegistry != null) {
            shortcutRegistry.refreshShortcuts(pages);
        }
    }

    private void syncMutableInfoLinesFromPageZero() {
        mutableInfoLines.clear();
        BetterInfoConfig.InfoPage page = currentPage();
        if (page != null) {
            mutableInfoLines.addAll(page.lines());
        }
    }

    private BetterInfoConfig.InfoPage currentPage() {
        return pageAt(0);
    }

    private BetterInfoConfig.InfoPage pageAt(int pageIndex) {
        if (pages.isEmpty()) {
            return BetterInfoConfig.InfoPage.empty(1);
        }
        int index = Math.max(0, Math.min(pages.size() - 1, pageIndex));
        return pages.get(index);
    }

    private int findPageIndexByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return -1;
        }
        String normalized = code.trim().toLowerCase();
        for (int i = 0; i < pages.size(); i++) {
            String stored = pages.get(i).commandCode();
            if (stored != null && !stored.isEmpty() && stored.equalsIgnoreCase(normalized)) {
                return i;
            }
        }
        return -1;
    }

    private void loadAcknowledged() {
        if (!Files.exists(acknowledgedPath)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(acknowledgedPath, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    acknowledged.add(UUID.fromString(trimmed));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException e) {
            System.out.println("[BetterInfo] Could not read acknowledged.txt: " + e.getMessage());
        }
    }

    private void persistAcknowledged() {
        List<String> lines = acknowledged.stream().map(UUID::toString).toList();
        try {
            Files.createDirectories(acknowledgedPath.getParent());
            Files.write(acknowledgedPath, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[BetterInfo] Could not write acknowledged.txt: " + e.getMessage());
        }
    }

    private String safePlayerName(Player player) {
        if (player == null) {
            return "Player";
        }
        String name = player.getDisplayName();
        return name != null ? name : "Player";
    }

}

package com.gillodaby.betterinfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class BetterInfoConfig {

    static final int MAX_PAGES = 12;
    static final int HARD_MAX_LINES = 50;
    static final String DEFAULT_HEADER_HINT = "All the essentials in one place.";
    static final String DEFAULT_BUTTON_TEXT = "Thanks for the info, let's go!";
    static final String DEFAULT_FOOTER_TEXT = "You can reopen this page anytime with /info";

    private final int maxLines;
    private final List<InfoPage> pages;
    private final boolean firstJoinPopupEnabled;
    private final Path dataDir;

    private BetterInfoConfig(int maxLines, List<InfoPage> pages, boolean firstJoinPopupEnabled, Path dataDir) {
        this.maxLines = maxLines;
        this.pages = pages;
        this.firstJoinPopupEnabled = firstJoinPopupEnabled;
        this.dataDir = dataDir;
    }

    int maxLines() {
        return Math.max(1, Math.min(HARD_MAX_LINES, maxLines));
    }

    boolean firstJoinPopupEnabled() {
        return firstJoinPopupEnabled;
    }

    List<InfoPage> pages() {
        return pages;
    }

    Path dataDir() {
        return dataDir;
    }

    static BetterInfoConfig load(Path dataDir) {
        if (dataDir == null) {
            dataDir = Path.of("BetterInfo");
        }
        Path configPath = dataDir.resolve("config.yaml");
        BetterInfoConfig defaults = defaults(dataDir);
        try {
            Files.createDirectories(configPath.getParent());
        } catch (IOException ignored) {
        }

        if (!Files.exists(configPath)) {
            persist(configPath, defaults);
            return defaults;
        }

        int maxLines = defaults.maxLines;
        boolean firstJoinPopupEnabled = defaults.firstJoinPopupEnabled;
        String legacyTitle = defaults.pages().get(0).title();
        @SuppressWarnings("unchecked")
        List<String>[] pageLines = new List[MAX_PAGES];
        String[] pageTitles = new String[MAX_PAGES];
        String[] pageHeaderHints = new String[MAX_PAGES];
        String[] pageCommandCodes = new String[MAX_PAGES];
        String[] pageButtonTexts = new String[MAX_PAGES];
        String[] pageFooterTexts = new String[MAX_PAGES];
        int[] pageNextPage = new int[MAX_PAGES];
        for (int i = 0; i < MAX_PAGES; i++) {
            InfoPage fallback = defaults.pages().get(i);
            pageLines[i] = new ArrayList<>(fallback.lines());
            pageTitles[i] = fallback.title();
            pageHeaderHints[i] = fallback.headerHint();
            pageCommandCodes[i] = fallback.commandCode();
            pageButtonTexts[i] = fallback.buttonText();
            pageFooterTexts[i] = fallback.footerText();
            pageNextPage[i] = fallback.nextPageIndex();
        }

        List<String> legacyLines = new ArrayList<>();
        boolean inLegacyLines = false;
        int currentPageLines = -1;

        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("lines:")) {
                    inLegacyLines = true;
                    currentPageLines = -1;
                    continue;
                }
                int pageLinesIndex = parsePageLinesIndex(line);
                if (pageLinesIndex >= 0) {
                    inLegacyLines = false;
                    currentPageLines = pageLinesIndex;
                    pageLines[currentPageLines] = new ArrayList<>();
                    continue;
                }
                if (inLegacyLines && line.startsWith("-")) {
                    String value = trimQuotes(line.substring(1).trim());
                    if (!value.isEmpty()) {
                        legacyLines.add(value);
                    }
                    continue;
                }
                if (currentPageLines >= 0 && line.startsWith("-")) {
                    String value = trimQuotes(line.substring(1).trim());
                    if (!value.isEmpty()) {
                        pageLines[currentPageLines].add(value);
                    }
                    continue;
                }
                inLegacyLines = false;
                currentPageLines = -1;

                int sep = line.indexOf(':');
                if (sep < 0) {
                    continue;
                }
                String key = line.substring(0, sep).trim();
                String value = trimQuotes(line.substring(sep + 1).trim());
                switch (key) {
                    case "maxLines" -> {
                        try {
                            maxLines = Integer.parseInt(value);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    case "firstJoinPopup" -> firstJoinPopupEnabled = parseToggleValue(value, firstJoinPopupEnabled);
                    case "title" -> {
                        if (!value.isEmpty()) {
                            legacyTitle = value;
                        }
                    }
                    default -> {
                        int pageTitleIndex = parsePageTitleIndex(key);
                        if (pageTitleIndex >= 0) {
                            pageTitles[pageTitleIndex] = value;
                            continue;
                        }
                        int headerIndex = parsePageHeaderIndex(key);
                        if (headerIndex >= 0) {
                            pageHeaderHints[headerIndex] = value;
                            continue;
                        }
                        int commandIndex = parsePageCommandIndex(key);
                        if (commandIndex >= 0) {
                            pageCommandCodes[commandIndex] = value;
                            continue;
                        }
                        int buttonIndex = parsePageButtonIndex(key);
                        if (buttonIndex >= 0) {
                            pageButtonTexts[buttonIndex] = value;
                            continue;
                        }
                        int footerIndex = parsePageFooterIndex(key);
                        if (footerIndex >= 0) {
                            pageFooterTexts[footerIndex] = value;
                            continue;
                        }
                        int nextIndex = parsePageNextIndex(key);
                        if (nextIndex >= 0) {
                            try {
                                pageNextPage[nextIndex] = Integer.parseInt(value) - 1;
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("[BetterInfo] Failed to read config.yaml, using defaults: " + e.getMessage());
            return defaults;
        }

        if (!legacyLines.isEmpty()) {
            pageLines[0].clear();
            pageLines[0].addAll(legacyLines);
        }
        if ((pageTitles[0] == null || pageTitles[0].isEmpty()) && legacyTitle != null) {
            pageTitles[0] = legacyTitle;
        }

        int cappedLines = Math.max(1, Math.min(HARD_MAX_LINES, maxLines));
        List<InfoPage> resolved = new ArrayList<>();
        for (int i = 0; i < MAX_PAGES; i++) {
            List<String> lines = new ArrayList<>(pageLines[i]);
            trimTrailingEmpty(lines);
            String title = pageTitles[i] != null && !pageTitles[i].isEmpty() ? pageTitles[i] : "Page " + (i + 1);
            String hint = pageHeaderHints[i] != null && !pageHeaderHints[i].isEmpty() ? pageHeaderHints[i] : DEFAULT_HEADER_HINT;
            String code = pageCommandCodes[i] != null ? pageCommandCodes[i] : "";
            String button = pageButtonTexts[i] != null && !pageButtonTexts[i].isEmpty() ? pageButtonTexts[i] : DEFAULT_BUTTON_TEXT;
            String footer = pageFooterTexts[i] != null ? pageFooterTexts[i] : "";
            int next = pageNextPage[i];
            resolved.add(new InfoPage(title, lines, hint, code, button, footer, next));
        }

        return new BetterInfoConfig(
            cappedLines,
            Collections.unmodifiableList(resolved),
            firstJoinPopupEnabled,
            dataDir
        );
    }

    private static BetterInfoConfig defaults(Path dataDir) {
        List<InfoPage> pages = new ArrayList<>();
        List<String> defaultLines = new ArrayList<>();
        defaultLines.add("Welcome to the server!");
        defaultLines.add("Need help? Run /info at any time.");
        defaultLines.add("Visit spawn for shops, quests and portals.");
        defaultLines.add("Join our Discord for live updates and support.");
        defaultLines.add("Claim land early to protect your builds.");
        defaultLines.add("Vote daily to unlock exclusive cosmetics.");
        pages.add(new InfoPage(
            "Server Info",
            defaultLines,
            DEFAULT_HEADER_HINT,
            "",
            DEFAULT_BUTTON_TEXT,
            "",
            -1
        ));
        for (int i = 1; i < MAX_PAGES; i++) {
            pages.add(InfoPage.empty(i + 1));
        }
        return new BetterInfoConfig(HARD_MAX_LINES, Collections.unmodifiableList(pages), true, dataDir);
    }

    BetterInfoConfig withPages(List<InfoPage> updatedPages) {
        List<InfoPage> resolved = new ArrayList<>();
        if (updatedPages != null) {
            resolved.addAll(updatedPages);
        }
        while (resolved.size() < MAX_PAGES) {
            resolved.add(InfoPage.empty(resolved.size() + 1));
        }
        return new BetterInfoConfig(
            maxLines,
            Collections.unmodifiableList(resolved),
            firstJoinPopupEnabled,
            dataDir
        );
    }

    static void persist(BetterInfoConfig cfg) {
        persist(cfg.dataDir().resolve("config.yaml"), cfg);
    }

    private static void persist(Path path, BetterInfoConfig cfg) {
        List<String> lines = new ArrayList<>();
        lines.add("# BetterInfo configuration");
        lines.add("# Prefix a color segment with [#rrggbb] to apply custom colors");
        lines.add("title: \"" + escape(cfg.pages().get(0).title()) + "\"");
        lines.add("maxLines: " + cfg.maxLines());
        lines.add("firstJoinPopup: " + (cfg.firstJoinPopupEnabled() ? "On" : "Off"));
        for (int i = 0; i < cfg.pages().size(); i++) {
            InfoPage page = cfg.pages().get(i);
            lines.add("# Page " + (i + 1));
            lines.add("page" + (i + 1) + "Title: \"" + escape(page.title()) + "\"");
            if (!page.headerHint().isEmpty()) {
                lines.add("page" + (i + 1) + "HeaderHint: \"" + escape(page.headerHint()) + "\"");
            }
            if (!page.commandCode().isEmpty()) {
                lines.add("page" + (i + 1) + "Command: \"" + escape(page.commandCode()) + "\"");
            }
            if (!page.buttonText().equals(DEFAULT_BUTTON_TEXT)) {
                lines.add("page" + (i + 1) + "ButtonText: \"" + escape(page.buttonText()) + "\"");
            }
            if (!page.footerText().isEmpty()) {
                lines.add("page" + (i + 1) + "FooterText: \"" + escape(page.footerText()) + "\"");
            }
            if (page.nextPageIndex() >= 0) {
                lines.add("page" + (i + 1) + "NextPage: " + (page.nextPageIndex() + 1));
            }
            lines.add("page" + (i + 1) + "Lines:");
            for (String line : page.lines()) {
                lines.add("  - \"" + escape(line) + "\"");
            }
        }
        persist(path, lines);
    }

    private static void persist(Path path, List<String> lines) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[BetterInfo] Could not write config.yaml: " + e.getMessage());
        }
    }

    private static void trimTrailingEmpty(List<String> lines) {
        while (!lines.isEmpty()) {
            String last = lines.get(lines.size() - 1);
            if (last == null || last.isEmpty()) {
                lines.remove(lines.size() - 1);
            } else {
                break;
            }
        }
    }

    private static int parsePageLinesIndex(String line) {
        if (line == null) {
            return -1;
        }
        for (int i = 1; i <= MAX_PAGES; i++) {
            if (line.startsWith("page" + i + "Lines:")) {
                return i - 1;
            }
        }
        return -1;
    }

    private static int parsePageTitleIndex(String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 1; i <= MAX_PAGES; i++) {
            if (key.equals("page" + i + "Title")) {
                return i - 1;
            }
        }
        return -1;
    }

    private static int parsePageHeaderIndex(String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 1; i <= MAX_PAGES; i++) {
            if (key.equals("page" + i + "HeaderHint")) {
                return i - 1;
            }
        }
        return -1;
    }

    private static int parsePageCommandIndex(String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 1; i <= MAX_PAGES; i++) {
            if (key.equals("page" + i + "Command")) {
                return i - 1;
            }
        }
        return -1;
    }

    private static int parsePageButtonIndex(String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 1; i <= MAX_PAGES; i++) {
            if (key.equals("page" + i + "ButtonText")) {
                return i - 1;
            }
        }
        return -1;
    }

    private static int parsePageFooterIndex(String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 1; i <= MAX_PAGES; i++) {
            if (key.equals("page" + i + "FooterText")) {
                return i - 1;
            }
        }
        return -1;
    }

    private static int parsePageNextIndex(String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 1; i <= MAX_PAGES; i++) {
            if (key.equals("page" + i + "NextPage")) {
                return i - 1;
            }
        }
        return -1;
    }

    private static boolean parseToggleValue(String raw, boolean defaultValue) {
        if (raw == null || raw.isEmpty()) {
            return defaultValue;
        }
        String normalized = raw.trim().toLowerCase();
        switch (normalized) {
            case "on":
            case "true":
            case "yes":
            case "y":
            case "1":
                return true;
            case "off":
            case "false":
            case "no":
            case "n":
            case "0":
                return false;
            default:
                return defaultValue;
        }
    }

    private static String trimQuotes(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        value = value.trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            if (value.length() >= 2) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\"", "\\\"");
    }

    static final class InfoPage {
        private final String title;
        private final List<String> lines;
        private final String headerHint;
        private final String commandCode;
        private final String buttonText;
        private final String footerText;
        private final int nextPageIndex;

        InfoPage(String title, List<String> lines) {
            this(title, lines, DEFAULT_HEADER_HINT, "", DEFAULT_BUTTON_TEXT, "", -1);
        }

        InfoPage(String title,
                 List<String> lines,
                 String headerHint,
                 String commandCode,
                 String buttonText,
                 String footerText,
                 int nextPageIndex) {
            this.title = title != null ? title : "";
            List<String> normalized = new ArrayList<>();
            if (lines != null) {
                for (String line : lines) {
                    if (line != null) {
                        normalized.add(line);
                    }
                }
            }
            this.lines = Collections.unmodifiableList(normalized);
            this.headerHint = headerHint != null ? headerHint : "";
            this.commandCode = commandCode != null ? commandCode : "";
            this.buttonText = buttonText != null && !buttonText.isEmpty() ? buttonText : DEFAULT_BUTTON_TEXT;
            this.footerText = footerText != null ? footerText : "";
            this.nextPageIndex = normalizeNextPage(nextPageIndex);
        }

        private int normalizeNextPage(int value) {
            if (value < 0 || value >= MAX_PAGES) {
                return -1;
            }
            return value;
        }

        String title() {
            return title;
        }

        List<String> lines() {
            return lines;
        }

        String headerHint() {
            return headerHint;
        }

        String commandCode() {
            return commandCode;
        }

        String buttonText() {
            return buttonText;
        }

        String footerText() {
            return footerText;
        }

        int nextPageIndex() {
            return nextPageIndex;
        }

        static InfoPage empty(int pageNumber) {
            return new InfoPage("Page " + pageNumber, Collections.emptyList(), "", "", DEFAULT_BUTTON_TEXT, "", -1);
        }

        InfoPage withTitle(String newTitle) {
            return new InfoPage(newTitle, lines, headerHint, commandCode, buttonText, footerText, nextPageIndex);
        }

        InfoPage withLines(List<String> newLines) {
            return new InfoPage(title, newLines, headerHint, commandCode, buttonText, footerText, nextPageIndex);
        }

        InfoPage withHeaderHint(String newHint) {
            return new InfoPage(title, lines, newHint, commandCode, buttonText, footerText, nextPageIndex);
        }

        InfoPage withCommandCode(String newCode) {
            return new InfoPage(title, lines, headerHint, newCode, buttonText, footerText, nextPageIndex);
        }

        InfoPage withButtonText(String newButtonText) {
            return new InfoPage(title, lines, headerHint, commandCode, newButtonText, footerText, nextPageIndex);
        }

        InfoPage withFooterText(String newFooterText) {
            return new InfoPage(title, lines, headerHint, commandCode, buttonText, newFooterText, nextPageIndex);
        }

        InfoPage withNextPageIndex(int newNextPageIndex) {
            return new InfoPage(title, lines, headerHint, commandCode, buttonText, footerText, newNextPageIndex);
        }
    }
}

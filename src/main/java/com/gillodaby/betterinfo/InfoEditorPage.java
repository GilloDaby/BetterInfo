package com.gillodaby.betterinfo;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;

final class InfoEditorPage extends InteractiveCustomUIPage<InfoEditorPage.EditorEventData> {

    private final PlayerRef playerRef;
    private final BetterInfoService service;
    private BetterInfoConfig config;
    private final List<PageDraft> pages = new ArrayList<>();
    private int currentPageIndex;

    InfoEditorPage(PlayerRef playerRef, BetterInfoService service, BetterInfoConfig config, List<BetterInfoConfig.InfoPage> pages, int activePageIndex) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, EditorEventData.CODEC);
        this.playerRef = playerRef;
        this.service = service;
        this.config = config;
        if (pages != null) {
            for (BetterInfoConfig.InfoPage page : pages) {
                this.pages.add(PageDraft.from(page));
            }
        }
        ensurePageCapacity();
        this.currentPageIndex = Math.max(0, Math.min(BetterInfoConfig.MAX_PAGES - 1, activePageIndex));
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder builder, UIEventBuilder events, Store<EntityStore> store) {
        builder.append("Pages/GilloDaby_BetterInfoEditor.ui");
        builder.set("#EditorRoot.Visible", true);
        builder.set("#PlaceholderHint.Text", "Use [#rrggbb] at the start of a segment to color it");
        builder.set("#LimitHint.Text", "Max info lines shown per page: " + BetterInfoConfig.HARD_MAX_LINES);
        builder.set("#ColorHint.Text", "Each page supports a title and up to " + BetterInfoConfig.HARD_MAX_LINES + " color-aware info lines");
        populatePageFields(builder);

        EventData apply = new EventData().append("Action", "apply");
        EventData save = new EventData().append("Action", "save");
        EventData reload = new EventData().append("Action", "reload");
        EventData close = new EventData().append("Action", "close");
        List<EventData> pageEvents = new ArrayList<>();
        for (int i = 1; i <= BetterInfoConfig.MAX_PAGES; i++) {
            pageEvents.add(new EventData().append("Action", "page" + i));
        }

        appendSharedField(apply, save, pageEvents, "@Title", "#TitleInput.Value");
        appendSharedField(apply, save, pageEvents, "@TitleColorHex", "#TitleColorHex.Value");
        appendSharedField(apply, save, pageEvents, "@HeaderHint", "#HeaderHintInput.Value");
        appendSharedField(apply, save, pageEvents, "@CommandCode", "#CommandCodeInput.Value");
        appendSharedField(apply, save, pageEvents, "@ButtonText", "#ButtonTextInput.Value");
        appendSharedField(apply, save, pageEvents, "@FooterText", "#FooterTextInput.Value");
        appendSharedField(apply, save, pageEvents, "@NextPage", "#NextPageInput.Value");

        for (int i = 0; i < BetterInfoConfig.HARD_MAX_LINES; i++) {
            String lineKey = "@Line" + (i + 1);
            String lineSelector = "#Line" + (i + 1) + "Input.Value";
            String colorKey = "@ColorHex" + (i + 1);
            String colorSelector = "#Line" + (i + 1) + "Color.Value";
            apply.append(lineKey, lineSelector);
            save.append(lineKey, lineSelector);
            for (EventData pageEvent : pageEvents) {
                pageEvent.append(lineKey, lineSelector);
            }
            apply.append(colorKey, colorSelector);
            save.append(colorKey, colorSelector);
            for (EventData pageEvent : pageEvents) {
                pageEvent.append(colorKey, colorSelector);
            }
        }

        events.addEventBinding(CustomUIEventBindingType.Activating, "#ApplyButton", apply, false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton", save, false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ReloadButton", reload, false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", close, false);
        for (int i = 1; i <= BetterInfoConfig.MAX_PAGES; i++) {
            String selector = "#Page" + i + "Button";
            events.addEventBinding(CustomUIEventBindingType.Activating, selector, pageEvents.get(i - 1), false);
        }
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, EditorEventData data) {
        if (data == null || data.action == null) {
            return;
        }
        EditorSubmission submission = collect(data);
        switch (data.action) {
            case "apply" -> {
                updateDraft(submission);
                service.applyEditorUpdate(currentPageIndex, buildUpdatedPages());
            }
            case "save" -> {
                updateDraft(submission);
                service.applyEditorUpdate(currentPageIndex, buildUpdatedPages());
                service.saveConfig();
                close();
            }
            case "reload" -> {
                service.reloadConfig();
                reloadFromService();
            }
            case "close" -> close();
            default -> {
                if (data.action.startsWith("page")) {
                    updateDraft(submission);
                    currentPageIndex = parsePageIndex(data.action);
                    refreshPageUI();
                }
            }
        }
    }

    private EditorSubmission collect(EditorEventData data) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < BetterInfoConfig.HARD_MAX_LINES; i++) {
            String color = resolveColor(data.color(i));
            String text = data.line(i);
            values.add(encodeLine(color, text));
        }
        String headerHint = safe(data.headerHint).trim();
        String commandCode = sanitizeCommandCode(data.commandCode);
        String buttonText = safe(data.buttonText).isEmpty() ? BetterInfoConfig.DEFAULT_BUTTON_TEXT : safe(data.buttonText);
        String footerText = safe(data.footerText).trim();
        int nextPageIndex = parseNextPageIndex(data.nextPage);
        return new EditorSubmission(
                encodeTitle(resolveColor(data.titleColorHex), safe(data.title)),
                values,
                headerHint,
                commandCode,
                buttonText,
                footerText,
                nextPageIndex
        );
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private String sanitizeCommandCode(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        normalized = normalized.replaceAll("\\s+", "");
        return normalized.toLowerCase();
    }

    private int parseNextPageIndex(String raw) {
        if (raw == null) {
            return -1;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return -1;
        }
        try {
            int value = Integer.parseInt(trimmed) - 1;
            if (value < 0 || value >= BetterInfoConfig.MAX_PAGES) {
                return -1;
            }
            return value;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String encodeLine(String color, String text) {
        String sanitized = safe(text);
        if (!sanitized.isEmpty() && textStartsWithColorSegment(sanitized)) {
            return sanitized;
        }
        if (color != null && !color.isEmpty() && !sanitized.isEmpty()) {
            return "[" + color + "]" + sanitized;
        }
        return sanitized;
    }

    private boolean textStartsWithColorSegment(String raw) {
        if (raw == null || raw.length() < 8 || raw.charAt(0) != '[') {
            return false;
        }
        int close = raw.indexOf(']');
        if (close <= 1 || close > 8) {
            return false;
        }
        String maybeColor = raw.substring(1, close);
        return !sanitizeColor(maybeColor).isEmpty();
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

    private String resolveColor(String hexValue) {
        return sanitizeColor(hexValue);
    }

    private String encodeTitle(String color, String title) {
        String sanitized = safe(title);
        if (!color.isEmpty()) {
            return "[" + color + "]" + sanitized;
        }
        return sanitized;
    }

    private void updateDraft(EditorSubmission submission) {
        PageDraft current = currentPage();
        current.title = submission.title();
        current.lines = trimTrailingEmpty(submission.lines());
        current.headerHint = submission.headerHint();
        current.commandCode = submission.commandCode();
        current.buttonText = submission.buttonText();
        current.footerText = submission.footerText();
        current.nextPage = submission.nextPageIndex();
    }

    private List<BetterInfoConfig.InfoPage> buildUpdatedPages() {
        List<BetterInfoConfig.InfoPage> updated = new ArrayList<>();
        for (PageDraft draft : pages) {
            updated.add(new BetterInfoConfig.InfoPage(
                    draft.title,
                    new ArrayList<>(draft.lines),
                    draft.headerHint,
                    draft.commandCode,
                    draft.buttonText,
                    draft.footerText,
                    draft.nextPage
            ));
        }
        return updated;
    }

    private void refreshPageUI() {
        UICommandBuilder builder = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        populatePageFields(builder);
        sendUpdate(builder, events, false);
    }

    private void reloadFromService() {
        this.config = service.currentConfig();
        this.pages.clear();
        for (BetterInfoConfig.InfoPage page : service.snapshotPages()) {
            this.pages.add(PageDraft.from(page));
        }
        ensurePageCapacity();
        currentPageIndex = Math.max(0, Math.min(BetterInfoConfig.MAX_PAGES - 1, currentPageIndex));
        refreshPageUI();
    }

    private void populatePageFields(UICommandBuilder builder) {
        PageDraft current = currentPage();
        LineParts titleParts = parseLine(current.title);
        builder.set("#EditorTitle.Text", "Better Info Editor - Page " + (currentPageIndex + 1));
        builder.set("#TitleInput.Value", titleParts.text());
        builder.set("#TitleColorHex.Value", titleParts.color().isEmpty() ? "#f6f8ff" : titleParts.color());
        builder.set("#HeaderHintInput.Value", current.headerHint);
        builder.set("#CommandCodeInput.Value", current.commandCode);
        String commandHint = current.commandCode.isEmpty()
            ? "Players can use /<code>"
            : "Players can use /" + current.commandCode;
        builder.set("#CommandHint.Text", commandHint);
        builder.set("#ButtonTextInput.Value", current.buttonText);
        builder.set("#FooterTextInput.Value", current.footerText);
        String nextValue = current.nextPage >= 0 ? String.valueOf(current.nextPage + 1) : "";
        builder.set("#NextPageInput.Value", nextValue);
        String nextHint = current.nextPage >= 0
            ? "Button navigates to page " + (current.nextPage + 1)
            : "Leave blank to confirm/close";
        builder.set("#NextPageHint.Text", nextHint);
        builder.set("#ActivePageLabel.Text", "Page active : " + (currentPageIndex + 1));
        for (int i = 0; i < BetterInfoConfig.HARD_MAX_LINES; i++) {
            String rowSelector = "#Line" + (i + 1) + "Row.Visible";
            String valueSelector = "#Line" + (i + 1) + "Input.Value";
            String colorSelector = "#Line" + (i + 1) + "Color.Value";
            builder.set(rowSelector, i < BetterInfoConfig.HARD_MAX_LINES);
            LineParts parts = i < current.lines.size() ? parseLine(current.lines.get(i)) : new LineParts("", "");
            builder.set(valueSelector, parts.text());
            builder.set(colorSelector, parts.color());
        }
        for (int i = 1; i <= BetterInfoConfig.MAX_PAGES; i++) {
            builder.set("#Page" + i + "Button.Visible", true);
        }
    }

    private void appendSharedField(EventData apply, EventData save, List<EventData> pageEvents, String key, String selector) {
        apply.append(key, selector);
        save.append(key, selector);
        for (EventData pageEvent : pageEvents) {
            pageEvent.append(key, selector);
        }
    }

    private PageDraft currentPage() {
        if (pages.isEmpty()) {
            return PageDraft.empty(1);
        }
        return pages.get(currentPageIndex);
    }

    private int parsePageIndex(String action) {
        if (action == null || action.length() <= 4 || !action.startsWith("page")) {
            return currentPageIndex;
        }
        try {
            int index = Integer.parseInt(action.substring(4)) - 1;
            return Math.max(0, Math.min(BetterInfoConfig.MAX_PAGES - 1, index));
        } catch (NumberFormatException e) {
            return currentPageIndex;
        }
    }

    private void ensurePageCapacity() {
        while (pages.size() < BetterInfoConfig.MAX_PAGES) {
            pages.add(PageDraft.empty(pages.size() + 1));
        }
    }

    private List<String> trimTrailingEmpty(List<String> lines) {
        List<String> trimmed = new ArrayList<>(lines);
        while (!trimmed.isEmpty()) {
            String last = trimmed.get(trimmed.size() - 1);
            if (last == null || last.isEmpty()) {
                trimmed.remove(trimmed.size() - 1);
            } else {
                break;
            }
        }
        return trimmed;
    }

    private LineParts parseLine(String raw) {
        if (raw == null) {
            return new LineParts("", "");
        }
        String color = "";
        String text = raw;
        if (raw.startsWith("[") && raw.length() > 8) {
            int close = raw.indexOf(']');
            if (close > 1) {
                String maybeColor = raw.substring(1, close);
                String sanitized = sanitizeColor(maybeColor);
                if (!sanitized.isEmpty()) {
                    color = sanitized;
                    text = raw.substring(close + 1);
                }
            }
        }
        return new LineParts(color, safe(text));
    }

    static final class EditorEventData {
        static final BuilderCodec<EditorEventData> CODEC;

        static {
            BuilderCodec.Builder<EditorEventData> builder = BuilderCodec.builder(EditorEventData.class, EditorEventData::new);
            builder.append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action).add();
            builder.append(new KeyedCodec<>("@Title", Codec.STRING), (e, v) -> e.title = v, e -> e.title).add();
            builder.append(new KeyedCodec<>("@TitleColorHex", Codec.STRING), (e, v) -> e.titleColorHex = v, e -> e.titleColorHex).add();
            builder.append(new KeyedCodec<>("@HeaderHint", Codec.STRING), (e, v) -> e.headerHint = v, e -> e.headerHint).add();
            builder.append(new KeyedCodec<>("@CommandCode", Codec.STRING), (e, v) -> e.commandCode = v, e -> e.commandCode).add();
            builder.append(new KeyedCodec<>("@ButtonText", Codec.STRING), (e, v) -> e.buttonText = v, e -> e.buttonText).add();
            builder.append(new KeyedCodec<>("@FooterText", Codec.STRING), (e, v) -> e.footerText = v, e -> e.footerText).add();
            builder.append(new KeyedCodec<>("@NextPage", Codec.STRING), (e, v) -> e.nextPage = v, e -> e.nextPage).add();
            for (int i = 1; i <= BetterInfoConfig.HARD_MAX_LINES; i++) {
                final int index = i - 1;
                builder.append(new KeyedCodec<>("@Line" + i, Codec.STRING), (e, v) -> e.lines[index] = v, e -> e.lines[index]).add();
            }
            for (int i = 1; i <= BetterInfoConfig.HARD_MAX_LINES; i++) {
                final int index = i - 1;
                builder.append(new KeyedCodec<>("@ColorHex" + i, Codec.STRING), (e, v) -> e.colors[index] = v, e -> e.colors[index]).add();
            }
            CODEC = builder.build();
        }

        private String action;
        private String title;
        private String titleColorHex;
        private String headerHint;
        private String commandCode;
        private String buttonText;
        private String footerText;
        private String nextPage;
        private final String[] lines = new String[BetterInfoConfig.HARD_MAX_LINES];
        private final String[] colors = new String[BetterInfoConfig.HARD_MAX_LINES];

        EditorEventData() {
        }

        String line(int index) {
            return index >= 0 && index < lines.length ? lines[index] : null;
        }

        String color(int index) {
            return index >= 0 && index < colors.length ? colors[index] : null;
        }
    }

    private record LineParts(String color, String text) {}

        private record EditorSubmission(
            String title,
            List<String> lines,
            String headerHint,
            String commandCode,
            String buttonText,
            String footerText,
            int nextPageIndex
        ) {}

    private static final class PageDraft {
        String title;
        List<String> lines;
        String headerHint;
        String commandCode;
        String buttonText;
        String footerText;
        int nextPage;

        PageDraft(String title, List<String> lines) {
            this(title, lines, "", "", BetterInfoConfig.DEFAULT_BUTTON_TEXT, "", -1);
        }

        PageDraft(String title, List<String> lines, String headerHint, String commandCode, String buttonText, String footerText, int nextPage) {
            this.title = title != null ? title : "";
            this.lines = lines != null ? lines : new ArrayList<>();
            this.headerHint = headerHint != null ? headerHint : "";
            this.commandCode = commandCode != null ? commandCode : "";
            this.buttonText = buttonText != null && !buttonText.isEmpty() ? buttonText : BetterInfoConfig.DEFAULT_BUTTON_TEXT;
            this.footerText = footerText != null ? footerText : "";
            this.nextPage = nextPage;
        }

        static PageDraft from(BetterInfoConfig.InfoPage page) {
            return new PageDraft(
                    page.title(),
                    new ArrayList<>(page.lines()),
                    page.headerHint(),
                    page.commandCode(),
                    page.buttonText(),
                    page.footerText(),
                    page.nextPageIndex()
            );
        }

        static PageDraft empty(int pageNumber) {
            return new PageDraft("Page " + pageNumber, new ArrayList<>(), "", "", BetterInfoConfig.DEFAULT_BUTTON_TEXT, "", -1);
        }
    }
}

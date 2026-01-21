package com.gillodaby.betterinfo;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BetterInfoPage extends InteractiveCustomUIPage<BetterInfoPage.PageEventData> {

    private static final String DEFAULT_TITLE_COLOR = "#f8efe1";
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+)");

    private final InfoView view;
    private final BetterInfoConfig config;
    private final BetterInfoService service;
    private final Player player;

    BetterInfoPage(PlayerRef ref, Player player, BetterInfoService service, InfoView view, BetterInfoConfig config) {
        super(ref, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageEventData.CODEC);
        this.player = player;
        this.service = service;
        this.view = view;
        this.config = config;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder builder, UIEventBuilder events, Store<EntityStore> store) {
        builder.append("Pages/GilloDaby_BetterInfo.ui");

        if (view == null) {
            builder.set("#InfoRoot.Visible", false);
            return;
        }

        builder.set("#InfoRoot.Visible", true);
        builder.set("#InfoTitle.Text", view.title());
        builder.set("#InfoTitle.Style.TextColor", resolveTitleColor(view));
        String header = view.headerHint() != null && !view.headerHint().isEmpty()
            ? view.headerHint()
            : BetterInfoConfig.DEFAULT_HEADER_HINT;
        builder.set("#HeaderHint.Text", header);
        String footer = view.footerText() != null && !view.footerText().isEmpty()
            ? view.footerText()
            : BetterInfoConfig.DEFAULT_FOOTER_TEXT;
        builder.set("#FooterHint.Text", footer);
        String buttonText = view.buttonText() != null && !view.buttonText().isEmpty()
            ? view.buttonText()
            : BetterInfoConfig.DEFAULT_BUTTON_TEXT;
        builder.set("#AgreeButton.Text", buttonText);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#AgreeButton", new EventData().append("Action", "next"), false);

        int lineCount = view.lines() != null ? view.lines().size() : 0;
        int maxVisible = Math.min(BetterInfoHud.MAX_LINES, lineCount);
        for (int i = 0; i < BetterInfoHud.MAX_LINES; i++) {
            String baseId = "#Line" + (i + 1);
            String textSelector = baseId + ".Text";
            String visibleSelector = baseId + ".Visible";
            String colorSelector = baseId + ".Style.Default.LabelStyle.TextColor";
            String hoverColorSelector = baseId + ".Style.Hovered.LabelStyle.TextColor";
            String pressedColorSelector = baseId + ".Style.Pressed.LabelStyle.TextColor";
            if (view.lines() != null && i < maxVisible && i < view.lines().size()) {
                InfoView.Line line = view.lines().get(i);
                builder.set(textSelector, line.text());
                builder.set(colorSelector, line.colorHex());
                builder.set(hoverColorSelector, line.colorHex());
                builder.set(pressedColorSelector, line.colorHex());
                builder.set(visibleSelector, true);
                String url = extractFirstUrl(line.text());
                if (!url.isEmpty()) {
                    events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        baseId,
                        new EventData().append("Action", "url").append("Url", url),
                        false
                    );
                }
            } else {
                builder.set(textSelector, "");
                builder.set(colorSelector, "#f0e7da");
                builder.set(hoverColorSelector, "#f0e7da");
                builder.set(pressedColorSelector, "#f0e7da");
                builder.set(visibleSelector, false);
            }
        }
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PageEventData data) {
        if (data != null && "url".equalsIgnoreCase(data.action) && data.url != null && !data.url.isBlank()) {
            if (player != null) {
                openUrl(player, data.url);
            }
            return;
        }
        if (view != null && service != null && player != null) {
            int next = view.nextPageIndex();
            if (next >= 0 && next != view.pageIndex()) {
                service.queueShowInfoPage(player, next);
                close();
                return;
            }
        }
        close();
    }

    private String resolveTitleColor(InfoView view) {
        if (view == null || view.titleColorHex() == null || view.titleColorHex().isEmpty()) {
            return DEFAULT_TITLE_COLOR;
        }
        return view.titleColorHex();
    }

    private String extractFirstUrl(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher matcher = URL_PATTERN.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        return trimUrl(matcher.group(1));
    }

    private String trimUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        String trimmed = url.trim();
        while (!trimmed.isEmpty()) {
            char last = trimmed.charAt(trimmed.length() - 1);
            if (last == '.' || last == ',' || last == ';' || last == ':' || last == ')' || last == ']' || last == '}' || last == '>' || last == '"' || last == '\'') {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
                continue;
            }
            break;
        }
        return trimmed;
    }

    private void openUrl(Player player, String url) {
        if (!tryOpenExternal(player, url)) {
            player.sendMessage(service.text("Open: " + url));
        }
    }

    private boolean tryOpenExternal(Player player, String url) {
        return tryInvoke(player, "openUrl", url)
            || tryInvoke(player, "openURL", url)
            || tryInvoke(player, "openBrowser", url)
            || tryInvoke(player, "openExternalUrl", url)
            || tryInvoke(player, "openExternalURL", url);
    }

    private boolean tryInvoke(Player player, String methodName, String url) {
        try {
            Method method = player.getClass().getMethod(methodName, String.class);
            method.invoke(player, url);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    static final class PageEventData {
        static final BuilderCodec<PageEventData> CODEC;

        static {
            BuilderCodec.Builder<PageEventData> builder = BuilderCodec.builder(PageEventData.class, PageEventData::new);
            builder.append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action).add();
            builder.append(new KeyedCodec<>("Url", Codec.STRING), (e, v) -> e.url = v, e -> e.url).add();
            CODEC = builder.build();
        }

        private String action;
        private String url;

        PageEventData() {
        }
    }
}

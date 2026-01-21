package com.gillodaby.betterinfo;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

final class BetterInfoHud extends CustomUIHud {

    static final int MAX_LINES = BetterInfoConfig.HARD_MAX_LINES;
    private static final String DEFAULT_TITLE_COLOR = "#f8efe1";

    private final BetterInfoConfig config;

    BetterInfoHud(PlayerRef ref, BetterInfoConfig config) {
        super(ref);
        this.config = config;
    }

    @Override
    protected void build(UICommandBuilder builder) {
        writeHud(builder, null);
    }

    void show(InfoView view) {
        UICommandBuilder builder = new UICommandBuilder();
        writeHud(builder, view);
        update(true, builder);
    }

    void hide() {
        UICommandBuilder builder = new UICommandBuilder();
        builder.append("Pages/GilloDaby_BetterInfo.ui");
        builder.set("#InfoRoot.Visible", false);
        update(true, builder);
    }

    private void writeHud(UICommandBuilder builder, InfoView view) {
        builder.append("Pages/GilloDaby_BetterInfo.ui");

        if (view == null) {
            builder.set("#InfoRoot.Visible", false);
            return;
        }

        builder.set("#InfoRoot.Visible", true);
        builder.set("#InfoTitle.Text", view.title());
        builder.set("#InfoTitle.Style.TextColor", resolveTitleColor(view));
        String buttonText = view.buttonText() != null && !view.buttonText().isEmpty()
            ? view.buttonText()
            : BetterInfoConfig.DEFAULT_BUTTON_TEXT;
        builder.set("#AgreeButton.Text", buttonText);
        String header = view.headerHint() != null && !view.headerHint().isEmpty()
            ? view.headerHint()
            : BetterInfoConfig.DEFAULT_HEADER_HINT;
        builder.set("#HeaderHint.Text", header);
        String footer = view.footerText() != null && !view.footerText().isEmpty()
            ? view.footerText()
            : BetterInfoConfig.DEFAULT_FOOTER_TEXT;
        builder.set("#FooterHint.Text", footer);

        int maxVisible = Math.min(MAX_LINES, config.maxLines());
        for (int i = 0; i < MAX_LINES; i++) {
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
            } else {
                builder.set(textSelector, "");
                builder.set(colorSelector, "#f0e7da");
                builder.set(hoverColorSelector, "#f0e7da");
                builder.set(pressedColorSelector, "#f0e7da");
                builder.set(visibleSelector, false);
            }
        }
    }

    private String resolveTitleColor(InfoView view) {
        if (view == null || view.titleColorHex() == null || view.titleColorHex().isEmpty()) {
            return DEFAULT_TITLE_COLOR;
        }
        return view.titleColorHex();
    }
}

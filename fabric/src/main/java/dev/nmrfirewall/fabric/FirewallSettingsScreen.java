package dev.nmrfirewall.fabric;

import dev.nmrfirewall.core.Decision;
import dev.nmrfirewall.core.OperationType;
import dev.nmrfirewall.core.PolicyConfig;
import dev.nmrfirewall.core.PolicyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Edits a draft; only Save changes the live policy. */
final class FirewallSettingsScreen extends Screen {
    private final Screen parent;
    private PolicyConfig draft;
    private Text status = Text.empty();
    private boolean operations;
    private int page;
    private List<Row> visible = List.of();
    private int left;
    private int panel;
    private int valueWidth;

    FirewallSettingsScreen(Screen parent) {
        super(t("config.title"));
        this.parent = parent;
        try { draft = new PolicyStore().load(NoMoreRatClient.configDirectory().resolve("rules.json")); }
        catch (Exception failure) { status = Text.translatable("no_more_rat.read_failed", failure.getMessage()); }
    }

    @Override protected void init() {
        clearChildren();
        panel = Math.min(620, width - 24);
        left = (width - panel) / 2;
        valueWidth = Math.min(164, panel / 2);
        if (draft != null) {
            button(t("config.general"), left, 48, panel / 2 - 2, () -> { operations = false; page = 0; init(); });
            button(t("config.operations"), left + panel / 2 + 2, 48, panel / 2 - 2, () -> { operations = true; page = 0; init(); });
            List<Row> rows = rows();
            int perPage = Math.max(1, (height - 140) / 30);
            int pages = (rows.size() + perPage - 1) / perPage;
            page = Math.min(page, pages - 1);
            visible = rows.subList(page * perPage, Math.min(rows.size(), (page + 1) * perPage));
            int y = 78;
            for (Row row : visible) {
                ButtonWidget control = button(row.value().get(), left + panel - valueWidth, y, valueWidth, () -> {
                    row.change().run();
                    status = t("config.unsaved");
                    init();
                });
                control.setTooltip(Tooltip.of(row.label().copy().append("\n").append(row.help())));
                y += 30;
            }
            if (pages > 1) {
                button(Text.literal("<"), left, height - 57, 30, () -> { page--; init(); }).active = page > 0;
                button(Text.translatable("no_more_rat.config.page", page + 1, pages), left + 34, height - 57, 80, () -> { }).active = false;
                button(Text.literal(">"), left + 118, height - 57, 30, () -> { page++; init(); }).active = page < pages - 1;
            }
            button(t("config.save"), left + panel - 100, height - 27, 100, this::save);
        }
        button(t("action.cancel"), left + panel - 204, height - 27, 100, this::close);
        if (panel >= 270) button(t("config.repository"), left, height - 27, Math.min(110, panel - 210),
                () -> client.keyboard.setClipboard("https://github.com/PL-VIP/NMR-No-More-RAT"));
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        if (operations) {
            for (OperationType operation : OperationType.values()) {
                // SECRET_ACCESS is reserved; no secret-read interception is currently implemented.
                if (operation == OperationType.SECRET_ACCESS) continue;
                String key = operation.name().toLowerCase(Locale.ROOT);
                rows.add(new Row(t("operation." + key), t("operation." + key + ".help"), () -> {
                    Decision explicit = draft.operationDecisions.get(operation);
                    return explicit == null ? Text.translatable("no_more_rat.config.inherit", decision(draft.decisionFor(operation)))
                            : decision(explicit);
                }, () -> {
                    Decision current = draft.operationDecisions.get(operation);
                    if (current == Decision.ALLOW) draft.operationDecisions.remove(operation);
                    else draft.operationDecisions.put(operation, current == null ? Decision.ASK : next(current));
                }));
            }
        } else {
            for (PolicyConfig.Preset preset : PolicyConfig.Preset.values()) {
                String key = preset.name().toLowerCase(Locale.ROOT);
                rows.add(new Row(t("config.preset." + key), t("config.preset." + key + ".help"),
                        () -> t(matchesPreset(preset) ? "config.selected" : "config.apply"), () -> draft.applyPreset(preset)));
            }
            rows.add(new Row(t("config.default"), t("config.default.help"), () -> decision(draft.defaultDecision),
                    () -> draft.defaultDecision = next(draft.defaultDecision)));
            rows.add(new Row(t("config.unknown"), t("config.unknown.help"), () -> decision(draft.unknownSourceDecision),
                    () -> draft.unknownSourceDecision = next(draft.unknownSourceDecision)));
            rows.add(new Row(t("config.strict"), t("config.strict.help"), () -> enabled(draft.strictMode),
                    () -> draft.strictMode = !draft.strictMode));
            rows.add(new Row(t("config.mod_config"), t("config.mod_config.help"), () -> enabled(draft.allowModConfigWrites),
                    () -> draft.allowModConfigWrites = !draft.allowModConfigWrites));
            rows.add(new Row(t("config.redirects"), t("config.redirects.help"), () -> enabled(draft.blockAutomaticHttpRedirects),
                    () -> draft.blockAutomaticHttpRedirects = !draft.blockAutomaticHttpRedirects));
            rows.add(new Row(t("config.timeout"), t("config.timeout.help"),
                    () -> Text.translatable("no_more_rat.config.seconds", draft.promptTimeoutSeconds),
                    () -> draft.promptTimeoutSeconds = nextNumber(draft.promptTimeoutSeconds, new int[]{5, 15, 30, 60, 120, 300})));
            rows.add(new Row(t("config.log_limit"), t("config.log_limit.help"), () -> Text.literal("" + draft.maxLogEntries),
                    () -> draft.maxLogEntries = nextNumber(draft.maxLogEntries, new int[]{100, 500, 2000, 10000, 100000})));
            rows.add(new Row(t("config.log_allowed"), t("config.log_allowed.help"), () -> enabled(draft.logAllowedOperations),
                    () -> draft.logAllowedOperations = !draft.logAllowedOperations));
        }
        return rows;
    }

    private void save() {
        try {
            new PolicyStore().savePreferences(NoMoreRatClient.configDirectory().resolve("rules.json"), draft);
            status = t("config.saved");
        } catch (Exception failure) { status = Text.translatable("no_more_rat.save_failed", failure.getMessage()); }
    }

    private boolean matchesPreset(PolicyConfig.Preset preset) {
        PolicyConfig candidate = new PolicyConfig();
        candidate.applyPreset(preset);
        if (draft.blockAutomaticHttpRedirects != candidate.blockAutomaticHttpRedirects
                || draft.strictMode != candidate.strictMode || draft.allowModConfigWrites != candidate.allowModConfigWrites
                || draft.defaultDecision != candidate.defaultDecision
                || draft.unknownSourceDecision != candidate.unknownSourceDecision) return false;
        for (OperationType operation : OperationType.values()) {
            if (draft.decisionFor(operation) != candidate.decisionFor(operation)) return false;
        }
        return true;
    }

    private ButtonWidget button(Text label, int x, int y, int w, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(label, button -> action.run()).dimensions(x, y, w, 20).build());
    }

    private static Decision next(Decision current) {
        return switch (current) { case ASK -> Decision.DENY; case DENY -> Decision.ALLOW; case ALLOW -> Decision.ASK; };
    }

    private static int nextNumber(int current, int[] choices) {
        for (int choice : choices) if (choice > current) return choice;
        return choices[0];
    }

    private static Text enabled(boolean value) { return t(value ? "value.enabled" : "value.disabled"); }
    private static Text decision(Decision decision) { return t("decision." + decision.name().toLowerCase(Locale.ROOT)); }
    private static Text t(String key) { return Text.translatable("no_more_rat." + key); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xF0182029);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(t("config.help").getString(), panel), left, 30, 0xFFAAB7C4);
        int y = 84;
        for (Row row : visible) {
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(row.label().getString(), panel - valueWidth - 10), left, y, 0xFFE1E8EF);
            y += 30;
        }
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(status.getString(), panel - 155), left + 155, height - 51, 0xFFF0C674);
    }

    @Override public void close() { client.setScreen(parent); }
    private record Row(Text label, Text help, Supplier<Text> value, Runnable change) { }
}

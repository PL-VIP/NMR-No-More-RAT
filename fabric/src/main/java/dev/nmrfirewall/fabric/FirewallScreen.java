package dev.nmrfirewall.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nmrfirewall.core.PolicyConfig;
import dev.nmrfirewall.core.PolicyStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class FirewallScreen extends Screen {
    private enum Tab { OVERVIEW, MODS, EVENTS, RULES, DOMAINS, SETTINGS }
    private final Screen parent;
    private Tab tab = Tab.OVERVIEW;
    private TextFieldWidget search;
    private List<String> rows = List.of();
    private String status = "";

    public FirewallScreen(Screen parent) {
        super(Text.translatable("no_more_rat.screen.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        clearChildren();
        int x = 12;
        int tabWidth = (width - 24 - 5 * 4) / 6;
        for (Tab value : Tab.values()) {
            String key = switch (value) {
                case OVERVIEW -> "no_more_rat.tab.status"; case MODS -> "no_more_rat.tab.mods";
                case EVENTS -> "no_more_rat.tab.events"; case RULES -> "no_more_rat.tab.rules";
                case DOMAINS -> "no_more_rat.tab.domains"; case SETTINGS -> "no_more_rat.tab.settings";
            };
            addDrawableChild(ButtonWidget.builder(Text.translatable(key), b -> {
                if (value == Tab.SETTINGS) client.setScreen(new FirewallSettingsScreen(this));
                else { tab = value; init(); }
            }).dimensions(x, 32, tabWidth, 20).build());
            x += tabWidth + 4;
        }
        search = new TextFieldWidget(textRenderer, 12, 60, Math.min(310, width - 140), 20, Text.translatable("no_more_rat.search"));
        search.setPlaceholder(Text.translatable("no_more_rat.search.placeholder"));
        search.setChangedListener(value -> refreshRows());
        addDrawableChild(search);
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.refresh"), b -> refreshRows())
                .dimensions(width - 214, height - 28, 96, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.done"), b -> close())
                .dimensions(width - 110, height - 28, 96, 20).build());
        if (tab == Tab.RULES || tab == Tab.DOMAINS) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.add_rule"), b ->
                    client.setScreen(new RuleEditorScreen(this))).dimensions(width - 122, 60, 110, 20).build());
        }
        refreshRows();
    }

    private void refreshRows() {
        try {
            rows = switch (tab) {
                case OVERVIEW -> overviewRows();
                case MODS -> FabricLoader.getInstance().getAllMods().stream()
                        .map(m -> m.getMetadata().getName() + "  [" + m.getMetadata().getId() + "]  "
                                + m.getMetadata().getVersion().getFriendlyString()).sorted().toList();
                case EVENTS -> eventRows();
                case RULES -> ruleRows(false);
                case DOMAINS -> ruleRows(true);
                case SETTINGS -> List.of();
            };
            String query = search == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            if (!query.isBlank()) rows = rows.stream().filter(r -> r.toLowerCase(Locale.ROOT).contains(query)).toList();
            status = Text.translatable("no_more_rat.items", rows.size()).getString();
        } catch (Exception failure) {
            rows = List.of(Text.translatable("no_more_rat.read_failed", failure.getMessage()).getString());
            status = Text.translatable("no_more_rat.error").getString();
        }
    }

    private List<String> overviewRows() throws Exception {
        PolicyConfig config = new PolicyStore().load(NoMoreRatClient.configDirectory().resolve("rules.json"));
        return List.of(
                Text.translatable("no_more_rat.status.agent", Text.translatable(NoMoreRatClient.agentActive()
                        ? "no_more_rat.status.active" : "no_more_rat.status.inactive")).getString(),
                Text.translatable("no_more_rat.status.strict", Text.translatable(config.strictMode
                        ? "no_more_rat.value.enabled" : "no_more_rat.value.disabled")).getString(),
                Text.translatable("no_more_rat.status.rule_count", config.rules.size()).getString(),
                Text.translatable("no_more_rat.status.unknown", config.unknownSourceDecision).getString(),
                Text.translatable("no_more_rat.status.directory", NoMoreRatClient.configDirectory().toAbsolutePath()).getString(),
                Text.translatable("no_more_rat.status.privacy").getString());
    }

    private List<String> eventRows() throws Exception {
        Path file = NoMoreRatClient.configDirectory().resolve("events.jsonl");
        if (!Files.exists(file)) return List.of(Text.translatable("no_more_rat.events.empty").getString());
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Collections.reverse(lines);
        List<String> result = new ArrayList<>();
        for (String line : lines.stream().limit(250).toList()) {
            try {
                JsonObject event = JsonParser.parseString(line).getAsJsonObject();
                result.add(text(event, "timestamp") + "  " + text(event, "operation") + "  "
                        + nested(event, "source", "modId") + "  → " + text(event, "target") + "  ["
                        + text(event, "decision") + "]");
            } catch (Exception malformed) { result.add(Text.translatable("no_more_rat.events.malformed").getString()); }
        }
        return result;
    }

    private List<String> ruleRows(boolean domainsOnly) throws Exception {
        PolicyConfig config = new PolicyStore().load(NoMoreRatClient.configDirectory().resolve("rules.json"));
        return config.rules.stream().filter(r -> !domainsOnly || r.targetPattern() != null)
                .map(r -> r.decision() + "  " + (r.modId() == null ? "*" : r.modId()) + "  "
                        + (r.operation() == null ? "*" : r.operation()) + "  "
                        + (r.targetPattern() == null ? "*" : r.targetPattern()) + "  #" + r.id()).toList();
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "?";
    }
    private static String nested(JsonObject object, String parent, String key) {
        return object.has(parent) ? text(object.getAsJsonObject(parent), key) : "unknown";
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        int y = 90;
        for (String row : rows.stream().limit(Math.max(1, (height - 130) / 12)).toList()) {
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(row, width - 28), 14, y, 0xD8DEE9);
            y += 12;
        }
        context.drawTextWithShadow(textRenderer, status, 14, height - 24, 0xA0A0A0);
    }

    @Override public void close() { client.setScreen(parent); }
}

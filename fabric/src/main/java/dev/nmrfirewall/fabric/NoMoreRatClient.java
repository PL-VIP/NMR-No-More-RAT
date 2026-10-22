package dev.nmrfirewall.fabric;

import dev.nmrfirewall.core.AgentBridge;
import dev.nmrfirewall.core.Decision;
import dev.nmrfirewall.core.PromptHandler;
import dev.nmrfirewall.core.SecurityRequest;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;

public final class NoMoreRatClient implements ClientModInitializer {
    private static final AtomicBoolean SETUP_SHOWN = new AtomicBoolean();
    private static final PromptBridgeEndpoint SHARED_BRIDGE = new PromptBridgeEndpoint();
    private static final PromptHandler PROMPTS = (request, preliminary) -> requestPrompt(request);

    static CompletableFuture<Decision> requestPrompt(SecurityRequest request) {
        CompletableFuture<Decision> result = new CompletableFuture<>();
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.currentScreen instanceof FirewallPromptScreen) {
                result.complete(Decision.DENY);
            } else {
                FirewallPromptScreen prompt = new FirewallPromptScreen(client.currentScreen, request, result);
                result.whenComplete((decision, failure) -> client.execute(prompt::dismissIfOpen));
                client.setScreen(prompt);
            }
        });
        return result;
    }

    @Override
    public void onInitializeClient() {
        AgentBridge.register(PROMPTS);
        System.getProperties().put("nmr.prompt.bridge", SHARED_BRIDGE);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!agentActive() && client.currentScreen instanceof TitleScreen && SETUP_SHOWN.compareAndSet(false, true)) {
                client.setScreen(new AgentSetupScreen(client.currentScreen));
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            AgentBridge.unregister(PROMPTS);
            System.getProperties().remove("nmr.prompt.bridge", SHARED_BRIDGE);
        }, "nmr-shutdown"));
    }

    public static Path configDirectory() {
        String agentPath = System.getProperty("nmr.configDir");
        return agentPath == null ? FabricLoader.getInstance().getConfigDir().resolve("no-more-rat") : Path.of(agentPath);
    }

    public static boolean agentActive() { return Boolean.getBoolean("nmr.agent.active"); }
}

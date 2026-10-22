package dev.nmrfirewall.fabric;

import dev.nmrfirewall.core.Decision;
import dev.nmrfirewall.core.OperationType;
import dev.nmrfirewall.core.SecurityRequest;
import dev.nmrfirewall.core.SourceIdentity;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;

public final class PromptBridgeEndpoint {
    public CompletableFuture<String> request(String operation, String modId, String name, String version,
                                             String jarName, String sha256, String callerClass, String confidence,
                                             String target, String httpMethod) {
        SecurityRequest request = buildRequest(operation, modId, name, version, jarName, sha256, callerClass,
                confidence, target, httpMethod);
        return NoMoreRatClient.requestPrompt(request).thenApply(Decision::name);
    }

    public void notifyBlocked(String operation, String modId, String name, String version,
                              String jarName, String sha256, String callerClass, String confidence,
                              String target, String httpMethod) {
        SecurityRequest request = buildRequest(operation, modId, name, version, jarName, sha256, callerClass,
                confidence, target, httpMethod);
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (!(client.currentScreen instanceof FirewallPromptScreen)
                    && !(client.currentScreen instanceof FirewallBlockedScreen)) {
                client.setScreen(new FirewallBlockedScreen(client.currentScreen, request));
            }
        });
    }

    private static SecurityRequest buildRequest(String operation, String modId, String name, String version,
                                                String jarName, String sha256, String callerClass, String confidence,
                                                String target, String httpMethod) {
        SourceIdentity.Confidence parsedConfidence;
        try { parsedConfidence = SourceIdentity.Confidence.valueOf(confidence); }
        catch (IllegalArgumentException ignored) { parsedConfidence = SourceIdentity.Confidence.UNKNOWN; }
        SourceIdentity source = new SourceIdentity(modId, name, version, jarName, sha256, callerClass, parsedConfidence);
        Map<String, String> parameters = httpMethod == null || httpMethod.isBlank()
                ? Map.of() : Map.of("method", httpMethod);
        return new SecurityRequest(OperationType.valueOf(operation), source, target, parameters);
    }
}

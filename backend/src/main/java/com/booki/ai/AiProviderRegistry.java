package com.booki.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves which {@link AiProvider} bean to use for a given session. All 4
 * providers are always registered (see their {@code @Component("name")}
 * bean names); a session picks one by name at creation time, falling back
 * to {@code booki.ai.default-provider} (itself profile-dependent — see
 * application.yml) when the session didn't specify one.
 */
@Component
public class AiProviderRegistry {

    private final Map<String, AiProvider> providers;
    private final String defaultProvider;

    public AiProviderRegistry(Map<String, AiProvider> providers,
                              @Value("${booki.ai.default-provider}") String defaultProvider) {
        this.providers = providers;
        this.defaultProvider = defaultProvider;
    }

    public Set<String> availableProviders() {
        return providers.keySet();
    }

    public String resolveName(String requested) {
        return (requested != null && providers.containsKey(requested)) ? requested : defaultProvider;
    }

    public AiProvider get(String requested) {
        return providers.get(resolveName(requested));
    }

    /** Whether the resolved provider can stream its reply natively (vs. the single-delta bridge below). */
    public boolean supportsStreaming(String requested) {
        return get(requested) instanceof StreamingAiProvider;
    }

    /**
     * One entry point for a streaming turn. If the resolved provider implements
     * {@link StreamingAiProvider} it streams natively; otherwise the blocking
     * {@link AiProvider#converse} result is delivered as a single delta. Either
     * way the {@link StreamingAiProvider.TokenStream} is terminated exactly once
     * and no provider is destabilised.
     */
    public void converseStreaming(String requested, String systemPrompt, List<AiProvider.Message> context,
                                  String userMessage, StreamingAiProvider.TokenStream stream) {
        AiProvider provider = get(requested);
        if (provider instanceof StreamingAiProvider streaming) {
            streaming.converseStream(systemPrompt, context, userMessage, stream);
            return;
        }
        try {
            String full = provider.converse(systemPrompt, context, userMessage);
            stream.onDelta(full);
            stream.onComplete(full);
        } catch (RuntimeException e) {
            stream.onError(e);
        }
    }
}

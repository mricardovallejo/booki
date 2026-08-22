package com.booki.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
}

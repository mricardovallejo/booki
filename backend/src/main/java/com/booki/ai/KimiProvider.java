package com.booki.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Moonshot's Kimi API is explicitly OpenAI-compatible — same wire format, different base URL/model. */
@Component("kimi")
public class KimiProvider extends OpenAiCompatibleProvider {

    public KimiProvider(@Value("${booki.ai.kimi.base-url}") String baseUrl,
                        @Value("${booki.ai.kimi.api-key}") String apiKey,
                        @Value("${booki.ai.kimi.model}") String model) {
        super(baseUrl, apiKey, model);
    }
}

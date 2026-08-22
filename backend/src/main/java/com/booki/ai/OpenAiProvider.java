package com.booki.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("openai")
public class OpenAiProvider extends OpenAiCompatibleProvider {

    public OpenAiProvider(@Value("${booki.ai.openai.api-key}") String apiKey,
                          @Value("${booki.ai.openai.model}") String model) {
        super("https://api.openai.com/v1", apiKey, model);
    }
}

package com.booki.conversation.capability;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityRegistryTest {

    private final CapabilityRegistry registry = new CapabilityRegistry(List.of(
            fake("quiz"), fake("summary")));

    @Test
    void recognisesABareDirectiveForAKnownCapability() {
        assertThat(registry.parseDirective("{\"capability\":\"quiz\"}")).contains("quiz");
        assertThat(registry.parseDirective("  {\"capability\": \"summary\"}  ")).contains("summary");
    }

    @Test
    void ignoresProseThatMerelyContainsJsonOrACapabilityWord() {
        assertThat(registry.parseDirective("Sure! Here is a recap: {\"points\": 3}")).isEmpty();
        assertThat(registry.parseDirective("I think you should take a quiz on this.")).isEmpty();
    }

    @Test
    void ignoresUnknownCapabilityMalformedJsonAndEmptyObject() {
        assertThat(registry.parseDirective("{\"capability\":\"translate\"}")).isEmpty();
        assertThat(registry.parseDirective("{\"capability\":}")).isEmpty();
        assertThat(registry.parseDirective("{}")).isEmpty();
        assertThat(registry.parseDirective(null)).isEmpty();
    }

    @Test
    void ignoresAnOversizedPayloadThatHappensToBeJson() {
        String big = "{\"capability\":\"quiz\",\"note\":\"" + "x".repeat(300) + "\"}";
        assertThat(registry.parseDirective(big)).isEmpty();
    }

    @Test
    void routerInstructionsListEveryCapability() {
        String instructions = registry.routerInstructions();
        assertThat(instructions).contains("quiz").contains("summary").contains("{\"capability\":\"<name>\"}");
    }

    @Test
    void routerInstructionsAreEmptyWhenNoCapabilitiesRegistered() {
        assertThat(new CapabilityRegistry(List.of()).routerInstructions()).isEmpty();
    }

    private static ConversationCapability fake(String name) {
        return new ConversationCapability() {
            @Override public String name() {
                return name;
            }

            @Override public String modelDescription() {
                return name + " — does " + name + " things";
            }

            @Override public String execute(CapabilityInvocation invocation) {
                return "";
            }
        };
    }
}

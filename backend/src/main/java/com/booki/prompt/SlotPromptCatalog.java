package com.booki.prompt;

import com.booki.domain.AiProfile;
import com.booki.domain.Capability;
import com.booki.domain.SlotKey;
import com.booki.domain.SlotPrompt;
import com.booki.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads and validates the fixed BooKI core and shipped AI Profile templates
 * from a versioned YAML resource. Java keeps the schema and domain keys typed;
 * prompt authors can edit the actual wording without changing source code.
 */
@Component
public class SlotPromptCatalog {

    public static final String DEFAULT_LOCATION = "classpath:prompts/catalog.yml";

    /** A shipped starting point. Not persisted — used only to seed and to restore user profiles. */
    public record Template(String key, String name, boolean isDefault,
                           EnumSet<Capability> capabilities, Map<SlotKey, String> texts) {
    }

    private final String version;
    private final String corePrompt;
    private final List<Template> templates;

    /** Default constructor is intentionally public for focused unit tests. */
    public SlotPromptCatalog() {
        this(DEFAULT_LOCATION);
    }

    @Autowired
    public SlotPromptCatalog(@Value("${booki.prompts.catalog:" + DEFAULT_LOCATION + "}") String location) {
        CatalogYaml yaml = load(location);
        this.version = required(yaml.getVersion(), "booki.prompts.version");
        this.corePrompt = required(yaml.getCore(), "booki.prompts.core");

        Map<SlotKey, String> shared = parseShared(yaml.getShared());
        List<Template> loaded = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        int defaults = 0;
        for (TemplateYaml source : yaml.getTemplates()) {
            String key = required(source.getKey(), "template key");
            if (!key.matches("[a-z0-9_]+") || !keys.add(key)) {
                throw invalid("Template keys must be unique snake_case values: " + key);
            }
            String name = required(source.getName(), "template name for " + key);
            String persona = required(source.getPersona(), "template persona for " + key);
            EnumSet<Capability> capabilities = parseCapabilities(source.getCapabilities(), key);
            Map<SlotKey, String> texts = new EnumMap<>(shared);
            texts.put(SlotKey.PERSONA, persona);
            requireEverySlot(texts, key);
            loaded.add(new Template(key, name, source.isDefaultProfile(), capabilities, Map.copyOf(texts)));
            defaults += source.isDefaultProfile() ? 1 : 0;
        }
        if (loaded.isEmpty() || defaults != 1) {
            throw invalid("The prompt catalog must define templates and exactly one default profile");
        }
        this.templates = List.copyOf(loaded);
    }

    public String version() {
        return version;
    }

    public String corePrompt() {
        return corePrompt;
    }

    public List<Template> templates() {
        return templates;
    }

    public Optional<Template> byKey(String key) {
        return templates.stream().filter(t -> t.key().equals(key)).findFirst();
    }

    /** One editable {@link AiProfile} (with all its SlotPrompts) per template, for a new user. */
    public List<AiProfile> seedFor(User user) {
        return templates.stream().map(t -> newProfile(t, user)).toList();
    }

    public AiProfile newProfile(Template t, User user) {
        AiProfile profile = new AiProfile();
        profile.setUser(user);
        profile.setName(t.name());
        profile.setBasedOnTemplate(t.key());
        profile.setDefaultProfile(t.isDefault());
        profile.setEnabledCapabilities(EnumSet.copyOf(t.capabilities()));
        for (SlotKey key : SlotKey.values()) {
            profile.addSlot(new SlotPrompt(key, t.texts().getOrDefault(key, "")));
        }
        return profile;
    }

    /** Reset an existing profile's editable fields back to its template. */
    public void restore(AiProfile profile) {
        Template t = byKey(profile.getBasedOnTemplate()).orElseThrow(
                () -> new IllegalArgumentException("This profile has no original template to restore from."));
        profile.setEnabledCapabilities(EnumSet.copyOf(t.capabilities()));
        for (SlotPrompt slot : profile.getSlots()) {
            String text = t.texts().getOrDefault(slot.getSlot(), "");
            slot.setText(text);
            slot.setOriginalText(text);
        }
    }

    private static CatalogYaml load(String location) {
        Resource resource = new DefaultResourceLoader().getResource(location);
        if (!resource.exists()) {
            throw invalid("Prompt catalog not found: " + location);
        }
        try {
            List<PropertySource<?>> loaded = new YamlPropertySourceLoader().load("booki-prompt-catalog", resource);
            MutablePropertySources sources = new MutablePropertySources();
            loaded.forEach(sources::addLast);
            return new Binder(ConfigurationPropertySources.from(sources))
                    .bind("booki.prompts", Bindable.of(CatalogYaml.class))
                    .orElseThrow(() -> invalid("Missing booki.prompts in " + location));
        } catch (IOException e) {
            throw invalid("Cannot read prompt catalog " + location, e);
        }
    }

    private static Map<SlotKey, String> parseShared(Map<String, String> raw) {
        Map<SlotKey, String> result = new EnumMap<>(SlotKey.class);
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            SlotKey key;
            try {
                key = SlotKey.ofWire(entry.getKey());
            } catch (IllegalArgumentException e) {
                throw invalid("Unknown shared prompt slot: " + entry.getKey(), e);
            }
            if (key == SlotKey.PERSONA) {
                throw invalid("persona belongs to each template, not the shared prompt map");
            }
            result.put(key, required(entry.getValue(), "shared prompt " + entry.getKey()));
        }
        return result;
    }

    private static EnumSet<Capability> parseCapabilities(List<String> raw, String templateKey) {
        EnumSet<Capability> result = EnumSet.noneOf(Capability.class);
        for (String wire : raw) {
            try {
                result.add(Capability.ofWire(wire));
            } catch (IllegalArgumentException e) {
                throw invalid("Unknown capability '" + wire + "' in template " + templateKey, e);
            }
        }
        return result;
    }

    private static void requireEverySlot(Map<SlotKey, String> texts, String templateKey) {
        for (SlotKey key : SlotKey.values()) {
            if (!texts.containsKey(key) || texts.get(key).isBlank()) {
                throw invalid("Template " + templateKey + " has no prompt for " + key.wire());
            }
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid("Missing or blank " + field);
        }
        return value.strip();
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("Invalid prompt catalog: " + message);
    }

    private static IllegalStateException invalid(String message, Exception cause) {
        return new IllegalStateException("Invalid prompt catalog: " + message, cause);
    }

    /** Mutable binding model used only while loading the YAML resource. */
    public static class CatalogYaml {
        private String version;
        private String core;
        private Map<String, String> shared = new LinkedHashMap<>();
        private List<TemplateYaml> templates = new ArrayList<>();

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getCore() { return core; }
        public void setCore(String core) { this.core = core; }
        public Map<String, String> getShared() { return shared; }
        public void setShared(Map<String, String> shared) { this.shared = shared; }
        public List<TemplateYaml> getTemplates() { return templates; }
        public void setTemplates(List<TemplateYaml> templates) { this.templates = templates; }
    }

    public static class TemplateYaml {
        private String key;
        private String name;
        private boolean defaultProfile;
        private List<String> capabilities = new ArrayList<>();
        private String persona;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isDefaultProfile() { return defaultProfile; }
        public void setDefaultProfile(boolean defaultProfile) { this.defaultProfile = defaultProfile; }
        public List<String> getCapabilities() { return capabilities; }
        public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities; }
        public String getPersona() { return persona; }
        public void setPersona(String persona) { this.persona = persona; }
    }
}

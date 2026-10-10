package com.booki.prompt;

import com.booki.domain.AiProfile;
import com.booki.domain.AppProfileSlot;
import com.booki.domain.AppProfileSlotKey;
import com.booki.domain.Capability;
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
 * Loads and validates the fixed BooKI core, the account-wide App Profile
 * defaults, and the shipped tutor-persona templates from a versioned YAML
 * resource. Java keeps the schema and domain keys typed; prompt authors can
 * edit the actual wording without changing source code.
 */
@Component
public class SlotPromptCatalog {

    public static final String DEFAULT_LOCATION = "classpath:prompts/catalog.yml";

    /** The three difficulty levels, account-wide — not a per-tutor-profile concept. */
    public static final List<String> DIFFICULTY_LEVELS = List.of("easy", "medium", "hard");

    /** A shipped tutor-persona starting point. Not persisted — used only to seed and to restore user profiles. */
    public record Template(String key, String name, boolean isDefault, String personaText) {
    }

    private final String version;
    private final String corePrompt;
    private final List<Template> templates;
    private final Map<AppProfileSlotKey, String> appProfileDefaults;
    private final EnumSet<Capability> defaultCapabilities;

    /** Default constructor is intentionally public for focused unit tests. */
    public SlotPromptCatalog() {
        this(DEFAULT_LOCATION);
    }

    @Autowired
    public SlotPromptCatalog(@Value("${booki.prompts.catalog:" + DEFAULT_LOCATION + "}") String location) {
        CatalogYaml yaml = load(location);
        this.version = required(yaml.getVersion(), "booki.prompts.version");
        this.corePrompt = required(yaml.getCore(), "booki.prompts.core");

        Map<AppProfileSlotKey, String> defaults = new EnumMap<>(AppProfileSlotKey.class);
        parseInto(defaults, yaml.getDifficulty(), "difficulty rubric");
        parseInto(defaults, yaml.getShared(), "shared prompt");
        requireEveryAppProfileSlot(defaults);
        this.appProfileDefaults = Map.copyOf(defaults);
        this.defaultCapabilities = parseCapabilities(yaml.getDefaultCapabilities(), "default-capabilities");

        List<Template> loaded = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        int defaultCount = 0;
        for (TemplateYaml source : yaml.getTemplates()) {
            String key = required(source.getKey(), "template key");
            if (!key.matches("[a-z0-9_]+") || !keys.add(key)) {
                throw invalid("Template keys must be unique snake_case values: " + key);
            }
            String name = required(source.getName(), "template name for " + key);
            String persona = required(source.getPersona(), "template persona for " + key);
            loaded.add(new Template(key, name, source.isDefaultProfile(), persona));
            defaultCount += source.isDefaultProfile() ? 1 : 0;
        }
        if (loaded.isEmpty() || defaultCount != 1) {
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

    /** The shipped default text for each App Profile slot — account-wide, not per tutor profile. */
    public Map<AppProfileSlotKey, String> appProfileDefaults() {
        return appProfileDefaults;
    }

    /** The shipped default enabled-capability set for a new account. */
    public EnumSet<Capability> defaultCapabilities() {
        return EnumSet.copyOf(defaultCapabilities);
    }

    /** One editable {@link AppProfileSlot} row per key, for a new user. */
    public List<AppProfileSlot> newAppProfileSlots(User user) {
        return List.of(AppProfileSlotKey.values()).stream()
                .map(key -> new AppProfileSlot(user, key, appProfileDefaults.getOrDefault(key, "")))
                .toList();
    }

    /** One editable {@link AiProfile} per template, for a new user. */
    public List<AiProfile> seedFor(User user) {
        return templates.stream().map(t -> newProfile(t, user)).toList();
    }

    public AiProfile newProfile(Template t, User user) {
        AiProfile profile = new AiProfile();
        profile.setUser(user);
        profile.setName(t.name());
        profile.setBasedOnTemplate(t.key());
        profile.setDefaultProfile(t.isDefault());
        profile.setPersonaText(t.personaText());
        profile.setPersonaOriginalText(t.personaText());
        return profile;
    }

    /** Reset an existing profile's editable fields back to its template. */
    public void restore(AiProfile profile) {
        Template t = byKey(profile.getBasedOnTemplate()).orElseThrow(
                () -> new IllegalArgumentException("This profile has no original template to restore from."));
        profile.setPersonaText(t.personaText());
        profile.setPersonaOriginalText(t.personaText());
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

    private static void parseInto(Map<AppProfileSlotKey, String> target, Map<String, String> raw, String fieldLabel) {
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            AppProfileSlotKey key;
            try {
                key = AppProfileSlotKey.ofWire(entry.getKey());
            } catch (IllegalArgumentException e) {
                throw invalid("Unknown " + fieldLabel + " slot: " + entry.getKey(), e);
            }
            target.put(key, required(entry.getValue(), fieldLabel + " " + entry.getKey()));
        }
    }

    private static EnumSet<Capability> parseCapabilities(List<String> raw, String fieldLabel) {
        EnumSet<Capability> result = EnumSet.noneOf(Capability.class);
        for (String wire : raw) {
            try {
                result.add(Capability.ofWire(wire));
            } catch (IllegalArgumentException e) {
                throw invalid("Unknown capability '" + wire + "' in " + fieldLabel, e);
            }
        }
        return result;
    }

    private static void requireEveryAppProfileSlot(Map<AppProfileSlotKey, String> defaults) {
        for (AppProfileSlotKey key : AppProfileSlotKey.values()) {
            if (!defaults.containsKey(key) || defaults.get(key).isBlank()) {
                throw invalid("App Profile catalog has no prompt for " + key.wire());
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
        private Map<String, String> difficulty = new LinkedHashMap<>();
        private Map<String, String> shared = new LinkedHashMap<>();
        private List<String> defaultCapabilities = new ArrayList<>();
        private List<TemplateYaml> templates = new ArrayList<>();

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getCore() { return core; }
        public void setCore(String core) { this.core = core; }
        public Map<String, String> getDifficulty() { return difficulty; }
        public void setDifficulty(Map<String, String> difficulty) { this.difficulty = difficulty; }
        public Map<String, String> getShared() { return shared; }
        public void setShared(Map<String, String> shared) { this.shared = shared; }
        public List<String> getDefaultCapabilities() { return defaultCapabilities; }
        public void setDefaultCapabilities(List<String> defaultCapabilities) { this.defaultCapabilities = defaultCapabilities; }
        public List<TemplateYaml> getTemplates() { return templates; }
        public void setTemplates(List<TemplateYaml> templates) { this.templates = templates; }
    }

    public static class TemplateYaml {
        private String key;
        private String name;
        private boolean defaultProfile;
        private String persona;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isDefaultProfile() { return defaultProfile; }
        public void setDefaultProfile(boolean defaultProfile) { this.defaultProfile = defaultProfile; }
        public String getPersona() { return persona; }
        public void setPersona(String persona) { this.persona = persona; }
    }
}

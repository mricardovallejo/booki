package com.booki.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Stores {@code Set<Capability>} as a sorted comma-separated string (e.g. {@code "explain,quiz"}). */
@Converter
public class CapabilitySetConverter implements AttributeConverter<Set<Capability>, String> {

    @Override
    public String convertToDatabaseColumn(Set<Capability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return "";
        }
        return capabilities.stream().sorted().map(Capability::wire).collect(Collectors.joining(","));
    }

    @Override
    public Set<Capability> convertToEntityAttribute(String csv) {
        Set<Capability> out = EnumSet.noneOf(Capability.class);
        if (csv == null || csv.isBlank()) {
            return out;
        }
        Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(s -> out.add(Capability.ofWire(s)));
        return out;
    }
}

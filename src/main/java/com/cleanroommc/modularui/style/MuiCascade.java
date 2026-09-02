package com.cleanroommc.modularui.style;

import com.cleanroommc.modularui.api.dom.MuiElement;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic stylesheet cascade with specificity and variable resolution. */
public final class MuiCascade {
    private final List<MuiStyleRule> rules;
    private final Map<String, JsonElement> variables;

    MuiCascade(List<MuiStyleRule> rules, Map<String, JsonElement> variables) {
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
        this.variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }

    public List<MuiStyleRule> getRules() { return this.rules; }
    public Map<String, JsonElement> getVariables() { return this.variables; }

    public MuiComputedStyle compute(MuiElement element) {
        return compute(element, Collections.<String, JsonElement>emptyMap(), MuiMediaEnvironment.UNCONSTRAINED);
    }

    public MuiComputedStyle compute(MuiElement element, Map<String, JsonElement> inline) {
        return compute(element, inline, MuiMediaEnvironment.UNCONSTRAINED);
    }

    public MuiComputedStyle compute(MuiElement element, MuiMediaEnvironment environment) {
        return compute(element, Collections.<String, JsonElement>emptyMap(), environment);
    }

    public MuiComputedStyle compute(MuiElement element, Map<String, JsonElement> inline, MuiMediaEnvironment environment) {
        if (element == null) throw new NullPointerException("element");
        Map<String, Winner> winners = new LinkedHashMap<>();
        for (MuiStyleRule rule : rules) {
            if (!rule.getMedia().matches(environment) || !rule.getSelector().matches(element)) continue;
            for (Map.Entry<String, JsonElement> declaration : rule.getDeclarations().entrySet()) {
                String name = normalize(declaration.getKey());
                Winner candidate = new Winner(declaration.getValue(), rule.getSelector().getSpecificity(), rule.getOrder());
                Winner current = winners.get(name);
                if (current == null || candidate.compareTo(current) >= 0) winners.put(name, candidate);
            }
        }
        int inlineOrder = Integer.MAX_VALUE;
        for (Map.Entry<String, JsonElement> declaration : inline.entrySet()) {
            winners.put(normalize(declaration.getKey()), new Winner(declaration.getValue(), 1000, inlineOrder++));
        }
        Map<String, JsonElement> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Winner> entry : winners.entrySet()) {
            resolved.put(entry.getKey(), resolve(entry.getValue().value, 0));
        }
        return new MuiComputedStyle(resolved);
    }

    private JsonElement resolve(JsonElement value, int depth) {
        if (depth > 16 || value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return value;
        String text = value.getAsString().trim();
        if (text.startsWith("var(") && text.endsWith(")")) {
            String key = text.substring(4, text.length() - 1).trim();
            JsonElement variable = variables.get(key);
            if (variable != null) return resolve(variable, depth + 1);
        }
        if (text.contains("var(")) {
            String result = text;
            for (Map.Entry<String, JsonElement> variable : variables.entrySet()) {
                String token = "var(" + variable.getKey() + ")";
                if (result.contains(token) && variable.getValue().isJsonPrimitive()) {
                    result = result.replace(token, variable.getValue().getAsString());
                }
            }
            return new JsonPrimitive(result);
        }
        return value;
    }

    private static String normalize(String value) { return value.trim().toLowerCase(java.util.Locale.ROOT); }

    private static final class Winner implements Comparable<Winner> {
        private final JsonElement value;
        private final int specificity;
        private final int order;
        private Winner(JsonElement value, int specificity, int order) {
            this.value = value;
            this.specificity = specificity;
            this.order = order;
        }
        @Override public int compareTo(Winner other) {
            int specificityOrder = Integer.compare(this.specificity, other.specificity);
            return specificityOrder != 0 ? specificityOrder : Integer.compare(this.order, other.order);
        }
    }
}

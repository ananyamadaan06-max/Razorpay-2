package com.razorpay.featureflags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.razorpay.featureflags.Condition.Operator.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigStoreTest {
    static Stream<Arguments> flagValues() {
        return Stream.of(Arguments.of(FlagType.BOOLEAN, false, true),
                Arguments.of(FlagType.STRING, "off", "on"), Arguments.of(FlagType.INTEGER, 0, 10));
    }

    @ParameterizedTest
    @MethodSource("flagValues")
    void storesAllTypesAndIsolatesEnvironments(FlagType type, Object defaultValue, Object enabledValue) {
        var store = new InMemoryConfigStore();
        var dev = new FlagConfig("flag", type, defaultValue, List.of(), null);
        var prod = new FlagConfig("flag", type, enabledValue, List.of(), null);
        store.set("dev", dev);
        store.set("prod", prod);
        assertSame(dev, store.get("dev", "flag"));
        assertSame(prod, store.get("prod", "flag"));
        assertNull(store.get("staging", "flag"));
        assertNull(store.get("dev", "missing"));
    }

    @ParameterizedTest
    @MethodSource("flagValues")
    void rejectsWrongDefaultRuleAndRolloutTypes(FlagType type, Object defaultValue, Object enabledValue) {
        Object wrongValue = type == FlagType.STRING ? 1 : "wrong";
        var rule = new TargetingRule(new Condition("tier", EQUALS, "pro"), 1, wrongValue);
        assertThrows(IllegalArgumentException.class,
                () -> new FlagConfig("flag", type, wrongValue, List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new FlagConfig("flag", type, defaultValue, List.of(rule), null));
        assertThrows(IllegalArgumentException.class,
                () -> new FlagConfig("flag", type, defaultValue, List.of(),
                        new RolloutConfig(50, wrongValue, null)));
        assertThrows(IllegalArgumentException.class,
                () -> new FlagConfig("flag", type, null, List.of(), null));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 100.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidPercentages(double percentage) {
        assertThrows(IllegalArgumentException.class, () -> new RolloutConfig(percentage, true, null));
    }

    @Test
    void rejectsInvalidNamesAndConditions() {
        var store = new InMemoryConfigStore();
        var config = new FlagConfig("flag", FlagType.BOOLEAN, false, List.of(), null);
        assertThrows(IllegalArgumentException.class, () -> store.set(" ", config));
        assertThrows(IllegalArgumentException.class, () -> store.get(null, "flag"));
        assertThrows(IllegalArgumentException.class, () -> store.get("dev", ""));
        assertThrows(NullPointerException.class, () -> store.set("dev", null));
        assertThrows(IllegalArgumentException.class,
                () -> new FlagConfig("", FlagType.BOOLEAN, false, List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new RolloutConfig(50, true, " "));
        assertThrows(IllegalArgumentException.class, () -> new Condition("", EQUALS, "pro"));
        assertThrows(IllegalArgumentException.class, () -> new Condition("tier", IN, "pro"));
        assertThrows(IllegalArgumentException.class, () -> new Condition("tier", IN, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Condition("tier", EQUALS, List.of("pro")));
        assertThrows(IllegalArgumentException.class, () -> new Condition("tier", IN, List.of(1.5)));
        assertThrows(IllegalArgumentException.class,
                () -> new EvaluationContext("tenant", "user", Map.of("mutable", new ArrayList<>())));
    }

    @Test
    void copiesMutableInputsAndOrdersRulesWithStableTies() {
        var tiers = new ArrayList<>(List.of("pro"));
        var condition = new Condition("tier", IN, tiers);
        var first = new TargetingRule(condition, 1, "first");
        var second = new TargetingRule(condition, 1, "second");
        var last = new TargetingRule(condition, 10, "last");
        var rules = new ArrayList<>(List.of(last, first, second));
        var config = new FlagConfig("flag", FlagType.STRING, "default", rules, null);
        var attributes = new HashMap<String, Object>(Map.of("tier", "pro"));
        var context = new EvaluationContext("tenant", "user", attributes);
        tiers.clear();
        rules.clear();
        attributes.clear();
        assertEquals(List.of(first, second, last), config.rules());
        assertTrue(condition.matches(context.attributes()));
        assertThrows(UnsupportedOperationException.class, () -> config.rules().clear());
        assertThrows(UnsupportedOperationException.class, () -> context.attributes().clear());
    }
}

package com.razorpay.featureflags;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static com.razorpay.featureflags.Condition.Operator.*;
import static org.junit.jupiter.api.Assertions.*;

class FlagEvaluatorTest {
    private final InMemoryConfigStore store = new InMemoryConfigStore();
    private final FlagEvaluator evaluator = new FlagEvaluator(store);

    static Stream<Arguments> values() {
        return Stream.of(Arguments.of(FlagType.BOOLEAN, Boolean.class, false, true),
                Arguments.of(FlagType.STRING, String.class, "default", "enabled"),
                Arguments.of(FlagType.INTEGER, Integer.class, 0, 42));
    }

    @ParameterizedTest
    @MethodSource("values")
    <T> void targetsAndDefaultsForAllTypes(FlagType type, Class<T> valueType, T defaultValue, T enabled) {
        var rule = new TargetingRule(new Condition("tier", EQUALS, "pro"), 1, enabled);
        store.set("prod", new FlagConfig("flag", type, defaultValue, List.of(rule), null));
        assertEquals(enabled, evaluator.evaluate("prod", "flag", context(Map.of("tier", "pro")),
                valueType, defaultValue));
        assertEquals(defaultValue, evaluator.evaluate("prod", "flag", context(Map.of("tier", "free")),
                valueType, enabled));
        assertEquals(defaultValue, evaluator.evaluate("prod", "flag", context(Map.of()), valueType, enabled));
        assertEquals(enabled, evaluator.evaluate("prod", "missing", context(Map.of()), valueType, enabled));
    }

    @Test
    void firstMatchingRuleWinsBeforeRolloutAndTiesPreserveInputOrder() {
        var condition = new Condition("tier", IN, List.of("pro", "enterprise"));
        store.set("prod", new FlagConfig("flag", FlagType.STRING, "default", List.of(
                new TargetingRule(condition, 5, "later"),
                new TargetingRule(new Condition("country", EQUALS, "US"), 0, "miss"),
                new TargetingRule(condition, 1, "first"),
                new TargetingRule(condition, 1, "tie")), new RolloutConfig(100, "rollout", null)));
        assertEquals("first", evaluator.evaluate("prod", "flag", context(Map.of("tier", "pro")),
                String.class, "fallback"));
        assertEquals("rollout", evaluator.evaluate("prod", "flag", context(Map.of("tier", "free")),
                String.class, "fallback"));
    }

    @Test
    void conditionsUseExactTypesAndDoNotMatchMissingAttributes() {
        assertTrue(new Condition("age", EQUALS, 20).matches(Map.of("age", 20)));
        assertFalse(new Condition("age", EQUALS, 20).matches(Map.of("age", "20")));
        assertTrue(new Condition("staff", IN, List.of(true)).matches(Map.of("staff", true)));
        assertFalse(new Condition("staff", IN, List.of(false)).matches(Map.of()));
    }

    @Test
    void targetingWorksWithoutRolloutIdentity() {
        store.set("prod", new FlagConfig("flag", FlagType.BOOLEAN, false, List.of(
                new TargetingRule(new Condition("staff", EQUALS, true), 1, true)),
                new RolloutConfig(100, true, null)));
        assertTrue(evaluator.evaluate("prod", "flag", new EvaluationContext(null, null,
                Map.of("staff", true)), Boolean.class, false));
        for (var context : List.of(new EvaluationContext(null, "user", Map.of()),
                new EvaluationContext("tenant", null, Map.of()),
                new EvaluationContext(" ", "user", Map.of()),
                new EvaluationContext("tenant", "", Map.of()))) {
            assertFalse(evaluator.evaluate("prod", "flag", context, Boolean.class, true));
        }
    }

    @Test
    void wrongRequestedTypeReturnsCallerFallback() {
        store.set("prod", new FlagConfig("flag", FlagType.BOOLEAN, false, List.of(), null));
        assertEquals("fallback", evaluator.evaluate("prod", "flag", context(Map.of()),
                String.class, "fallback"));
        assertNull(evaluator.evaluate("prod", "missing", context(Map.of()), String.class, null));
    }

    static EvaluationContext context(Map<String, Object> attributes) {
        return new EvaluationContext("tenant", "user", attributes);
    }
}

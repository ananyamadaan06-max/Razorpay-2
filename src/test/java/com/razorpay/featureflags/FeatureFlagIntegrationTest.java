package com.razorpay.featureflags;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.razorpay.featureflags.Condition.Operator.IN;
import static org.junit.jupiter.api.Assertions.*;

class FeatureFlagIntegrationTest {
    @Test
    void targetingSharedRolloutsEnvironmentIsolationAndLiveUpdatesWorkTogether() {
        var store = new InMemoryConfigStore();
        var evaluator = new FlagEvaluator(store);
        store.set("prod", new FlagConfig("checkout", FlagType.BOOLEAN, false, List.of(
                new TargetingRule(new Condition("tier", IN, List.of("pro", "staff")), 1, true)),
                new RolloutConfig(50, true, "checkout")));
        store.set("prod", new FlagConfig("label", FlagType.STRING, "classic", List.of(),
                new RolloutConfig(50, "preview", "checkout")));
        store.set("prod", new FlagConfig("limit", FlagType.INTEGER, 2, List.of(),
                new RolloutConfig(100, 5, null)));
        store.set("dev", new FlagConfig("checkout", FlagType.BOOLEAN, false, List.of(), null));
        var pro = new EvaluationContext("tenant", "pro-user", Map.of("tier", "pro"));
        assertTrue(evaluator.evaluate("prod", "checkout", pro, Boolean.class, false));
        assertFalse(evaluator.evaluate("dev", "checkout", pro, Boolean.class, true));
        assertTrue(evaluator.evaluate("staging", "checkout", pro, Boolean.class, true));
        for (int i = 0; i < 100; i++) {
            var free = new EvaluationContext("tenant", "user-" + i, Map.of("tier", "free"));
            boolean selected = evaluator.evaluate("prod", "checkout", free, Boolean.class, false);
            assertEquals(selected ? "preview" : "classic",
                    evaluator.evaluate("prod", "label", free, String.class, "fallback"));
            assertEquals(5, evaluator.evaluate("prod", "limit", free, Integer.class, 0));
        }
        var anonymous = new EvaluationContext(null, null, Map.of());
        assertEquals(2, evaluator.evaluate("prod", "limit", anonymous, Integer.class, 0));
        store.set("prod", new FlagConfig("checkout", FlagType.BOOLEAN, false, List.of(), null));
        assertFalse(evaluator.evaluate("prod", "checkout", pro, Boolean.class, true));
        assertEquals("fallback", evaluator.evaluate("prod", "missing", pro, String.class, "fallback"));
    }
}

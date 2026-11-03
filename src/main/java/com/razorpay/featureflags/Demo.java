package com.razorpay.featureflags;

import java.util.List;
import java.util.Map;

import static com.razorpay.featureflags.Condition.Operator.EQUALS;

public final class Demo {
    public static void main(String[] args) {
        var store = new InMemoryConfigStore();
        var evaluator = new FlagEvaluator(store);
        store.set("prod", new FlagConfig("checkout", FlagType.BOOLEAN, false, List.of(
                new TargetingRule(new Condition("tier", EQUALS, "pro"), 1, true)),
                new RolloutConfig(50, true, "checkout-experiment")));
        store.set("prod", new FlagConfig("checkout-label", FlagType.STRING, "classic", List.of(),
                new RolloutConfig(50, "preview", "checkout-experiment")));
        store.set("prod", new FlagConfig("retry-limit", FlagType.INTEGER, 2, List.of(),
                new RolloutConfig(100, 5, null)));
        store.set("dev", new FlagConfig("checkout", FlagType.BOOLEAN, false, List.of(), null));

        var pro = new EvaluationContext("tenant-1", "user-1", Map.of("tier", "pro"));
        var free = new EvaluationContext("tenant-1", "user-1", Map.of("tier", "free"));
        System.out.println("Targeted pro user: "
                + evaluator.evaluate("prod", "checkout", pro, Boolean.class, false));
        System.out.println("Free user shared rollout: checkout="
                + evaluator.evaluate("prod", "checkout", free, Boolean.class, false)
                + ", label=" + evaluator.evaluate("prod", "checkout-label", free, String.class, "classic"));
        System.out.println("Integer rollout: "
                + evaluator.evaluate("prod", "retry-limit", free, Integer.class, 2));
        System.out.println("Dev isolation: "
                + evaluator.evaluate("dev", "checkout", pro, Boolean.class, false));
        System.out.println("Missing flag fallback: "
                + evaluator.evaluate("prod", "missing", free, String.class, "fallback"));

        store.set("prod", new FlagConfig("checkout", FlagType.BOOLEAN, true, List.of(), null));
        System.out.println("After live update: "
                + evaluator.evaluate("prod", "checkout", free, Boolean.class, false));
    }
}

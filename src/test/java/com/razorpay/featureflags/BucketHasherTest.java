package com.razorpay.featureflags;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BucketHasherTest {
    private final BucketHasher hasher = new BucketHasher();

    @Test
    void hashEncodingMatchesFixedReferenceVectors() {
        assertEquals(3379, hasher.bucket("prod", "tenant", "user", "checkout", null));
        assertEquals(9235, hasher.bucket("prod", "tenant", "user", "ignored", "checkout"));
    }

    @Test
    void bucketsAreStickyAndSharedGroupsIgnoreFlagName() {
        for (int i = 0; i < 100; i++) {
            String user = "user-" + i;
            int bucket = hasher.bucket("prod", "tenant", user, "flag-a", null);
            assertTrue(bucket >= 0 && bucket < BucketHasher.BUCKETS);
            assertEquals(bucket, new BucketHasher().bucket("prod", "tenant", user, "flag-a", null));
            assertEquals(hasher.bucket("prod", "tenant", user, "flag-a", "experiment"),
                    hasher.bucket("prod", "tenant", user, "flag-b", "experiment"));
        }
    }

    @Test
    void keysSeparateEnvironmentsTenantsFlagsAndGroups() {
        int baseline = hasher.bucket("prod", "tenant", "user", "flag", null);
        assertNotEquals(baseline, hasher.bucket("dev", "tenant", "user", "flag", null));
        assertNotEquals(baseline, hasher.bucket("prod", "other", "user", "flag", null));
        assertNotEquals(baseline, hasher.bucket("prod", "tenant", "user", "other", null));
        assertNotEquals(baseline, hasher.bucket("prod", "tenant", "user", "flag", "flag"));
        assertNotEquals(hasher.bucket("ab", "c", "user", "flag", null),
                hasher.bucket("a", "bc", "user", "flag", null));
    }

    @Test
    void fiftyPercentDistributionIsBalancedAndIndependent() {
        long selected = IntStream.range(0, 20_000).filter(i ->
                hasher.bucket("prod", "tenant", "user-" + i, "a", null) < 5000).count();
        long differing = IntStream.range(0, 20_000).filter(i ->
                (hasher.bucket("prod", "tenant", "user-" + i, "a", null) < 5000)
                        != (hasher.bucket("prod", "tenant", "user-" + i, "b", null) < 5000)).count();
        assertTrue(selected > 9600 && selected < 10400, "Selected: " + selected);
        assertTrue(differing > 9600 && differing < 10400, "Differing: " + differing);
    }

    @Test
    void rolloutBoundariesAndBucketThresholdMatchEvaluation() {
        var store = new InMemoryConfigStore();
        var evaluator = new FlagEvaluator(store);
        for (double percentage : new double[]{0, 50, 100}) {
            store.set("prod", new FlagConfig("flag", FlagType.BOOLEAN, false, List.of(),
                    new RolloutConfig(percentage, true, null)));
            for (int i = 0; i < 100; i++) {
                var context = new EvaluationContext("tenant", "user-" + i, Map.of());
                boolean expected = hasher.bucket("prod", "tenant", context.userId(), "flag", null)
                        < percentage * 100;
                assertEquals(expected, evaluator.evaluate("prod", "flag", context, Boolean.class, false));
            }
        }
        assertEquals(1, new RolloutConfig(0.01, true, null).bucketCount());
        assertEquals(13, new RolloutConfig(0.125, true, null).bucketCount());
    }

    @Test
    void sharedRolloutsSelectTheSameUsersAndIncreasingPercentageKeepsSelectedUsers() {
        var store = new InMemoryConfigStore();
        var evaluator = new FlagEvaluator(store);
        store.set("prod", new FlagConfig("a", FlagType.BOOLEAN, false, List.of(),
                new RolloutConfig(25, true, "checkout")));
        store.set("prod", new FlagConfig("b", FlagType.BOOLEAN, false, List.of(),
                new RolloutConfig(50, true, "checkout")));
        store.set("prod", new FlagConfig("c", FlagType.BOOLEAN, false, List.of(),
                new RolloutConfig(25, true, "checkout")));
        for (int i = 0; i < 1000; i++) {
            var context = new EvaluationContext("tenant", "user-" + i, Map.of());
            boolean a = evaluator.evaluate("prod", "a", context, Boolean.class, false);
            assertEquals(a, evaluator.evaluate("prod", "c", context, Boolean.class, false));
            if (a) {
                assertTrue(evaluator.evaluate("prod", "b", context, Boolean.class, false));
            }
        }
    }
}

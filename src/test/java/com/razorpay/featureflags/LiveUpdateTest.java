package com.razorpay.featureflags;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import static com.razorpay.featureflags.Condition.Operator.EQUALS;
import static org.junit.jupiter.api.Assertions.*;

class LiveUpdateTest {
    @Test
    void anUpdateAfterReadingTheSnapshotAppliesToTheNextEvaluation() {
        var store = new InMemoryConfigStore();
        store.set("prod", new FlagConfig("flag", FlagType.INTEGER, 1, List.of(), null));
        var replacement = new FlagConfig("flag", FlagType.INTEGER, 2, List.of(), null);
        ConfigStore updatingStore = new ConfigStore() {
            public void set(String environment, FlagConfig config) {
                store.set(environment, config);
            }

            public FlagConfig get(String environment, String flagName) {
                var snapshot = store.get(environment, flagName);
                store.set(environment, replacement);
                return snapshot;
            }
        };
        var evaluator = new FlagEvaluator(updatingStore);
        var context = new EvaluationContext("tenant", "user", Map.of());
        assertEquals(1, evaluator.evaluate("prod", "flag", context, Integer.class, -1));
        assertEquals(2, evaluator.evaluate("prod", "flag", context, Integer.class, -1));
    }

    @Test
    void replacementIsVisibleOnTheNextEvaluationAndInvalidUpdatesPreserveTheOldConfig() {
        var store = new InMemoryConfigStore();
        var evaluator = new FlagEvaluator(store);
        var context = new EvaluationContext("tenant", "user", Map.of());
        var original = new FlagConfig("flag", FlagType.INTEGER, 1, List.of(), null);
        store.set("prod", original);
        assertEquals(1, evaluator.evaluate("prod", "flag", context, Integer.class, -1));
        assertThrows(IllegalArgumentException.class, () -> store.set("prod",
                new FlagConfig("flag", FlagType.INTEGER, "wrong", List.of(), null)));
        assertSame(original, store.get("prod", "flag"));
        assertThrows(IllegalArgumentException.class, () -> store.set("prod",
                new FlagConfig("flag", FlagType.INTEGER, 2, List.of(), new RolloutConfig(101, 3, null))));
        assertSame(original, store.get("prod", "flag"));
        store.set("prod", new FlagConfig("flag", FlagType.INTEGER, 2, List.of(), null));
        assertEquals(1, original.defaultValue());
        assertEquals(2, evaluator.evaluate("prod", "flag", context, Integer.class, -1));
    }

    @Test
    void evaluationErrorsReturnFlagDefaultAndLogStructuredContext() {
        var store = new InMemoryConfigStore();
        store.set("prod", new FlagConfig("flag", FlagType.BOOLEAN, true, List.of(), null));
        var logger = (Logger) LoggerFactory.getLogger(FlagEvaluator.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertTrue(new FlagEvaluator(store).evaluate("prod", "flag", null, Boolean.class, false));
            var event = appender.list.get(0);
            assertEquals("ERROR", event.getLevel().toString());
            assertNotNull(event.getThrowableProxy());
            assertTrue(event.getKeyValuePairs().stream().anyMatch(pair ->
                    pair.key.equals("event") && pair.value.equals("flag_evaluation_error")));
            assertTrue(event.getKeyValuePairs().stream().anyMatch(pair ->
                    pair.key.equals("environment") && pair.value.equals("prod")));
            assertTrue(event.getKeyValuePairs().stream().anyMatch(pair ->
                    pair.key.equals("flag") && pair.value.equals("flag")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void storeFailureReturnsCallerFallbackWhenNoDefaultCanBeRead() {
        ConfigStore failingStore = new ConfigStore() {
            public void set(String environment, FlagConfig config) {
                throw new UnsupportedOperationException();
            }

            public FlagConfig get(String environment, String flagName) {
                throw new IllegalStateException("Store unavailable");
            }
        };
        assertEquals(7, new FlagEvaluator(failingStore).evaluate("prod", "flag",
                new EvaluationContext("tenant", "user", Map.of()), Integer.class, 7));
    }

    @Test
    @Timeout(15)
    void concurrentReadersAndWritersObserveCompleteSnapshots() throws Exception {
        var store = new InMemoryConfigStore();
        var evaluator = new FlagEvaluator(store);
        var oldConfig = new FlagConfig("flag", FlagType.STRING, "old", List.of(), null);
        var newConfig = new FlagConfig("flag", FlagType.STRING, "unreachable", List.of(
                new TargetingRule(new Condition("staff", EQUALS, true), 1, "new")), null);
        var context = new EvaluationContext("tenant", "user", Map.of("staff", true));
        store.set("prod", oldConfig);
        var executor = Executors.newFixedThreadPool(6);
        var start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int writer = 0; writer < 2; writer++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 5000; i++) {
                        store.set("prod", i % 2 == 0 ? oldConfig : newConfig);
                    }
                    return null;
                }));
            }
            for (int reader = 0; reader < 4; reader++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 5000; i++) {
                        String result = evaluator.evaluate("prod", "flag", context, String.class, "fallback");
                        assertTrue(result.equals("old") || result.equals("new"), result);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var task : tasks) {
                task.get();
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}

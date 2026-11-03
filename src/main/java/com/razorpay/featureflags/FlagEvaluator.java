package com.razorpay.featureflags;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlagEvaluator {
    private static final Logger LOG = LoggerFactory.getLogger(FlagEvaluator.class);
    private final ConfigStore store;
    private final BucketHasher hasher = new BucketHasher();

    public FlagEvaluator(ConfigStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public <T> T evaluate(String environment, String flagName, EvaluationContext context,
                          Class<T> valueType, T fallback) {
        Objects.requireNonNull(valueType, "valueType");
        if (fallback != null && !valueType.isInstance(fallback)) {
            throw new IllegalArgumentException("Fallback does not match requested type");
        }
        FlagConfig config = null;
        try {
            config = store.get(environment, flagName);
            if (config == null) {
                return fallback;
            }
            if (!config.type().matches(valueType)) {
                throw new IllegalArgumentException("Requested type does not match configured flag type");
            }
            Objects.requireNonNull(context, "context");
            for (var rule : config.rules()) {
                if (rule.condition().matches(context.attributes())) {
                    return valueType.cast(rule.value());
                }
            }
            var rollout = config.rollout();
            if (rollout != null && context.hasRolloutIdentity()
                    && hasher.bucket(environment, context.tenantId(), context.userId(), flagName,
                    rollout.bucketGroup()) < rollout.bucketCount()) {
                return valueType.cast(rollout.value());
            }
            return valueType.cast(config.defaultValue());
        } catch (RuntimeException e) {
            LOG.atError().addKeyValue("event", "flag_evaluation_error")
                    .addKeyValue("environment", environment).addKeyValue("flag", flagName)
                    .setCause(e).log("Feature flag evaluation failed");
            return config != null && config.type().matches(valueType)
                    ? valueType.cast(config.defaultValue()) : fallback;
        }
    }
}

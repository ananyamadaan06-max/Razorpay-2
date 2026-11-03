package com.razorpay.featureflags;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryConfigStore implements ConfigStore {
    private record Key(String environment, String flagName) {
        private Key {
            if (environment == null || environment.isBlank() || flagName == null || flagName.isBlank()) {
                throw new IllegalArgumentException("Environment and flag name must not be blank");
            }
        }
    }

    private final ConcurrentHashMap<Key, FlagConfig> configs = new ConcurrentHashMap<>();

    @Override
    public void set(String environment, FlagConfig config) {
        Objects.requireNonNull(config, "config");
        configs.put(new Key(environment, config.flagName()), config);
    }

    @Override
    public FlagConfig get(String environment, String flagName) {
        return configs.get(new Key(environment, flagName));
    }
}

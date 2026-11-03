package com.razorpay.featureflags;

public interface ConfigStore {
    void set(String environment, FlagConfig config);

    FlagConfig get(String environment, String flagName);
}

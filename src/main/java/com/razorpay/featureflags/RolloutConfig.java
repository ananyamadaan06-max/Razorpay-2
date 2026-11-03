package com.razorpay.featureflags;

import java.util.Objects;

public record RolloutConfig(double percentage, Object value, String bucketGroup) {
    public RolloutConfig {
        if (!Double.isFinite(percentage) || percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("Percentage must be between 0 and 100");
        }
        Objects.requireNonNull(value, "value");
        if (bucketGroup != null && bucketGroup.isBlank()) {
            throw new IllegalArgumentException("Bucket group must not be blank");
        }
    }

    public int bucketCount() {
        return (int) Math.round(percentage * 100);
    }
}

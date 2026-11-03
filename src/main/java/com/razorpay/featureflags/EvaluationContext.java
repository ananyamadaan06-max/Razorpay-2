package com.razorpay.featureflags;

import java.util.Map;

public record EvaluationContext(String tenantId, String userId, Map<String, Object> attributes) {
    public EvaluationContext {
        attributes = Map.copyOf(attributes);
        for (var value : attributes.values()) {
            if (!(value instanceof String || value instanceof Boolean || value instanceof Integer)) {
                throw new IllegalArgumentException("Attributes support String, Boolean, and Integer values");
            }
        }
    }

    public boolean hasRolloutIdentity() {
        return tenantId != null && !tenantId.isBlank() && userId != null && !userId.isBlank();
    }
}

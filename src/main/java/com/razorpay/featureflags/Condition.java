package com.razorpay.featureflags;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record Condition(String attribute, Operator operator, Object value) {
    public enum Operator { EQUALS, IN }

    public Condition {
        if (attribute == null || attribute.isBlank()) {
            throw new IllegalArgumentException("Attribute must not be blank");
        }
        Objects.requireNonNull(operator, "operator");
        if (operator == Operator.IN) {
            if (!(value instanceof Collection<?> values) || values.isEmpty()) {
                throw new IllegalArgumentException("IN requires a nonempty collection");
            }
            values.forEach(Condition::validateValue);
            value = Set.copyOf(values);
        } else {
            validateValue(value);
        }
    }

    private static void validateValue(Object value) {
        if (!(value instanceof String || value instanceof Boolean || value instanceof Integer)) {
            throw new IllegalArgumentException("Conditions support String, Boolean, and Integer values");
        }
    }

    public boolean matches(Map<String, Object> attributes) {
        return attributes.containsKey(attribute) && (operator == Operator.EQUALS
                ? value.equals(attributes.get(attribute))
                : ((Set<?>) value).contains(attributes.get(attribute)));
    }
}

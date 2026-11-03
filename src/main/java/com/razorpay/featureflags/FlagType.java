package com.razorpay.featureflags;

public enum FlagType {
    BOOLEAN(Boolean.class), STRING(String.class), INTEGER(Integer.class);

    private final Class<?> valueClass;

    FlagType(Class<?> valueClass) {
        this.valueClass = valueClass;
    }

    public boolean accepts(Object value) {
        return valueClass.isInstance(value);
    }

    public boolean matches(Class<?> requestedType) {
        return valueClass.equals(requestedType);
    }
}

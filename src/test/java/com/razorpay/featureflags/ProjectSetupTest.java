package com.razorpay.featureflags;

import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ProjectSetupTest {
    @Test
    void loggingIsAvailable() {
        assertInstanceOf(LoggerContext.class, LoggerFactory.getILoggerFactory());
    }
}

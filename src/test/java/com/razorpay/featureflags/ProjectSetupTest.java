package com.razorpay.featureflags;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProjectSetupTest {
    @Test
    void loggingIsAvailable() {
        var logger = LoggerFactory.getLogger(ProjectSetupTest.class);
        assertNotNull(logger);
        logger.info("CP0 smoke test passed");
    }
}

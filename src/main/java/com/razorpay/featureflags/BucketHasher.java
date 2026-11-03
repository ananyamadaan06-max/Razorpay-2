package com.razorpay.featureflags;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class BucketHasher {
    public static final int BUCKETS = 10_000;

    public int bucket(String environment, String tenantId, String userId, String flagName,
                      String bucketGroup) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            // Length prefixes prevent ambiguous concatenations; the tag separates group and flag keys.
            for (var part : new String[]{environment, tenantId, userId,
                    bucketGroup == null ? "flag" : "group",
                    bucketGroup == null ? flagName : bucketGroup}) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return (int) Long.remainderUnsigned(ByteBuffer.wrap(digest.digest()).getLong(), BUCKETS);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}

package org.xidian.program325.common;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
        return PREFIX + "$" + ITERATIONS + "$" + encoder.encodeToString(salt)
                + "$" + encoder.encodeToString(derive(password, salt, ITERATIONS));
    }

    public static boolean matches(String password, String encoded) {
        String[] parts = encoded.split("\\$", -1);
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 1 || iterations > 2_000_000) {
                return false;
            }
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode(parts[2]);
            byte[] expected = decoder.decode(parts[3]);
            return salt.length >= 16 && expected.length == 32
                    && MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec specification = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(specification).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("密码算法不可用", error);
        } finally {
            specification.clearPassword();
        }
    }
}

package sg.edu.nus.facilityflow.auth;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import sg.edu.nus.facilityflow.service.ValidationException;

/** AUT-004/005: JDK PBKDF2-HMAC-SHA256, independent salts, no composition rule. */
public final class PasswordHasher {
    private static final int ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(char[] password) {
        int length = password == null ? 0 : Character.codePointCount(password, 0, password.length);
        if (length < 8 || length > 24) {
            throw new ValidationException("Password must contain 8–24 characters.");
        }
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] key = derive(password, salt);
        try {
            return "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                    + "$" + Base64.getEncoder().encodeToString(key);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    public static boolean matches(char[] password, String encoded) {
        if (password == null || password.length > 48 || encoded == null) {
            return false;
        }
        try {
            String[] parts = encoded.split("\\$", -1);
            if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")
                    || !parts[1].equals(Integer.toString(ITERATIONS))) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (salt.length != 16 || expected.length != 32) {
                return false;
            }
            byte[] actual = derive(password, salt);
            try {
                return MessageDigest.isEqual(expected, actual);
            } finally {
                Arrays.fill(actual, (byte) 0);
            }
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt) {
        var spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password security is unavailable.", exception);
        } finally {
            spec.clearPassword();
        }
    }
}

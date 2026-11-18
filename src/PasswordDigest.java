import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class PasswordDigest {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 600_000;
    private static final int DIGEST_BITS = 256;

    private final byte[] salt;
    private final byte[] digest;

    private PasswordDigest(byte[] salt, byte[] digest) {
        this.salt = salt;
        this.digest = digest;
    }

    static PasswordDigest create(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return new PasswordDigest(salt, derive(password, salt));
    }

    boolean matches(String password) {
        byte[] candidate = derive(password, salt);
        try {
            return MessageDigest.isEqual(digest, candidate);
        } finally {
            Arrays.fill(candidate, (byte) 0);
        }
    }

    private static byte[] derive(String password, byte[] salt) {
        char[] chars = password.toCharArray();
        PBEKeySpec key = new PBEKeySpec(chars, salt, ITERATIONS, DIGEST_BITS);
        Arrays.fill(chars, '\0');
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(key).getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is unavailable", ex);
        } finally {
            key.clearPassword();
        }
    }
}

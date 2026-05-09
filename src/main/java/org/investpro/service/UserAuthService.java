package org.investpro.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.prefs.Preferences;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class UserAuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_BYTES = 24;
    private static final int HASH_BYTES = 32;
    private static final int ITERATIONS = 120_000;
    private static final long RESET_TOKEN_TTL_SECONDS = 30 * 60;

    private final Preferences preferences = Preferences.userNodeForPackage(UserAuthService.class);

    public AuthResult register(String username, String email, char[] password) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedEmail = safe(email).trim().toLowerCase(Locale.ROOT);
        if (normalizedUsername.isBlank() || normalizedEmail.isBlank() || password == null || password.length == 0) {
            return AuthResult.failure("Username, email, and password are required.");
        }
        if (!normalizedEmail.contains("@") || normalizedEmail.startsWith("@") || normalizedEmail.endsWith("@")) {
            return AuthResult.failure("Enter a valid email address.");
        }
        String passwordError = validatePassword(password);
        if (passwordError != null) {
            return AuthResult.failure(passwordError);
        }
        if (userExists(normalizedUsername)) {
            return AuthResult.failure("This username already exists.");
        }

        byte[] salt = randomBytes(SALT_BYTES);
        byte[] hash = hashPassword(password, salt);
        preferences.put(userKey(normalizedUsername, "username"), username.trim());
        preferences.put(userKey(normalizedUsername, "email"), normalizedEmail);
        preferences.put(userKey(normalizedUsername, "salt"), encode(salt));
        preferences.put(userKey(normalizedUsername, "password_hash"), encode(hash));
        preferences.putLong(userKey(normalizedUsername, "created_at"), Instant.now().getEpochSecond());
        return AuthResult.success("Account created.");
    }

    public AuthResult signIn(String username, char[] password) {
        if (safe(username).isBlank() || password == null || password.length == 0) {
            return AuthResult.failure("Username and password are required.");
        }
        String normalizedUsername = findUser(username).orElse("");
        if (normalizedUsername.isBlank()) {
            return AuthResult.failure("Account not found.");
        }

        byte[] salt = decode(preferences.get(userKey(normalizedUsername, "salt"), ""));
        byte[] expected = decode(preferences.get(userKey(normalizedUsername, "password_hash"), ""));
        byte[] actual = hashPassword(password, salt);
        if (!MessageDigest.isEqual(expected, actual)) {
            return AuthResult.failure("Incorrect username or password.");
        }

        preferences.putLong(userKey(normalizedUsername, "last_login_at"), Instant.now().getEpochSecond());
        return AuthResult.success("Signed in.");
    }

    public ResetTokenResult beginPasswordReset(String usernameOrEmail) {
        String normalizedUsername = findUser(usernameOrEmail).orElse("");
        if (normalizedUsername.isBlank()) {
            return ResetTokenResult.failure("No account matches that username or email.");
        }

        String token = createResetToken();
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] tokenHash = hashSecret(token.toCharArray(), salt);
        preferences.put(userKey(normalizedUsername, "reset_salt"), encode(salt));
        preferences.put(userKey(normalizedUsername, "reset_hash"), encode(tokenHash));
        preferences.putLong(userKey(normalizedUsername, "reset_expires_at"), Instant.now().plusSeconds(RESET_TOKEN_TTL_SECONDS).getEpochSecond());
        return ResetTokenResult.success(
                token,
                preferences.get(userKey(normalizedUsername, "email"), ""),
                "Password reset token created."
        );
    }

    public AuthResult resetPassword(String usernameOrEmail, String token, char[] newPassword) {
        String normalizedUsername = findUser(usernameOrEmail).orElse("");
        if (normalizedUsername.isBlank()) {
            return AuthResult.failure("No account matches that username or email.");
        }
        String passwordError = validatePassword(newPassword);
        if (passwordError != null) {
            return AuthResult.failure(passwordError);
        }

        long expiresAt = preferences.getLong(userKey(normalizedUsername, "reset_expires_at"), 0L);
        if (expiresAt < Instant.now().getEpochSecond()) {
            clearResetToken(normalizedUsername);
            return AuthResult.failure("Reset token expired. Request a new one.");
        }

        byte[] salt = decode(preferences.get(userKey(normalizedUsername, "reset_salt"), ""));
        byte[] expected = decode(preferences.get(userKey(normalizedUsername, "reset_hash"), ""));
        byte[] actual = hashSecret(safe(token).toCharArray(), salt);
        if (!MessageDigest.isEqual(expected, actual)) {
            return AuthResult.failure("Reset token is incorrect.");
        }

        byte[] passwordSalt = randomBytes(SALT_BYTES);
        preferences.put(userKey(normalizedUsername, "salt"), encode(passwordSalt));
        preferences.put(userKey(normalizedUsername, "password_hash"), encode(hashPassword(newPassword, passwordSalt)));
        clearResetToken(normalizedUsername);
        return AuthResult.success("Password updated. You can sign in now.");
    }

    public Optional<String> rememberedUsername() {
        if (!preferences.getBoolean("remember_me_enabled", false)) {
            return Optional.empty();
        }
        return Optional.of(preferences.get("remembered_username", "")).filter(value -> !value.isBlank());
    }

    public void rememberUser(String username) {
        preferences.put("remembered_username", safe(username).trim());
        preferences.putBoolean("remember_me_enabled", true);
    }

    public void forgetRememberedUser() {
        preferences.remove("remembered_username");
        preferences.putBoolean("remember_me_enabled", false);
    }

    public boolean isRememberMeEnabled() {
        return preferences.getBoolean("remember_me_enabled", false);
    }

    private Optional<String> findUser(String usernameOrEmail) {
        String lookup = safe(usernameOrEmail).trim().toLowerCase(Locale.ROOT);
        if (lookup.isBlank()) {
            return Optional.empty();
        }
        String normalizedUsername = normalizeUsername(lookup);
        if (userExists(normalizedUsername)) {
            return Optional.of(normalizedUsername);
        }
        try {
            for (String key : preferences.keys()) {
                if (!key.startsWith("user.") || !key.endsWith(".email")) {
                    continue;
                }
                if (lookup.equals(preferences.get(key, ""))) {
                    return Optional.of(key.substring("user.".length(), key.length() - ".email".length()));
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private boolean userExists(String normalizedUsername) {
        return !preferences.get(userKey(normalizedUsername, "password_hash"), "").isBlank();
    }

    private void clearResetToken(String normalizedUsername) {
        preferences.remove(userKey(normalizedUsername, "reset_salt"));
        preferences.remove(userKey(normalizedUsername, "reset_hash"));
        preferences.remove(userKey(normalizedUsername, "reset_expires_at"));
    }

    private static String validatePassword(char[] password) {
        if (password == null || password.length < 8) {
            return "Password must be at least 8 characters.";
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char value : password) {
            hasLetter |= Character.isLetter(value);
            hasDigit |= Character.isDigit(value);
        }
        if (!hasLetter || !hasDigit) {
            return "Password must include letters and numbers.";
        }
        return null;
    }

    private static byte[] hashPassword(char[] password, byte[] salt) {
        return hashSecret(password, salt);
    }

    private static byte[] hashSecret(char[] secret, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(secret, salt, ITERATIONS, HASH_BYTES * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash credential.", exception);
        }
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String createResetToken() {
        byte[] tokenBytes = randomBytes(18);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private static String userKey(String normalizedUsername, String suffix) {
        return "user.%s.%s".formatted(normalizedUsername, suffix);
    }

    private static String normalizeUsername(String username) {
        return safe(username).trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private static byte[] decode(String value) {
        if (value == null || value.isBlank()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(value);
    }

}

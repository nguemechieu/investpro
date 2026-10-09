package org.investpro.exchange.schwab;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.SecureRandom;
import java.util.*;

/** AES-GCM authenticated encryption; unlock password is supplied separately, never saved alongside tokens. */
public final class SchwabEncryptedTokenStore implements SchwabTokenStore {
    private final Path file;
    private final java.util.function.Supplier<String> password;
    private final ObjectMapper json;
    private static final int MAGIC = 0x49505331;

    public SchwabEncryptedTokenStore(Path file, java.util.function.Supplier<String> password, ObjectMapper json) {
        this.file = file.toAbsolutePath().normalize(); this.password = password; this.json = json;
    }
    @Override public synchronized Optional<SchwabTokenState> load() throws IOException {
        if (!Files.exists(file)) return Optional.empty();
        try {
            if (Files.size(file) > 65536) throw new IOException();
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length < 48 || bytes.length > 65536) throw new IOException();
            var buffer = ByteBuffer.wrap(bytes);
            if (buffer.getInt() != MAGIC) throw new IOException();
            byte[] salt = new byte[16], nonce = new byte[12], encrypted = new byte[buffer.remaining() - 28];
            buffer.get(salt).get(nonce).get(encrypted);
            byte[] plain = crypt(Cipher.DECRYPT_MODE, encrypted, salt, nonce);
            try { return Optional.of(json.readValue(plain, SchwabTokenState.class)); }
            finally { Arrays.fill(plain, (byte) 0); }
        } catch (Exception error) {
            throw new SchwabAuthenticationException("Schwab token store cannot be unlocked or is corrupted. Check the unlock password or unlink and reauthorize.");
        }
    }
    @Override public synchronized void save(SchwabTokenState state) throws IOException {
        Path temporary = null;
        try {
            byte[] salt = new byte[16], nonce = new byte[12];
            var random = new SecureRandom(); random.nextBytes(salt); random.nextBytes(nonce);
            byte[] plain = json.writeValueAsBytes(state), encrypted;
            try { encrypted = crypt(Cipher.ENCRYPT_MODE, plain, salt, nonce); }
            finally { Arrays.fill(plain, (byte) 0); }
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), ".schwab-", ".tmp");
            restrict(temporary);
            Files.write(temporary, ByteBuffer.allocate(32 + encrypted.length).putInt(MAGIC)
                    .put(salt).put(nonce).put(encrypted).array());
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            throw new SchwabAuthenticationException("Unable to securely persist Schwab tokens. Configure SCHWAB_TOKEN_STORE_PASSWORD and a writable local token path.");
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ignored) { /* Encrypted temporary file retains owner-only permissions. */ }
            }
        }
    }
    private byte[] crypt(int mode, byte[] input, byte[] salt, byte[] nonce) throws Exception {
        String value = password.get();
        if (value == null || value.length() < 16) throw new IOException();
        char[] chars = value.toCharArray();
        var spec = new PBEKeySpec(chars, salt, 210000, 256);
        byte[] key;
        try { key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { Arrays.fill(chars, '\0'); spec.clearPassword(); }
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(ByteBuffer.allocate(4).putInt(MAGIC).array());
            return cipher.doFinal(input);
        } finally { Arrays.fill(key, (byte) 0); }
    }
    private static void restrict(Path path) throws IOException {
        var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) { posix.setPermissions(PosixFilePermissions.fromString("rw-------")); return; }
        var acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl == null) throw new IOException("Unsupported secure file permissions");
        acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
    }
    @Override public synchronized void clear() throws IOException {
        try { Files.deleteIfExists(file); }
        catch (IOException error) { throw new IOException("Unable to remove saved Schwab authorization."); }
    }
}

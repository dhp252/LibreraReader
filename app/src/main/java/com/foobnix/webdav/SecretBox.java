package com.foobnix.webdav;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A secret (the AI key) as it is stored on the WebDAV server: encrypted with the WebDAV password,
 * which every device of the user knows and the server only checks (design D9). PBKDF2-HMAC-SHA1
 * and hex, so it runs from API 24 and in JVM tests.
 */
public final class SecretBox {

    static final String PREFIX = "enc1:";
    private static final int ROUNDS = 20_000;
    private static final int SALT = 16;
    private static final int IV = 12;

    private SecretBox() {
    }

    public static String seal(String password, String plain) throws GeneralSecurityException {
        final SecureRandom random = new SecureRandom();
        final byte[] salt = new byte[SALT];
        final byte[] iv = new byte[IV];
        random.nextBytes(salt);
        random.nextBytes(iv);
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), new GCMParameterSpec(128, iv));
        final byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        final byte[] all = new byte[SALT + IV + sealed.length];
        System.arraycopy(salt, 0, all, 0, SALT);
        System.arraycopy(iv, 0, all, SALT, IV);
        System.arraycopy(sealed, 0, all, SALT + IV, sealed.length);
        return PREFIX + hex(all);
    }

    /** The secret, or null when it was sealed with another password, damaged or not sealed. */
    public static String open(String password, String sealed) {
        if (sealed == null || !sealed.startsWith(PREFIX)) {
            return null;
        }
        try {
            final byte[] all = unhex(sealed.substring(PREFIX.length()));
            if (all.length <= SALT + IV) {
                return null;
            }
            final byte[] salt = new byte[SALT];
            final byte[] iv = new byte[IV];
            System.arraycopy(all, 0, salt, 0, SALT);
            System.arraycopy(all, SALT, iv, 0, IV);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), new GCMParameterSpec(128, iv));
            final byte[] plain = cipher.doFinal(all, SALT + IV, all.length - SALT - IV);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    /** A digest standing for a secret where only a change must be seen (the merge base, D10). */
    public static String digest(String plain) {
        try {
            return "sha256:" + hex(MessageDigest.getInstance("SHA-256").digest(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SecretKeySpec key(String password, byte[] salt) throws GeneralSecurityException {
        final PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ROUNDS, 256);
        final byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        return new SecretKeySpec(key, "AES");
    }

    private static String hex(byte[] bytes) {
        final StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return out.toString();
    }

    private static byte[] unhex(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("odd hex");
        }
        final byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            final int hi = Character.digit(hex.charAt(2 * i), 16);
            final int lo = Character.digit(hex.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("not hex");
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}

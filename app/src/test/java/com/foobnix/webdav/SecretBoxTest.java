package com.foobnix.webdav;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SecretBoxTest {

    @Test
    public void aSealedKeyOpensWithTheSamePasswordOnly() throws Exception {
        final String sealed = SecretBox.seal("webdav-pass", "sk-ant-api03-secret");
        assertTrue(sealed.startsWith(SecretBox.PREFIX));
        assertFalse(sealed.contains("secret"));
        assertEquals("sk-ant-api03-secret", SecretBox.open("webdav-pass", sealed));
        assertNull(SecretBox.open("new-password", sealed));
    }

    @Test
    public void everySealIsDifferentAndDamageIsRefused() throws Exception {
        final String a = SecretBox.seal("p", "key");
        final String b = SecretBox.seal("p", "key");
        assertNotEquals(a, b);

        final char last = a.charAt(a.length() - 1);
        final String damaged = a.substring(0, a.length() - 1) + (last == '0' ? '1' : '0');
        assertNull(SecretBox.open("p", damaged));
        assertNull(SecretBox.open("p", "plain text"));
        assertNull(SecretBox.open("p", SecretBox.PREFIX + "zz"));
        assertNull(SecretBox.open("p", null));
    }

    @Test
    public void theDigestTellsKeysApartWithoutHoldingThem() {
        assertEquals(SecretBox.digest("key"), SecretBox.digest("key"));
        assertNotEquals(SecretBox.digest("key"), SecretBox.digest("key2"));
        assertFalse(SecretBox.digest("key").contains("key"));
    }
}

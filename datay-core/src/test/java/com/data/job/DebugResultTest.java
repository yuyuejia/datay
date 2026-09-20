package com.data.job;

import org.junit.jupiter.api.Test;

import javax.sql.rowset.serial.SerialBlob;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DebugResultTest {

    @Test
    public void testToSerializableKeepsSimpleValues() {
        assertNull(DebugResult.toSerializable(null));
        assertEquals(1, DebugResult.toSerializable(1));
        assertEquals("abc", DebugResult.toSerializable("abc"));
        assertEquals(true, DebugResult.toSerializable(true));
    }

    @Test
    public void testToSerializableByteArray() {
        byte[] bytes = {1, 2, 3};
        assertEquals(Base64.getEncoder().encodeToString(bytes), DebugResult.toSerializable(bytes));
    }

    @Test
    public void testToSerializableBlob() throws Exception {
        byte[] bytes = {4, 5, 6};
        assertEquals(Base64.getEncoder().encodeToString(bytes), DebugResult.toSerializable(new SerialBlob(bytes)));
    }
}

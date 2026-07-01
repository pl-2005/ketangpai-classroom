package com.ketangpai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FileServiceTest {

    @Test
    void normalizeObjectPathAcceptsRawObjectPath() {
        assertEquals("files/2026/07/demo.txt",
                FileService.normalizeObjectPath("files/2026/07/demo.txt"));
    }

    @Test
    void normalizeObjectPathStripsPublicApiPrefix() {
        assertEquals("files/2026/07/demo.txt",
                FileService.normalizeObjectPath("/api/files/files/2026/07/demo.txt"));
    }

    @Test
    void normalizeObjectPathStripsPublicApiPrefixFromAbsoluteUrl() {
        assertEquals("files/2026/07/demo.txt",
                FileService.normalizeObjectPath("http://localhost:8080/api/files/files/2026/07/demo.txt"));
    }

    @Test
    void normalizeObjectPathKeepsNullAsNull() {
        assertNull(FileService.normalizeObjectPath(null));
    }
}

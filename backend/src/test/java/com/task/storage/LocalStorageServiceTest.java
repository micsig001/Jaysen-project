package com.task.storage;

import com.task.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LocalStorageService 单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>正常上传/下载/删除/存在性</li>
 *   <li>路径穿越防护（包含 ..、绝对路径）</li>
 *   <li>空 key 拒绝</li>
 *   <li>下载不存在文件</li>
 * </ul>
 *
 * <p>不依赖 Spring 容器，直接 new（构造器只接 StorageProperties + SignedUrlUtil）。
 *
 * @author Mavis
 */
class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    LocalStorageService service;

    @BeforeEach
    void setUp() throws Exception {
        // 构造 StorageProperties
        StorageProperties props = new StorageProperties();
        StorageProperties.Local local = new StorageProperties.Local();
        local.setPath(tempDir.toString());
        Field f = StorageProperties.class.getDeclaredField("local");
        f.setAccessible(true);
        f.set(props, local);

        // 构造 SignedUrlUtil（绕过 Spring @Value，直接传构造参数）
        // secret 必须 >= 32 字符
        String testSecret = "test-secret-for-unit-tests-aaaaaaaaaaaaaaaaaaaaaa";
        SignedUrlUtil signedUrlUtil = new SignedUrlUtil(testSecret, "");

        service = new LocalStorageService(props, signedUrlUtil);
        // init() 是 @PostConstruct，测试里手动调
        service.init();
    }

    @Test
    @DisplayName("正常上传/下载")
    void uploadAndDownload() throws IOException {
        String key = "tasks/123/test.txt";
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
        String url = service.upload(key, new ByteArrayInputStream(data), "text/plain", data.length);
        assertEquals("/api/files/" + key, url);
        assertTrue(Files.exists(tempDir.resolve("tasks/123/test.txt")));

        try (InputStream is = service.download(key)) {
            byte[] read = is.readAllBytes();
            assertArrayEquals(data, read);
        }
    }

    @Test
    @DisplayName("路径穿越 .. 被拒绝")
    void pathTraversalWithDoubleDot() {
        String key = "../etc/passwd";
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.upload(key, new ByteArrayInputStream(new byte[0]), "text/plain", 0));
        assertTrue(ex.getMessage().contains("非法的存储 key"));
    }

    @Test
    @DisplayName("绝对路径 key 被拒绝")
    void pathTraversalAbsolute() {
        String key = "/etc/passwd";
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.upload(key, new ByteArrayInputStream(new byte[0]), "text/plain", 0));
        assertTrue(ex.getMessage().contains("非法的存储 key"));
    }

    @Test
    @DisplayName("反斜杠路径被规范化")
    void backslashPath() throws IOException {
        String key = "uploads\\file.txt";
        byte[] data = "x".getBytes();
        service.upload(key, new ByteArrayInputStream(data), "text/plain", 1);
        // 写到正斜杠路径
        assertTrue(Files.exists(tempDir.resolve("uploads/file.txt")));
    }

    @Test
    @DisplayName("空 key 拒绝")
    void emptyKeyRejected() {
        assertThrows(BusinessException.class,
                () -> service.upload("", new ByteArrayInputStream(new byte[0]), "text/plain", 0));
        assertThrows(BusinessException.class,
                () -> service.upload(null, new ByteArrayInputStream(new byte[0]), "text/plain", 0));
    }

    @Test
    @DisplayName("下载不存在的文件 404")
    void downloadMissing() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.download("not/exists.txt"));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("delete 删除存在的文件返回 true")
    void deleteExisting() throws IOException {
        String key = "to-delete.txt";
        service.upload(key, new ByteArrayInputStream("x".getBytes()), "text/plain", 1);
        assertTrue(service.delete(key));
        assertFalse(service.exists(key));
    }

    @Test
    @DisplayName("delete 删除不存在的文件返回 false（不抛异常）")
    void deleteMissingReturnsFalse() {
        assertFalse(service.delete("never/existed.txt"));
    }

    @Test
    @DisplayName("exists 检查存在性")
    void existsCheck() throws IOException {
        String key = "exists.txt";
        service.upload(key, new ByteArrayInputStream("y".getBytes()), "text/plain", 1);
        assertTrue(service.exists(key));
        assertFalse(service.exists("nope.txt"));
    }

    @Test
    @DisplayName("generatePresignedUrl 返回的 URL 包含 sig + exp 参数")
    void presignedUrlShape() {
        String url = service.generatePresignedUrl("test/file.txt", 60);
        assertNotNull(url);
        assertTrue(url.startsWith("/api/files/"));
        assertTrue(url.contains("?exp="));
        assertTrue(url.contains("&sig="));
    }

    @Test
    @DisplayName("同 key 重复上传被拒绝（防止误覆盖）")
    void duplicateUploadRejected() throws IOException {
        String key = "duplicate.txt";
        service.upload(key, new ByteArrayInputStream("first".getBytes()), "text/plain", 5);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.upload(key, new ByteArrayInputStream("second".getBytes()),
                        "text/plain", 6));
        assertEquals(409, ex.getCode());
        // 第一个文件未被覆盖
        try (InputStream is = service.download(key)) {
            byte[] read = is.readAllBytes();
            assertEquals("first", new String(read));
        }
    }
}

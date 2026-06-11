package com.task.storage;

import com.task.common.BusinessException;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MinioStorageService 单元测试
 *
 * <p>用 Mockito mock {@link MinioClient}（SDK 真实对象需要 endpoint/credentials，
 * 不便在单元测试中起一个真 MinIO server），验证 5 个接口方法的：
 * <ul>
 *   <li>正常路径：参数透传到 MinioClient，返回值正确映射</li>
 *   <li>异常路径：MinIO 抛 NoSuchKey / 其它错误 → 映射为 BusinessException(404) / false</li>
 *   <li>空 key / expireSeconds<=0 等入参校验</li>
 *   <li>未初始化（config 不全）→ 503</li>
 * </ul>
 *
 * <p>不依赖 Spring 容器，直接 new + 反射注入 mock client。
 *
 * @author Mavis
 */
class MinioStorageServiceTest {

    private MinioClient minioClient;
    private MinioStorageService service;
    private StorageProperties props;

    @BeforeEach
    void setUp() throws Exception {
        // 构造 StorageProperties.minio 子配置
        props = new StorageProperties();
        StorageProperties.Minio minio = new StorageProperties.Minio();
        minio.setEndpoint("http://localhost:9000");
        minio.setAccessKey("test-access-key");
        minio.setSecretKey("test-secret-key-32chars-aaaaaaaaaa");
        minio.setBucket("test-bucket");
        Field f = StorageProperties.class.getDeclaredField("minio");
        f.setAccessible(true);
        f.set(props, minio);

        // mock MinioClient
        minioClient = mock(MinioClient.class);

        service = new MinioStorageService(props);
        // init() 是 @PostConstruct 构造 MinioClient，会把 minioClient 字段覆盖
        // 这里 init 后再用反射把 mock 注入回去
        service.init();
        Field clientField = MinioStorageService.class.getDeclaredField("minioClient");
        clientField.setAccessible(true);
        clientField.set(service, minioClient);
    }

    // ============================================
    // upload
    // ============================================

    @Test
    @DisplayName("upload：合法 key + 输入流 → putObject 触发 + 返回公开 URL")
    void upload_validKey_callsPutObjectAndReturnsUrl() throws Exception {
        // Given
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenReturn(mock(ObjectWriteResponse.class));

        // When
        String url = service.upload("tasks/1/doc.pdf",
                new ByteArrayInputStream("hello".getBytes()),
                "application/pdf", 5L);

        // Then
        assertEquals("http://localhost:9000/test-bucket/tasks/1/doc.pdf", url);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        PutObjectArgs args = captor.getValue();
        assertEquals("test-bucket", args.bucket());
        assertEquals("tasks/1/doc.pdf", args.object());
        assertEquals("application/pdf", args.contentType());
    }

    @Test
    @DisplayName("upload：contentType 为 null → 默认 application/octet-stream")
    void upload_nullContentType_usesDefault() throws Exception {
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenReturn(mock(ObjectWriteResponse.class));

        service.upload("k", new ByteArrayInputStream(new byte[0]), null, 0);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertEquals("application/octet-stream", captor.getValue().contentType());
    }

    @Test
    @DisplayName("upload：空 key → 400 BusinessException")
    void upload_emptyKey_throws400() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.upload("", new ByteArrayInputStream(new byte[0]), "text/plain", 0));
        assertEquals(400, ex.getCode());
        verifyNoInteractions(minioClient);
    }

    @Test
    @DisplayName("upload：null 输入流 → 400 BusinessException")
    void upload_nullStream_throws400() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.upload("k", null, "text/plain", 0));
        assertEquals(400, ex.getCode());
        verifyNoInteractions(minioClient);
    }

    @Test
    @DisplayName("upload：MinioClient 抛异常 → 500 BusinessException")
    void upload_minioThrows_returns500() throws Exception {
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new RuntimeException("connection refused"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.upload("k", new ByteArrayInputStream(new byte[0]), "text/plain", 0));
        assertEquals(500, ex.getCode());
        assertTrue(ex.getMessage().contains("MinIO 上传失败"));
    }

    // ============================================
    // download
    // ============================================

    @Test
    @DisplayName("download：合法 key → 返回 GetObjectResponse 流")
    void download_validKey_returnsStream() throws Exception {
        InputStream stubStream = new ByteArrayInputStream("file-content".getBytes());
        // GetObjectResponse 构造：(headers, bucket, region, object, stream)
        GetObjectResponse response = new GetObjectResponse(
                new Headers.Builder().build(), "test-bucket", "us-east-1", "k", stubStream);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

        try (InputStream is = service.download("tasks/1/doc.pdf")) {
            assertNotNull(is);
            byte[] data = is.readAllBytes();
            assertEquals("file-content", new String(data));
        }
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }

    @Test
    @DisplayName("download：MinIO NoSuchKey → 404 BusinessException")
    void download_noSuchKey_returns404() throws Exception {
        ErrorResponse errResp = mockErrorResponse("NoSuchKey", "Object does not exist");
        ErrorResponseException ex = new ErrorResponseException(errResp, null, null);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(ex);

        BusinessException be = assertThrows(BusinessException.class,
                () -> service.download("missing.txt"));
        assertEquals(404, be.getCode());
    }

    @Test
    @DisplayName("download：MinIO 其它错误 → 500 BusinessException")
    void download_otherError_returns500() throws Exception {
        ErrorResponse errResp = mockErrorResponse("InternalError", "Internal Server Error");
        ErrorResponseException ex = new ErrorResponseException(errResp, null, null);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(ex);

        BusinessException be = assertThrows(BusinessException.class,
                () -> service.download("any.txt"));
        assertEquals(500, be.getCode());
    }

    @Test
    @DisplayName("download：空 key → 400")
    void download_emptyKey_returns400() {
        assertThrows(BusinessException.class, () -> service.download(""));
        verifyNoInteractions(minioClient);
    }

    // ============================================
    // delete
    // ============================================

    @Test
    @DisplayName("delete：文件存在 → removeObject 调用 + 返回 true")
    void delete_existing_returnsTrue() throws Exception {
        // MinIO removeObject 在文件不存在时也不抛异常；直接 mock 让它成功
        // removeObject 返回 void，所以 doNothing() 即可
        doNothing().when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertTrue(service.delete("tasks/1/old.pdf"));
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("delete：MinIO 抛 NoSuchKey → 返回 false（不抛异常）")
    void delete_noSuchKey_returnsFalse() throws Exception {
        ErrorResponse errResp = mockErrorResponse("NoSuchKey", "Object does not exist");
        ErrorResponseException ex = new ErrorResponseException(errResp, null, null);
        doThrow(ex).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertFalse(service.delete("missing.txt"));
    }

    @Test
    @DisplayName("delete：MinIO 抛其它异常 → 返回 false（不抛异常）")
    void delete_otherError_returnsFalse() throws Exception {
        doThrow(new RuntimeException("network")).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertFalse(service.delete("any.txt"));
    }

    @Test
    @DisplayName("delete：空 key → 直接 false")
    void delete_emptyKey_returnsFalse() {
        assertFalse(service.delete(""));
        verifyNoInteractions(minioClient);
    }

    // ============================================
    // exists
    // ============================================

    @Test
    @DisplayName("exists：文件存在 → true")
    void exists_existing_returnsTrue() throws Exception {
        StatObjectResponse response = mock(StatObjectResponse.class);
        when(response.size()).thenReturn(100L);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(response);

        assertTrue(service.exists("k"));
    }

    @Test
    @DisplayName("exists：NoSuchKey → false")
    void exists_noSuchKey_returnsFalse() throws Exception {
        ErrorResponse errResp = mockErrorResponse("NoSuchKey", "Object does not exist");
        ErrorResponseException ex = new ErrorResponseException(errResp, null, null);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(ex);

        assertFalse(service.exists("missing.txt"));
    }

    @Test
    @DisplayName("exists：其它异常 → false（不抛）")
    void exists_otherError_returnsFalse() throws Exception {
        when(minioClient.statObject(any(StatObjectArgs.class)))
                .thenThrow(new RuntimeException("net"));

        assertFalse(service.exists("any.txt"));
    }

    @Test
    @DisplayName("exists：空 key → false")
    void exists_emptyKey_returnsFalse() {
        assertFalse(service.exists(""));
        verifyNoInteractions(minioClient);
    }

    // ============================================
    // generatePresignedUrl
    // ============================================

    @Test
    @DisplayName("generatePresignedUrl：合法参数 → 返回带签名的 URL")
    void presignedUrl_valid_returnsSignedUrl() throws Exception {
        String expected = "http://localhost:9000/test-bucket/k?X-Amz-Signature=abc&X-Amz-Expires=60";
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn(expected);

        String url = service.generatePresignedUrl("k", 60);
        assertEquals(expected, url);

        ArgumentCaptor<GetPresignedObjectUrlArgs> captor =
                ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(captor.capture());
        assertEquals("test-bucket", captor.getValue().bucket());
        assertEquals("k", captor.getValue().object());
        assertEquals(60, captor.getValue().expiry());
    }

    @Test
    @DisplayName("generatePresignedUrl：expireSeconds=0 → 400")
    void presignedUrl_zeroExpire_returns400() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generatePresignedUrl("k", 0));
        assertEquals(400, ex.getCode());
        verifyNoInteractions(minioClient);
    }

    @Test
    @DisplayName("generatePresignedUrl：expireSeconds 超过 7 天 → 截断为 604800")
    void presignedUrl_overMaxExpiry_truncatedTo7Days() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://...");

        // 30 天 = 2592000 秒 → 应被截断为 604800 (7 days)
        service.generatePresignedUrl("k", 30 * 24 * 3600);

        ArgumentCaptor<GetPresignedObjectUrlArgs> captor =
                ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(captor.capture());
        assertEquals(7 * 24 * 3600, captor.getValue().expiry());
    }

    @Test
    @DisplayName("generatePresignedUrl：空 key → 400")
    void presignedUrl_emptyKey_returns400() {
        assertThrows(BusinessException.class, () -> service.generatePresignedUrl("", 60));
        verifyNoInteractions(minioClient);
    }

    @Test
    @DisplayName("generatePresignedUrl：MinioClient 抛异常 → 500")
    void presignedUrl_minioThrows_returns500() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new RuntimeException("sign failed"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generatePresignedUrl("k", 60));
        assertEquals(500, ex.getCode());
    }

    // ============================================
    // 未初始化（配置缺失）
    // ============================================

    @Test
    @DisplayName("未初始化：minioClient == null → 503")
    void notInitialized_throws503() throws Exception {
        // 构造一个 endpoint 为空的 props
        StorageProperties emptyProps = new StorageProperties();
        StorageProperties.Minio minio = new StorageProperties.Minio();
        minio.setBucket("test-bucket");
        // 不设 endpoint / accessKey / secretKey
        Field f = StorageProperties.class.getDeclaredField("minio");
        f.setAccessible(true);
        f.set(emptyProps, minio);

        MinioStorageService uninitService = new MinioStorageService(emptyProps);
        uninitService.init();  // minioClient 仍为 null

        BusinessException ex = assertThrows(BusinessException.class,
                () -> uninitService.exists("k"));
        assertEquals(503, ex.getCode());
    }

    @Test
    @DisplayName("未初始化：bucket 为空 → 400")
    void notInitialized_emptyBucket_returns400() throws Exception {
        StorageProperties emptyProps = new StorageProperties();
        StorageProperties.Minio minio = new StorageProperties.Minio();
        minio.setEndpoint("http://localhost:9000");
        minio.setAccessKey("k");
        minio.setSecretKey("s");
        // 不设 bucket
        Field f = StorageProperties.class.getDeclaredField("minio");
        f.setAccessible(true);
        f.set(emptyProps, minio);

        MinioStorageService svc = new MinioStorageService(emptyProps);
        svc.init();
        // minioClient 已构造，但 bucket 缺失

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.exists("k"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("bucket"));
    }

    // ============================================
    // getBeanName
    // ============================================

    @Test
    @DisplayName("getBeanName：返回 'minioStorageService'")
    void beanName_isStable() {
        assertEquals("minioStorageService", service.getBeanName());
    }

    // ============================================
    // 辅助方法
    // ============================================

    /**
     * 构造带 code / message 的 ErrorResponse
     * <p>ErrorResponse 的 code/message 字段是 protected（包外不可写），
     * 用 Mockito mock 出来再 stub 受保护字段的 getter
     */
    private static ErrorResponse mockErrorResponse(String code, String message) {
        ErrorResponse mock = mock(ErrorResponse.class);
        when(mock.code()).thenReturn(code);
        when(mock.message()).thenReturn(message);
        return mock;
    }
}

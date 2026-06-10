package com.task.storage;

import com.task.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件下载 Controller（基于 HMAC 签名 URL）
 *
 * <p>URL 格式：{@code GET /api/files/{encodedKey}?exp={expireSeconds}&sig={hmac}}
 *
 * <p>由 {@link SignedUrlUtil} 生成、校验签名。SecurityConfig 已配置
 * {@code /api/files/**} 为 permitAll（不走 JWT 鉴权），但**必须有合法签名**才能下载。
 *
 * <p>支持的存储实现：当前为 {@link LocalStorageService}；后续接入 MinIO / OSS
 * 后，对应实现直接通过 {@code StorageService} 接口注入。
 *
 * @author Mavis
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final StorageServiceFactory storageServiceFactory;
    private final SignedUrlUtil signedUrlUtil;

    /**
     * 下载文件
     *
     * <p>URL 示例：{@code /api/files/tasks%2F123%2Ffile.pdf?exp=1700000000&sig=AbC...}
     *
     * @param exp 过期时间（秒）
     * @param sig HMAC 签名
     */
    @GetMapping("/**")
    public ResponseEntity<InputStreamResource> download(
            HttpServletRequest request,
            @RequestParam("exp") long exp,
            @RequestParam("sig") String sig
    ) {
        // 1) 从 request URI 提取 encoded key
        String requestUri = request.getRequestURI();
        String prefix = request.getContextPath() + "/api/files/";
        if (!requestUri.startsWith(prefix)) {
            throw BusinessException.badRequest("非法的下载 URL");
        }
        String encodedKey = requestUri.substring(prefix.length());
        if (encodedKey.isBlank()) {
            throw BusinessException.badRequest("存储 key 不能为空");
        }
        String key = URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);

        // 2) 校验签名
        if (!signedUrlUtil.verify(key, exp, sig)) {
            log.warn("[文件下载] 签名校验失败: remoteAddr={}, key={}, exp={}",
                    request.getRemoteAddr(), key, exp);
            throw BusinessException.forbidden("签名无效或已过期");
        }

        // 3) 取当前激活的存储实现
        StorageService storage = storageServiceFactory.getActive();

        // 4) 检查存在性 + 拿到 InputStream
        if (!storage.exists(key)) {
            throw BusinessException.notFound("文件不存在: " + key);
        }
        try {
            InputStream stream = storage.download(key);
            String filename = extractFilename(key);
            MediaType contentType = guessContentType(filename);

            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + sanitizeFilename(filename) + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                    .body(new InputStreamResource(stream));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[文件下载] 下载失败: key={}", key, e);
            throw new BusinessException(500, "文件下载失败: " + e.getMessage());
        }
    }

    /**
     * 从 key 中提取文件名（去掉目录前缀）
     */
    private String extractFilename(String key) {
        if (key == null) return "file";
        int idx = key.lastIndexOf('/');
        return idx >= 0 ? key.substring(idx + 1) : key;
    }

    /**
     * 简单的 Content-Type 猜测（按扩展名）
     */
    private MediaType guessContentType(String filename) {
        if (filename == null) return MediaType.APPLICATION_OCTET_STREAM;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".svg")) return MediaType.valueOf("image/svg+xml");
        if (lower.endsWith(".txt")) return MediaType.TEXT_PLAIN;
        if (lower.endsWith(".json")) return MediaType.APPLICATION_JSON;
        if (lower.endsWith(".zip")) return MediaType.valueOf("application/zip");
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return MediaType.valueOf("application/msword");
        }
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) {
            return MediaType.valueOf("application/vnd.ms-excel");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * 清理文件名（去掉特殊字符，防止 header 注入）
     */
    private String sanitizeFilename(String name) {
        if (name == null) return "file";
        // 替换 \r \n 和引号
        return name.replaceAll("[\r\n\"\\\\]", "_");
    }
}

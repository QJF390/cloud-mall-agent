package com.exdemo.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "admin.upload")
public class UploadProperties {

    /** 落盘根目录，支持相对路径（相对进程的工作目录） */
    private String dir = "uploads";

    /** 对外访问前缀，必须与 StaticResourceConfig 的映射、网关路由、前端代理保持一致 */
    private String urlPrefix = "/uploads";

    /** 单文件大小上限（MB），与 spring.servlet.multipart.max-file-size 保持一致 */
    private long maxSizeMb = 5;

    /**
     * 扩展名白名单。
     * <p><b>刻意不含 svg</b>：SVG 是 XML，可以内嵌 &lt;script&gt;，一旦被当图片直出就是存储型 XSS。
     * 白名单比黑名单安全，因为黑名单永远列不全。</p>
     */
    private List<String> allowedExtensions = new ArrayList<>(List.of("jpg", "jpeg", "png", "webp", "gif"));
}

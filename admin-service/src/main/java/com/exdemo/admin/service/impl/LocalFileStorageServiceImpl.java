package com.exdemo.admin.service.impl;

import com.exdemo.admin.config.UploadProperties;
import com.exdemo.admin.service.FileStorageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFileStorageServiceImpl implements FileStorageService {

    /** 子目录格式：年/月 */
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM");

    private final UploadProperties uploadProperties;

    /** 校验并缓存的落盘根目录绝对路径 */
    private Path rootDir;

    @PostConstruct
    void init() {
        rootDir = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootDir);
            log.info("[上传] 文件存储根目录: {}", rootDir);
        } catch (IOException e) {
            // 目录都建不出来，说明配置或挂载有问题，必须启动即失败而不是等用户上传时报 500
            throw new IllegalStateException("上传目录创建失败: " + rootDir, e);
        }
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片");
        }
        Long maxBytes = uploadProperties.getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("图片不能超过 " + uploadProperties.getMaxSizeMb() + " MB");
        }

        String ext = resolveExtension(file.getOriginalFilename());

        // 文件名由服务端生成，绝不使用原始文件名：
        // 1) 原始名可能带 ../ 做目录穿越；2) 可能重名互相覆盖；3) 可能含特殊字符导致 URL 异常
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        String datePath = LocalDate.now().format(DATE_DIR);
        Path targetDir = rootDir.resolve(datePath);
        Path target = targetDir.resolve(filename);

        if (!target.normalize().startsWith(rootDir)) {
            throw new IllegalArgumentException("非法的文件路径");
        }

        try {
            Files.createDirectories(targetDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("[上传] 文件落盘失败, target={}", target, e);
            throw new IllegalStateException("图片保存失败，请稍后重试", e);
        }

        String url = uploadProperties.getUrlPrefix() + "/" + datePath + "/" + filename;
        log.info("[上传] 保存成功, size={}B, url={}", file.getSize(), url);
        return url;
    }

    /**
     * 取扩展名并做白名单校验。
     * <p>只看扩展名不看 Content-Type：Content-Type 由客户端随便填，不可信；
     * 真正可信的是我们"只回显不执行"的静态资源服务策略 + 扩展名白名单。</p>
     */
    private String resolveExtension(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename;
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (ext.isEmpty() || !uploadProperties.getAllowedExtensions().contains(ext)) {
            throw new IllegalArgumentException("仅支持 " + String.join("/", uploadProperties.getAllowedExtensions()) + " 格式的图片");
        }
        return ext;
    }
}

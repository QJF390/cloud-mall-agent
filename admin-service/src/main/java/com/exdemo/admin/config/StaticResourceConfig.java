package com.exdemo.admin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class StaticResourceConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
        String location = root.toUri().toString();
        // toUri() 在目录不存在时不会补末尾斜杠，缺了斜杠 Spring 会把最后一段当文件名，映射直接失效
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        registry.addResourceHandler(uploadProperties.getUrlPrefix() + "/**")
                .addResourceLocations(location)
                // 文件名是 UUID，内容永不变更，可以放心让浏览器长缓存，减少图片回源
                .setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic());
    }
}

package com.exdemo.admin.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务。
 *
 * <p>抽成接口是为了给"换存储"留门：现在是本地磁盘，
 * 真上生产多半要换成对象存储（OSS/COS），届时只需加一个 OSS 实现，
 * 上层业务代码一行不用改 —— 这就是"依赖抽象而不是实现"的实际收益。</p>
 */
public interface FileStorageService {

    /**
     * 保存文件并返回可直接访问的相对 URL。
     *
     * @param file 上传的文件（不可为空）
     * @return 形如 {@code /uploads/2026/09/3f2a....png} 的相对 URL
     * @throws IllegalArgumentException 文件为空、超限、类型不在白名单时抛出（由全局异常处理器转成 400）
     */
    String store(MultipartFile file);
}

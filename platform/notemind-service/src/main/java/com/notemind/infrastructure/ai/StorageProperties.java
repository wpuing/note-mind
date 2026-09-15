package com.notemind.infrastructure.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地文件存储配置，对应 {@code notemind.storage.*}。
 */
@ConfigurationProperties(prefix = "notemind.storage")
public class StorageProperties {

    /** 相对进程工作目录或绝对路径 */
    private String uploadDir = "./data/uploads";

    /**
     * 获取上传目录路径。
     *
     * @return 上传目录
     */
    public String getUploadDir() {
        return uploadDir;
    }

    /**
     * 设置上传目录路径。
     *
     * @param uploadDir 上传目录
     */
    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }
}

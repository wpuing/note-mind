package com.notemind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NoteMind Service 启动入口。
 * <p>Spring Boot 主类，默认业务端口见应用配置（通常 8081）。</p>
 */
@SpringBootApplication
public class NoteMindApplication {

    /**
     * 启动 Spring Boot 应用上下文。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 启动 NoteMind Service
        SpringApplication.run(NoteMindApplication.class, args);
    }
}

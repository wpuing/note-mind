package com.notemind.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NoteMind API 网关启动类（默认端口 8080）。
 * <p>
 * 负责路由、CORS、剥离伪造用户头，以及 Bearer 粗拦截；浏览器只应访问本网关。
 */
@SpringBootApplication
public class GatewayApplication {

    /**
     * 启动 Spring Cloud Gateway 应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 启动网关 Spring Boot 容器
        SpringApplication.run(GatewayApplication.class, args);
    }
}

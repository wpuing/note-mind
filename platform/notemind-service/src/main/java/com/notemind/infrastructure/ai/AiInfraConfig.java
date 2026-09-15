package com.notemind.infrastructure.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.notemind.infrastructure.config.NoteMindDefaultsProperties;

/**
 * AI 基础设施配置：启用 AI 引擎、本地存储与业务默认值三类 ConfigurationProperties。
 * <p>本身无 Bean 方法，仅作属性绑定开关。
 */
@Configuration
@EnableConfigurationProperties({
    AiEngineProperties.class,
    StorageProperties.class,
    NoteMindDefaultsProperties.class
})
public class AiInfraConfig {
}

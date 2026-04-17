package com.aicode.review.config;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * DotEnv 配置类
 * 
 * 用于在 Spring 上下文初始化后记录 .env 加载状态。
 * 实际的 .env 文件加载在 Application.main() 中完成，
 * 以确保在 Spring Boot 配置类初始化之前环境变量已设置。
 */
@Slf4j
@Configuration
public class DotEnvConfig {

    @PostConstruct
    public void init() {
        // 检查关键环境变量是否已加载
        String apiKey = System.getProperty("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }
        
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("OPENAI_API_KEY 未设置，请在 .env 文件或环境变量中配置");
        } else {
            log.info("OPENAI_API_KEY 已配置 (长度: {})", apiKey.length());
        }
    }
}

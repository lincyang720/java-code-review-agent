package com.aicode.review.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * LangChain4j 配置类
 * 配置 LangChain4j 的 AI 模型和组件
 * <p>
 * 默认使用 DeepSeek API（国内访问，性价比高）
 * 兼容 OpenAI API 格式，可无缝切换其他模型
 */
@Slf4j
@Configuration
public class LangChain4jConfig {

    @Value("${DEEPSEEK_API_KEY:${OPENAI_API_KEY:}}")
    private String apiKey;

    @Value("${DEEPSEEK_MODEL:deepseek-chat}")
    private String modelName;

    @Value("${langchain4j.open-ai.chat-model.temperature:0.3}")
    private Double temperature;

    @Value("${DEEPSEEK_BASE_URL:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.timeout:120}")
    private Integer timeoutSeconds;

    /**
     * 创建 LangChain4j ChatLanguageModel
     * <p>
     * 默认使用 DeepSeek API，如需切换回 OpenAI，修改环境变量即可：
     * - DEEPSEEK_BASE_URL=https://api.openai.com/v1
     * - DEEPSEEK_MODEL=gpt-4o-mini
     */
    @Bean
    @Primary
    public ChatLanguageModel chatLanguageModel() {
        log.info("初始化 LangChain4j ChatLanguageModel");
        log.info("模型提供商: {}", baseUrl.contains("deepseek") ? "DeepSeek" : "OpenAI");
        log.info("模型名称: {}", modelName);
        log.info("API 地址: {}", baseUrl);

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .baseUrl(baseUrl)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
}

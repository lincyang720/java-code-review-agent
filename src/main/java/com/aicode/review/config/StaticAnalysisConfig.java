package com.aicode.review.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 静态代码分析工具配置
 * 
 * 支持 SonarQube、SpotBugs 等工具的集成配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.analysis")
public class StaticAnalysisConfig {
    
    /**
     * SonarQube 配置
     */
    private SonarQube sonarqube = new SonarQube();
    
    /**
     * SpotBugs 配置
     */
    private SpotBugs spotbugs = new SpotBugs();
    
    /**
     * 是否启用本地规则检测
     */
    private boolean localRulesEnabled = true;
    
    /**
     * 是否启用 AI 深度分析
     */
    private boolean aiAnalysisEnabled = true;
    
    @Data
    public static class SonarQube {
        /**
         * 是否启用 SonarQube
         */
        private boolean enabled = false;
        
        /**
         * SonarQube 服务器地址
         */
        private String url = "http://localhost:9000";
        
        /**
         * 认证 Token
         */
        private String token = "";
        
        /**
         * 项目 Key
         */
        private String projectKey = "";
    }
    
    @Data
    public static class SpotBugs {
        /**
         * 是否启用 SpotBugs
         */
        private boolean enabled = false;
        
        /**
         * SpotBugs 可执行路径
         */
        private String executablePath = "spotbugs";
        
        /**
         * 检测级别：min, default, max
         */
        private String effort = "default";
        
        /**
         * 最低报告级别：high, medium, low, experimental
         */
        private String threshold = "medium";
    }
}

package com.aicode.review.service;

import com.aicode.review.model.CommitMessage;
import com.aicode.review.model.PRDifferences;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * 提示词服务
 * 
 * 管理和构建代码审查的 Prompt 模板。
 * 支持从配置文件加载提示词，并提供多种场景的 Prompt 构建方法。
 * 
 * @author AI Code Review Team
 */
@Slf4j
@Service
public class PromptService {

    /**
     * 从配置文件加载审查提示词模板
     */
    @Value("classpath:prompts/review-prompt.txt")
    private Resource reviewPromptResource;

    private String baseReviewPrompt;

    /**
     * 初始化，加载提示词模板
     */
    @PostConstruct
    public void init() {
        try {
            baseReviewPrompt = loadPromptFromResource(reviewPromptResource);
            log.info("成功加载审查提示词模板");
        } catch (IOException e) {
            log.warn("无法从文件加载提示词，使用默认提示词: {}", e.getMessage());
            baseReviewPrompt = getDefaultReviewPrompt();
        }
    }

    /**
     * 构建代码审查 Prompt
     * 
     * @param diff PR 差异信息
     * @param commit 提交信息
     * @return 完整的提示词
     */
    public String buildReviewPrompt(PRDifferences diff, CommitMessage commit) {
        StringBuilder prompt = new StringBuilder();
        
        // 系统提示
        prompt.append(baseReviewPrompt).append("\n\n");
        
        // 上下文信息
        prompt.append("## 审查上下文\n\n");
        prompt.append(diff.getContext()).append("\n");
        
        if (commit != null) {
            prompt.append("提交信息: ").append(commit.getShortMessage()).append("\n");
            if (!commit.isConventionalCommit()) {
                prompt.append("⚠️ 注意：提交信息不符合 Conventional Commits 规范\n");
            }
        }
        
        prompt.append("\n");
        
        // 代码变更
        prompt.append("## 代码变更\n\n");
        
        // 只包含 Java 文件的变更
        var javaFiles = diff.getJavaFileDiffs();
        if (javaFiles.isEmpty()) {
            prompt.append("本次 PR 没有 Java 文件变更。\n");
        } else {
            for (var file : javaFiles) {
                prompt.append("### 文件: ").append(file.getFilePath()).append("\n");
                prompt.append("```java\n");
                if (file.getPatch() != null) {
                    // 限制 patch 大小，避免 token 过多
                    String patch = file.getPatch();
                    if (patch.length() > 3000) {
                        patch = patch.substring(0, 3000) + "\n... (内容已截断)";
                    }
                    prompt.append(patch);
                }
                prompt.append("\n```\n\n");
            }
        }
        
        // 输出格式要求
        prompt.append("\n");
        prompt.append(getOutputFormatInstruction());
        
        return prompt.toString();
    }

    /**
     * 构建简单代码审查 Prompt（用于单个代码片段）
     * 
     * @param code 代码片段
     * @param context 上下文描述
     * @return 提示词
     */
    public String buildSimpleReviewPrompt(String code, String context) {
        return """
            你是一位资深的 Java 代码审查专家。请审查以下代码：
            
            上下文：%s
            
            ```java
            %s
            ```
            
            请从以下方面进行分析：
            1. 是否存在 Bug 风险（空指针、数组越界等）
            2. 代码质量（可读性、命名、复杂度）
            3. 性能问题
            4. 安全漏洞
            
            返回格式：
            - 严重问题：...
            - 警告：...
            - 建议：...
            """.formatted(context, code);
    }

    /**
     * 构建复杂度分析 Prompt
     * 
     * @param code 代码片段
     * @return 提示词
     */
    public String buildComplexityPrompt(String code) {
        return """
            分析以下 Java 代码的复杂度：
            
            ```java
            %s
            ```
            
            请提供：
            1. 圈复杂度估算
            2. 方法行数统计
            3. 嵌套深度分析
            4. 可读性评分（1-10）
            5. 改进建议
            """.formatted(code);
    }

    /**
     * 构建安全扫描 Prompt
     * 
     * @param code 代码片段
     * @return 提示词
     */
    public String buildSecurityPrompt(String code) {
        return """
            作为安全专家，扫描以下 Java 代码的安全漏洞：
            
            ```java
            %s
            ```
            
            重点关注：
            - SQL 注入
            - XSS 攻击
            - 敏感信息泄露
            - 不安全的反序列化
            - 路径遍历
            - 不安全的随机数
            
            如果发现漏洞，请说明：
            1. 漏洞类型和严重程度
            2. 具体位置
            3. 修复建议
            """.formatted(code);
    }

    /**
     * 从资源文件加载提示词
     * 
     * @param resource 资源文件
     * @return 文件内容
     * @throws IOException 读取失败时抛出
     */
    private String loadPromptFromResource(Resource resource) throws IOException {
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        }
    }

    /**
     * 获取默认的审查提示词
     * 
     * @return 默认提示词
     */
    private String getDefaultReviewPrompt() {
        return """
            你是一位拥有 10 年以上经验的资深 Java 架构师，负责进行代码审查。
            
            你的审查原则：
            1. 关注代码的正确性、可读性和可维护性
            2. 发现潜在 Bug 和安全漏洞
            3. 提供具体、可执行的改进建议
            4. 保持建设性态度，解释为什么需要修改
            
            审查重点：
            - Bug 风险：空指针、并发问题、资源泄漏
            - 代码质量：重复代码、过长方法、复杂逻辑
            - 安全漏洞：SQL 注入、XSS、敏感信息泄露
            - 性能问题：低效算法、N+1 查询、内存泄漏
            - 规范遵守：命名规范、注释、代码风格
            """;
    }

    /**
     * 获取输出格式说明
     * 
     * @return 格式说明
     */
    private String getOutputFormatInstruction() {
        return """
            ## 输出格式要求
            
            请以 JSON 格式返回审查结果：
            
            ```json
            {
              "summary": "总体评价（1-2句话）",
              "issues": [
                {
                  "severity": "CRITICAL|WARNING|INFO",
                  "type": "BUG|SECURITY|QUALITY|PERFORMANCE|STYLE",
                  "file": "文件路径",
                  "line": 行号,
                  "description": "问题描述",
                  "suggestion": "修复建议"
                }
              ]
            }
            ```
            
            severity 定义：
            - CRITICAL: 严重问题，必须修复（如空指针风险、安全漏洞）
            - WARNING: 警告，建议修复（如代码质量问题）
            - INFO: 信息，供参考（如风格建议）
            """;
    }
}

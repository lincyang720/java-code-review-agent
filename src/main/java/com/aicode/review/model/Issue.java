package com.aicode.review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码审查发现的问题模型
 * 
 * 用于表示代码审查过程中发现的各种问题，包括 BUG、安全漏洞、
 * 代码质量问题等。支持不同严重级别的问题分类。
 * 
 * @author AI Code Review Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Issue {

    /**
     * 问题严重级别
     * CRITICAL: 严重问题，必须修复
     * WARNING: 警告级别，建议修复
     * INFO: 信息级别，可供参考
     */
    public enum Severity {
        CRITICAL, WARNING, INFO
    }

    /**
     * 问题类型
     * BUG: 潜在 Bug
     * SECURITY: 安全漏洞
     * QUALITY: 代码质量问题
     * PERFORMANCE: 性能问题
     * STYLE: 代码风格问题
     */
    public enum Type {
        BUG, SECURITY, QUALITY, PERFORMANCE, STYLE
    }

    /**
     * 问题严重级别
     */
    private Severity severity;

    /**
     * 问题类型
     */
    private Type type;

    /**
     * 问题所在文件路径
     */
    private String file;

    /**
     * 问题所在行号
     */
    private Integer line;

    /**
     * 问题描述
     */
    private String description;

    /**
     * 修复建议
     */
    private String suggestion;

    /**
     * 相关代码片段
     */
    private String code;

    /**
     * 规则ID（如果是规则检测发现的问题）
     */
    private String ruleId;

    /**
     * 转换为 Markdown 格式的问题描述
     * 
     * @return Markdown 格式的字符串
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        
        // 严重级别图标
        String icon = switch (severity) {
            case CRITICAL -> "🚨";
            case WARNING -> "⚠️";
            case INFO -> "ℹ️";
        };
        
        sb.append("### ").append(icon).append(" ").append(type).append(": ").append(description).append("\n\n");
        
        if (file != null) {
            sb.append("**文件**: `").append(file).append("`\n\n");
        }
        
        if (line != null) {
            sb.append("**行号**: ").append(line).append("\n\n");
        }
        
        if (code != null && !code.isEmpty()) {
            sb.append("**代码片段**:\n```java\n").append(code).append("\n```\n\n");
        }
        
        if (suggestion != null && !suggestion.isEmpty()) {
            sb.append("**建议**: ").append(suggestion).append("\n\n");
        }
        
        if (ruleId != null) {
            sb.append("**规则**: `").append(ruleId).append("`\n\n");
        }
        
        return sb.toString();
    }

    /**
     * 判断是否为严重问题
     * 
     * @return 如果是 CRITICAL 级别返回 true
     */
    public boolean isCritical() {
        return severity == Severity.CRITICAL;
    }
}

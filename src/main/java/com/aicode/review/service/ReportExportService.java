package com.aicode.review.service;

import com.aicode.review.model.Issue;
import com.aicode.review.model.ReviewReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * 报告导出服务
 *
 * 支持将审查报告导出为多种格式：HTML、JSON、PDF、Markdown
 *
 * @author AI Code Review Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExportService {

    private final ObjectMapper objectMapper;

    /**
     * 导出报告为指定格式
     *
     * @param report 审查报告
     * @param format 导出格式 (html, json, pdf, markdown)
     * @return 导出的字节数组
     */
    public byte[] exportReport(ReviewReport report, String format) {
        if (report == null) {
            throw new IllegalArgumentException("报告不能为空");
        }

        return switch (format.toLowerCase()) {
            case "html" -> exportToHtml(report);
            case "json" -> exportToJson(report);
            case "pdf" -> exportToPdf(report);
            case "markdown" -> exportToMarkdown(report);
            default -> throw new IllegalArgumentException("不支持的导出格式: " + format);
        };
    }

    /**
     * 获取导出文件的 MIME 类型
     */
    public String getMimeType(String format) {
        return switch (format.toLowerCase()) {
            case "html" -> "text/html; charset=UTF-8";
            case "json" -> "application/json; charset=UTF-8";
            case "pdf" -> "application/pdf";
            case "markdown" -> "text/markdown; charset=UTF-8";
            default -> "application/octet-stream";
        };
    }

    /**
     * 获取导出文件的扩展名
     */
    public String getFileExtension(String format) {
        return switch (format.toLowerCase()) {
            case "markdown" -> "md";
            default -> format.toLowerCase();
        };
    }

    /**
     * 导出为 HTML 格式
     */
    private byte[] exportToHtml(ReviewReport report) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"zh-CN\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\" />\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n");
        html.append("    <title>代码审查报告</title>\n");
        html.append("    <style>\n");
        html.append(getHtmlStyles());
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container\">\n");
        
        // 标题
        html.append("        <div class=\"header\">\n");
        html.append("            <h1>🔍 代码审查报告</h1>\n");
        html.append("            <p>生成时间: ").append(formatDateTime(report.getReviewTime())).append("</p>\n");
        html.append("        </div>\n");
        
        // 统计概览
        html.append("        <div class=\"stats-overview\">\n");
        html.append("            <div class=\"stat-card critical\">\n");
        html.append("                <div class=\"stat-value\">").append(report.getCriticalCount()).append("</div>\n");
        html.append("                <div class=\"stat-label\">严重问题</div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"stat-card warning\">\n");
        html.append("                <div class=\"stat-value\">").append(report.getWarningCount()).append("</div>\n");
        html.append("                <div class=\"stat-label\">警告</div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"stat-card info\">\n");
        html.append("                <div class=\"stat-value\">").append(report.getInfoCount()).append("</div>\n");
        html.append("                <div class=\"stat-label\">建议</div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"stat-card total\">\n");
        html.append("                <div class=\"stat-value\">").append(report.getIssueCount()).append("</div>\n");
        html.append("                <div class=\"stat-label\">总计</div>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
        
        // 摘要
        if (report.getSummary() != null) {
            html.append("        <div class=\"section\">\n");
            html.append("            <h2>📋 审查摘要</h2>\n");
            html.append("            <div class=\"summary-box\">\n");
            html.append("                <p>").append(escapeHtml(report.getSummary())).append("</p>\n");
            html.append("            </div>\n");
            html.append("        </div>\n");
        }
        
        // 问题列表 - 按类型分组展示
        if (report.getIssues() != null && !report.getIssues().isEmpty()) {
            html.append("        <div class=\"section\">\n");
            html.append("            <h2>⚠️ 问题详情</h2>\n");
            
            // 按类型分组
            java.util.Map<Issue.Type, List<Issue>> issuesByType = report.getIssues().stream()
                    .collect(java.util.stream.Collectors.groupingBy(Issue::getType));
            
            // 按类型顺序展示
            Issue.Type[] typeOrder = {Issue.Type.SECURITY, Issue.Type.BUG, Issue.Type.PERFORMANCE, Issue.Type.QUALITY, Issue.Type.STYLE};
            for (Issue.Type type : typeOrder) {
                List<Issue> typeIssues = issuesByType.getOrDefault(type, java.util.List.of());
                if (!typeIssues.isEmpty()) {
                    html.append(renderIssueGroupHtml(type, typeIssues));
                }
            }
            
            html.append("        </div>\n");
        }
        
        // AI 报告 - Markdown 转 HTML
        if (report.getAiReport() != null && !report.getAiReport().isEmpty()) {
            html.append("        <div class=\"section\">\n");
            html.append("            <h2>🤖 AI 审查报告</h2>\n");
            html.append("            <div class=\"ai-report\">\n");
            // 将 Markdown 转换为 HTML
            String aiReportHtml = markdownToHtml(report.getAiReport());
            html.append(aiReportHtml);
            html.append("            </div>\n");
            html.append("        </div>\n");
        }
        
        // 页脚
        html.append("        <div class=\"footer\">\n");
        html.append("            <p>由 Java Code Review Agent 生成</p>\n");
        if (report.getReviewDurationMs() != null) {
            html.append("            <p>审查耗时: ").append(report.getReviewDurationMs()).append("ms</p>\n");
        }
        html.append("        </div>\n");
        
        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>");
        
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 渲染单个问题为 HTML
     */
    private String renderIssueHtml(Issue issue) {
        StringBuilder sb = new StringBuilder();
        sb.append("                <div class=\"issue-item\">\n");
        sb.append("                    <div class=\"issue-header\">\n");
        sb.append("                        <span class=\"issue-rule\">").append(escapeHtml(issue.getRuleId())).append("</span>\n");
        sb.append("                        <span class=\"issue-type-badge\">").append(escapeHtml(issue.getType().name())).append("</span>\n");
        sb.append("                    </div>\n");
        sb.append("                    <div class=\"issue-body\">\n");
        sb.append("                        <p class=\"issue-desc\">").append(escapeHtml(issue.getDescription())).append("</p>\n");
        if (issue.getFile() != null) {
            sb.append("                        <p class=\"issue-location\">📍 ").append(escapeHtml(issue.getFile()));
            if (issue.getLine() > 0) {
                sb.append(":").append(issue.getLine());
            }
            sb.append("</p>\n");
        }
        if (issue.getSuggestion() != null) {
            sb.append("                        <div class=\"issue-suggestion\">\n");
            sb.append("                            <strong>建议:</strong> ").append(escapeHtml(issue.getSuggestion())).append("\n");
            sb.append("                        </div>\n");
        }
        sb.append("                    </div>\n");
        sb.append("                </div>\n");
        return sb.toString();
    }

    /**
     * 获取问题类型的图标
     */
    private String getTypeIcon(Issue.Type type) {
        return switch (type) {
            case SECURITY -> "🔒";
            case QUALITY -> "✨";
            case PERFORMANCE -> "⚡";
            case STYLE -> "🎨";
            case BUG -> "🐛";
        };
    }

    /**
     * 获取问题类型的名称
     */
    private String getTypeName(Issue.Type type) {
        return switch (type) {
            case SECURITY -> "安全问题";
            case QUALITY -> "代码质量";
            case PERFORMANCE -> "性能问题";
            case STYLE -> "代码风格";
            case BUG -> "潜在Bug";
        };
    }

    /**
     * 渲染问题分组 HTML - 按类型分组，卡片网格布局
     */
    private String renderIssueGroupHtml(Issue.Type type, List<Issue> issues) {
        StringBuilder sb = new StringBuilder();
        String typeClass = type.name().toLowerCase();
        String icon = getTypeIcon(type);
        String typeName = getTypeName(type);
        
        sb.append("            <div class=\"type-section\">\n");
        sb.append("                <div class=\"type-header ").append(typeClass).append("\">\n");
        sb.append("                    <span class=\"type-title\">").append(icon).append(" ").append(typeName).append("</span>\n");
        sb.append("                    <span class=\"type-count\">").append(issues.size()).append(" 个问题</span>\n");
        sb.append("                </div>\n");
        sb.append("                <div class=\"issue-grid\">\n");
        
        for (Issue issue : issues) {
            sb.append(renderIssueCardHtml(issue));
        }
        
        sb.append("                </div>\n");
        sb.append("            </div>\n");
        return sb.toString();
    }

    /**
     * 渲染问题卡片 HTML - 卡片式紧凑展示
     */
    private String renderIssueCardHtml(Issue issue) {
        StringBuilder sb = new StringBuilder();
        String severityClass = issue.getSeverity().name().toLowerCase();
        String severityLabel = getSeverityLabel(issue.getSeverity());
        
        sb.append("                    <div class=\"issue-card ").append(severityClass).append("\">\n");
        sb.append("                        <div class=\"issue-card-header\">\n");
        sb.append("                            <span class=\"issue-card-rule\">").append(escapeHtml(issue.getRuleId())).append("</span>\n");
        sb.append("                            <span class=\"issue-card-severity ").append(severityClass).append("\">").append(severityLabel).append("</span>\n");
        sb.append("                        </div>\n");
        sb.append("                        <div class=\"issue-card-desc\">").append(escapeHtml(issue.getDescription())).append("</div>\n");
        
        // 文件位置 - 精简显示
        if (issue.getFile() != null) {
            sb.append("                        <div class=\"issue-card-file\">📍 ");
            String fileName = issue.getFile();
            if (fileName.contains("/")) {
                fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
            }
            sb.append(escapeHtml(fileName));
            if (issue.getLine() > 0) {
                sb.append(":").append(issue.getLine());
            }
            sb.append("</div>\n");
        }
        
        sb.append("                    </div>\n");
        return sb.toString();
    }

    /**
     * 获取严重程度标签
     */
    private String getSeverityLabel(Issue.Severity severity) {
        return switch (severity) {
            case CRITICAL -> "严重";
            case WARNING -> "警告";
            case INFO -> "建议";
        };
    }

    /**
     * 获取 HTML 样式
     */
    private String getHtmlStyles() {
        return """
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body { 
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                background: #f5f7fa;
                color: #333;
                line-height: 1.6;
            }
            .container { max-width: 1000px; margin: 0 auto; padding: 40px 20px; }
            .header { text-align: center; margin-bottom: 40px; }
            .header h1 { font-size: 2.5rem; color: #2d3748; margin-bottom: 10px; }
            .header p { color: #718096; }
            .stats-overview { 
                display: grid; 
                grid-template-columns: repeat(4, 1fr); 
                gap: 20px; 
                margin-bottom: 40px;
            }
            .stat-card { 
                background: white; 
                border-radius: 12px; 
                padding: 24px; 
                text-align: center;
                box-shadow: 0 2px 8px rgba(0,0,0,0.08);
            }
            .stat-card.critical { border-top: 4px solid #e53e3e; }
            .stat-card.warning { border-top: 4px solid #dd6b20; }
            .stat-card.info { border-top: 4px solid #3182ce; }
            .stat-card.total { border-top: 4px solid #38a169; }
            .stat-value { font-size: 2.5rem; font-weight: 700; color: #2d3748; }
            .stat-label { color: #718096; margin-top: 5px; }
            .section { background: white; border-radius: 12px; padding: 24px; margin-bottom: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
            .section h2 { font-size: 1.5rem; color: #2d3748; margin-bottom: 20px; padding-bottom: 10px; border-bottom: 2px solid #e2e8f0; }
            .summary-box { background: #f7fafc; padding: 16px; border-radius: 8px; border-left: 4px solid #667eea; }
            .issue-type { margin: 20px 0 15px; padding: 10px 15px; border-radius: 8px; font-size: 1.1rem; }
            .issue-type.critical { background: #fed7d7; color: #c53030; }
            .issue-type.warning { background: #feebc8; color: #c05621; }
            .issue-type.info { background: #bee3f8; color: #2b6cb0; }
            .issue-list { display: flex; flex-direction: column; gap: 12px; }
            .issue-item { background: #f7fafc; border-radius: 8px; padding: 16px; border-left: 4px solid #cbd5e0; }
            .issue-item:hover { background: #edf2f7; }
            .issue-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
            .issue-rule { font-weight: 600; color: #4a5568; font-family: monospace; }
            .issue-type-badge { background: #e2e8f0; padding: 4px 10px; border-radius: 12px; font-size: 0.85rem; color: #4a5568; }
            .issue-desc { color: #2d3748; margin-bottom: 8px; }
            .issue-location { color: #718096; font-size: 0.9rem; margin-bottom: 8px; }
            .issue-suggestion { background: #f0fff4; padding: 10px; border-radius: 6px; border-left: 3px solid #48bb78; color: #276749; }
            .ai-report { background: #f7fafc; padding: 20px; border-radius: 8px; color: #4a5568; }
            .ai-report p { margin-bottom: 12px; line-height: 1.6; }
            .ai-report ul, .ai-report ol { margin-bottom: 12px; padding-left: 24px; }
            .ai-report li { margin-bottom: 6px; }
            .ai-report code { background: #edf2f7; padding: 2px 6px; border-radius: 4px; font-family: monospace; font-size: 0.9em; }
            .ai-report pre { background: #edf2f7; padding: 12px; border-radius: 6px; overflow-x: auto; white-space: pre-wrap; word-wrap: break-word; font-family: monospace; }
            .ai-report blockquote { border-left: 4px solid #cbd5e0; padding-left: 16px; margin: 12px 0; color: #718096; }
            .ai-report h1, .ai-report h2, .ai-report h3, .ai-report h4 { margin: 16px 0 12px; color: #2d3748; }
            .ai-report strong { font-weight: 600; color: #2d3748; }
            /* 问题卡片网格布局 */
            .issue-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 12px; }
            .issue-card { background: white; border-radius: 8px; padding: 12px; border-left: 4px solid #cbd5e0; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
            .issue-card.critical { border-left-color: #e53e3e; }
            .issue-card.warning { border-left-color: #dd6b20; }
            .issue-card.info { border-left-color: #3182ce; }
            .issue-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
            .issue-card-rule { font-size: 0.85rem; font-weight: 600; color: #4a5568; font-family: monospace; }
            .issue-card-severity { font-size: 0.75rem; padding: 2px 8px; border-radius: 12px; font-weight: 500; }
            .issue-card-severity.critical { background: #fed7d7; color: #c53030; }
            .issue-card-severity.warning { background: #feebc8; color: #c05621; }
            .issue-card-severity.info { background: #bee3f8; color: #2b6cb0; }
            .issue-card-desc { font-size: 0.9rem; color: #2d3748; margin-bottom: 6px; line-height: 1.4; }
            .issue-card-file { font-size: 0.8rem; color: #718096; }
            /* 类型标题样式 */
            .type-section { margin-bottom: 24px; }
            .type-header { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; padding-bottom: 8px; border-bottom: 2px solid #e2e8f0; }
            .type-header.security { border-bottom-color: #e53e3e; }
            .type-header.bug { border-bottom-color: #d69e2e; }
            .type-header.performance { border-bottom-color: #805ad5; }
            .type-header.quality { border-bottom-color: #38a169; }
            .type-header.style { border-bottom-color: #3182ce; }
            .type-title { font-size: 1.1rem; font-weight: 600; color: #2d3748; }
            .type-count { font-size: 0.9rem; color: #718096; }
            .footer { text-align: center; color: #718096; margin-top: 40px; padding-top: 20px; border-top: 1px solid #e2e8f0; }
            @media (max-width: 768px) {
                .stats-overview { grid-template-columns: repeat(2, 1fr); }
                .header h1 { font-size: 1.8rem; }
            }
            """;
    }

    /**
     * 导出为 JSON 格式
     */
    private byte[] exportToJson(ReviewReport report) {
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .enable(SerializationFeature.INDENT_OUTPUT);
            return mapper.writeValueAsBytes(report);
        } catch (Exception e) {
            log.error("导出 JSON 失败", e);
            throw new RuntimeException("导出 JSON 失败", e);
        }
    }

    /**
     * 导出为 PDF 格式
     */
    private byte[] exportToPdf(ReviewReport report) {
        try {
            // 先导出为 HTML
            byte[] htmlBytes = exportToHtml(report);
            String html = new String(htmlBytes, StandardCharsets.UTF_8);
            
            // 查找可用中文字体
            FontInfo fontInfo = findChineseFont();
            log.info("PDF 使用字体: {}, 路径: {}", fontInfo.family, fontInfo.path);
            
            // 修改 HTML 中的样式，注入中文字体支持
            String originalBodyStyle = "body { \n                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;";
            String newBodyStyle = "body { \n                font-family: '" + fontInfo.family + "', 'SimSun', 'SimHei', serif;";
            html = html.replace(originalBodyStyle, newBodyStyle);
            
            // 同时替换 * 选择器中的字体
            html = html.replace("* { margin: 0; padding: 0; box-sizing: border-box; }", 
                "* { margin: 0; padding: 0; box-sizing: border-box; font-family: '" + fontInfo.family + "', 'SimSun', 'SimHei', serif; }");
            
            // 使用 openhtmltopdf 将 HTML 转换为 PDF
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFont(new java.io.File(fontInfo.path), fontInfo.family);
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();
            
            byte[] pdfBytes = outputStream.toByteArray();
            log.info("PDF 导出成功，大小: {} bytes", pdfBytes.length);
            return pdfBytes;
        } catch (Exception e) {
            log.error("导出 PDF 失败", e);
            throw new RuntimeException("导出 PDF 失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 字体信息
     */
    private record FontInfo(String path, String family) {}
    
    /**
     * 查找系统中可用的中文字体
     */
    private FontInfo findChineseFont() {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            // Windows 字体候选列表 - 优先使用已知可用的字体
            String[][] winFonts = {
                {"C:/Windows/Fonts/simsun.ttc", "SimSun"},           // 宋体 - 最兼容
                {"C:/Windows/Fonts/simhei.ttf", "SimHei"},           // 黑体
                {"C:/Windows/Fonts/simkai.ttf", "KaiTi"},            // 楷体
                {"C:/Windows/Fonts/simfang.ttf", "FangSong"},        // 仿宋
                {"C:/Windows/Fonts/msyh.ttc", "Microsoft YaHei"},    // 微软雅黑
                {"C:/Windows/Fonts/msyhbd.ttc", "Microsoft YaHei"}   // 微软雅黑粗体
            };
            
            for (String[] font : winFonts) {
                java.io.File file = new java.io.File(font[0]);
                if (file.exists()) {
                    return new FontInfo(font[0], font[1]);
                }
            }
        } else if (os.contains("mac")) {
            String[] macFonts = {
                "/System/Library/Fonts/PingFang.ttc",
                "/System/Library/Fonts/STHeiti Light.ttc",
                "/Library/Fonts/Arial Unicode.ttf"
            };
            for (String path : macFonts) {
                if (new java.io.File(path).exists()) {
                    return new FontInfo(path, "PingFang SC");
                }
            }
        } else {
            // Linux
            String[] linuxFonts = {
                "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
            };
            for (String path : linuxFonts) {
                if (new java.io.File(path).exists()) {
                    return new FontInfo(path, "Noto Sans CJK SC");
                }
            }
        }
        
        // 如果找不到中文字体，抛出异常
        throw new RuntimeException("找不到系统中文字体，请确保系统安装了中文字体");
    }

    /**
     * 导出为 Markdown 格式
     */
    private byte[] exportToMarkdown(ReviewReport report) {
        StringBuilder md = new StringBuilder();
        
        md.append("# 🔍 代码审查报告\n\n");
        md.append("**生成时间:** ").append(formatDateTime(report.getReviewTime())).append("\n\n");
        
        // 统计
        md.append("## 📊 统计概览\n\n");
        md.append("| 安全问题 | 代码质量 | 代码风格 | 潜在Bug | 性能问题 | 总计 |\n");
        md.append("|:-------:|:-------:|:-------:|:------:|:-------:|:---:|\n");
        
        // 按类型统计
        long securityCount = report.getIssues().stream().filter(i -> i.getType() == Issue.Type.SECURITY).count();
        long qualityCount = report.getIssues().stream().filter(i -> i.getType() == Issue.Type.QUALITY).count();
        long styleCount = report.getIssues().stream().filter(i -> i.getType() == Issue.Type.STYLE).count();
        long bugCount = report.getIssues().stream().filter(i -> i.getType() == Issue.Type.BUG).count();
        long performanceCount = report.getIssues().stream().filter(i -> i.getType() == Issue.Type.PERFORMANCE).count();
        
        md.append("| ").append(securityCount).append(" | ")
          .append(qualityCount).append(" | ")
          .append(styleCount).append(" | ")
          .append(bugCount).append(" | ")
          .append(performanceCount).append(" | ")
          .append(report.getIssueCount()).append(" |\n\n");
        
        // 摘要
        if (report.getSummary() != null) {
            md.append("## 📋 审查摘要\n\n");
            md.append(report.getSummary()).append("\n\n");
        }
        
        // 问题列表 - 按类型分组
        if (report.getIssues() != null && !report.getIssues().isEmpty()) {
            md.append("## ⚠️ 问题详情\n\n");
            
            // 按类型分组
            java.util.Map<Issue.Type, List<Issue>> issuesByType = report.getIssues().stream()
                    .collect(java.util.stream.Collectors.groupingBy(Issue::getType));
            
            // 按类型顺序展示
            Issue.Type[] typeOrder = {Issue.Type.SECURITY, Issue.Type.BUG, Issue.Type.PERFORMANCE, Issue.Type.QUALITY, Issue.Type.STYLE};
            for (Issue.Type type : typeOrder) {
                List<Issue> typeIssues = issuesByType.getOrDefault(type, java.util.List.of());
                if (!typeIssues.isEmpty()) {
                    md.append(renderIssueGroupMarkdown(type, typeIssues));
                }
            }
        }
        
        // AI 报告
        if (report.getAiReport() != null && !report.getAiReport().isEmpty()) {
            md.append("## 🤖 AI 审查报告\n\n");
            md.append("```\n").append(report.getAiReport()).append("\n```\n\n");
        }
        
        // 页脚
        md.append("---\n\n");
        md.append("*由 Java Code Review Agent 生成*\n");
        if (report.getReviewDurationMs() != null) {
            md.append("*审查耗时: ").append(report.getReviewDurationMs()).append("ms*\n");
        }
        
        return md.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 渲染问题分组 Markdown
     */
    private String renderIssueGroupMarkdown(Issue.Type type, List<Issue> issues) {
        StringBuilder sb = new StringBuilder();
        String icon = getTypeIcon(type);
        String typeName = getTypeName(type);
        
        sb.append("### ").append(icon).append(" ").append(typeName)
          .append(" (").append(issues.size()).append(")\n\n");
        
        for (Issue issue : issues) {
            sb.append(renderCompactIssueMarkdown(issue));
        }
        
        return sb.toString();
    }

    /**
     * 渲染精简版问题 Markdown
     */
    private String renderCompactIssueMarkdown(Issue issue) {
        StringBuilder sb = new StringBuilder();
        String severityLabel = getSeverityLabel(issue.getSeverity());
        
        sb.append("- **[").append(severityLabel).append("]** ")
          .append("`").append(issue.getRuleId()).append("` ")
          .append(issue.getDescription());
        
        // 文件位置 - 只显示文件名
        if (issue.getFile() != null) {
            String fileName = issue.getFile();
            if (fileName.contains("/")) {
                fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
            }
            sb.append(" (`").append(fileName);
            if (issue.getLine() > 0) {
                sb.append(":").append(issue.getLine());
            }
            sb.append("`)");
        }
        
        sb.append("\n");
        return sb.toString();
    }

    /**
     * HTML 转义
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }

    /**
     * Markdown 转 HTML
     */
    private String markdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        try {
            Parser parser = Parser.builder().build();
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            String html = renderer.render(parser.parse(markdown));
            log.debug("Markdown 转 HTML 成功，输入长度: {}, 输出长度: {}", markdown.length(), html.length());
            return html;
        } catch (Exception e) {
            log.error("Markdown 转 HTML 失败", e);
            // 转换失败时返回原始内容（转义后）
            return "<pre>" + escapeHtml(markdown) + "</pre>";
        }
    }

    /**
     * 格式化日期时间
     */
    private String formatDateTime(java.time.LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}

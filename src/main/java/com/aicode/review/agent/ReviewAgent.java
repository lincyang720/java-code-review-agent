package com.aicode.review.agent;

import com.aicode.review.client.SonarQubeClient;
import com.aicode.review.client.SpotBugsRunner;
import com.aicode.review.model.Issue;
import com.aicode.review.model.ReviewReport;
import com.aicode.review.service.P3CAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 代码审查 Agent
 * 
 * 架构设计：
 * 1. 检测层：使用成熟的静态分析规则（P3C、自定义规则），不调用 AI 做检测
 * 2. 总结层：AI 只负责汇总检测结果，生成审查报告文档
 * 
 * @author AI Code Review Team
 */
@Slf4j
@Service
public class ReviewAgent {

    private final ChatLanguageModel chatLanguageModel;
    private final ReviewTools reviewTools;
    private final ObjectMapper objectMapper;
    private final SonarQubeClient sonarQubeClient;
    private final SpotBugsRunner spotBugsRunner;
    private final P3CAnalysisService p3cAnalysisService;

    // AI 报告生成服务
    private final ReportGenerator reportGenerator;

    @Autowired
    public ReviewAgent(
            ChatLanguageModel chatLanguageModel,
            ReviewTools reviewTools,
            ObjectMapper objectMapper,
            SonarQubeClient sonarQubeClient,
            SpotBugsRunner spotBugsRunner,
            P3CAnalysisService p3cAnalysisService) {
        this.chatLanguageModel = chatLanguageModel;
        this.reviewTools = reviewTools;
        this.objectMapper = objectMapper;
        this.sonarQubeClient = sonarQubeClient;
        this.spotBugsRunner = spotBugsRunner;
        this.p3cAnalysisService = p3cAnalysisService;
        
        // 构建 AI 报告生成服务
        this.reportGenerator = AiServices.builder(ReportGenerator.class)
                .chatLanguageModel(chatLanguageModel)
                .build();
        
        log.info("ReviewAgent 初始化完成（P3C: {}, SonarQube: {}, SpotBugs: {}）", 
                p3cAnalysisService.isAvailable() ? p3cAnalysisService.getRuleCount() + " rules" : "disabled",
                sonarQubeClient.isEnabled(), 
                spotBugsRunner.isEnabled());
    }

    /**
     * 执行代码审查（完整流程：本地检测 + AI报告）
     * 
     * @param code 要审查的代码
     * @param context 审查上下文（PR信息、提交信息等）
     * @return 审查报告
     */
    public ReviewReport review(String code, String context) {
        log.info("开始代码审查，代码长度: {} 字符", code.length());
        long startTime = System.currentTimeMillis();
        
        ReviewReport.ReviewReportBuilder reportBuilder = ReviewReport.builder()
                .reportId(UUID.randomUUID().toString())
                .reviewTime(LocalDateTime.now())
                .status(ReviewReport.Status.IN_PROGRESS);
        
        List<Issue> allIssues = new ArrayList<>();
        
        try {
            // 第一步：本地规则检测（P3C + 自定义规则）
            log.debug("执行本地规则检测...");
            List<Issue> localIssues = performLocalChecks(code);
            allIssues.addAll(localIssues);
            log.debug("本地规则检测完成，发现 {} 个问题", localIssues.size());
            
            // 第二步：AI 生成审查报告总结
            log.debug("生成 AI 审查报告...");
            String aiReport = generateAIReport(code, context, allIssues);
            
            // 构建报告
            reportBuilder
                    .issues(allIssues)
                    .summary(generateSummary(allIssues, context))
                    .aiReport(aiReport)
                    .status(ReviewReport.Status.SUCCESS)
                    .reviewDurationMs(System.currentTimeMillis() - startTime);
            
            log.info("代码审查完成，共发现 {} 个问题，耗时 {}ms", 
                    allIssues.size(), System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            log.error("代码审查失败", e);
            reportBuilder
                    .status(ReviewReport.Status.FAILED)
                    .errorMessage(e.getMessage())
                    .reviewDurationMs(System.currentTimeMillis() - startTime);
        }
        
        return reportBuilder.build();
    }

    /**
     * 仅执行本地规则检测（不调用AI）
     * 用于批量审查场景，先收集所有问题，最后统一生成AI报告
     * 
     * @param code 要检测的代码
     * @return 发现的问题列表
     */
    public List<Issue> performLocalChecksOnly(String code) {
        return performLocalChecks(code);
    }

    /**
     * 仅生成AI审查报告（基于已收集的问题）
     * 用于批量审查场景，所有文件检测完成后统一调用
     * 
     * @param fileCount 审查的文件数量
     * @param context 审查上下文
     * @param issues 已收集的所有问题
     * @return AI生成的报告
     */
    public String generateAIReportOnly(int fileCount, String context, List<Issue> issues) {
        return generateAIReportSummary(fileCount, context, issues);
    }

    /**
     * 获取 SonarQube 分析结果
     * 
     * @param projectKey 项目 Key
     * @param branch 分支名
     * @return 问题列表
     */
    public List<Issue> getSonarQubeIssues(String projectKey, String branch) {
        return sonarQubeClient.getIssues(projectKey, branch);
    }

    /**
     * 运行 SpotBugs 分析
     * 
     * @param classPath 类文件路径
     * @param sourcePath 源代码路径
     * @return 问题列表
     */
    public List<Issue> runSpotBugsAnalysis(String classPath, String sourcePath) {
        return spotBugsRunner.analyze(classPath, sourcePath);
    }

    /**
     * 执行本地规则检测
     * 
     * @param code 要检测的代码
     * @return 发现的问题列表
     */
    private List<Issue> performLocalChecks(String code) {
        List<Issue> issues = new ArrayList<>();
        
        try {
            // 第一步：P3C 阿里巴巴代码规范检测
            if (p3cAnalysisService.isAvailable()) {
                log.debug("执行 P3C 阿里巴巴代码规范检测...");
                List<Issue> p3cIssues = p3cAnalysisService.analyze(code, "Review.java");
                issues.addAll(p3cIssues);
                log.debug("P3C 检测完成，发现 {} 个问题", p3cIssues.size());
            }
            
            // 第二步：自定义规则检测（补充 P3C 未覆盖的场景）
            // 空指针检测
            issues.addAll(reviewTools.detectNullPointer(code));
            
            // 并发安全检测
            issues.addAll(reviewTools.detectConcurrency(code));
            
            // 安全漏洞检测（OWASP）
            issues.addAll(reviewTools.detectSecurity(code));
            
            // 资源泄露检测
            issues.addAll(reviewTools.detectResourceLeak(code));
            
            // 代码规范检测（补充规则）
            issues.addAll(reviewTools.detectCodeStyle(code));
            
        } catch (Exception e) {
            log.warn("本地规则检测部分失败: {}", e.getMessage());
        }
        
        return issues;
    }

    /**
     * 使用 AI 生成审查报告（单文件审查，包含代码）
     * 
     * @param code 被审查的代码
     * @param context 审查上下文
     * @param issues 检测到的问题列表
     * @return AI 生成的报告文档
     */
    private String generateAIReport(String code, String context, List<Issue> issues) {
        try {
            // 将问题列表格式化为文本
            String issuesText = formatIssuesForAI(issues);
            
            return reportGenerator.generateReport(code, context, issuesText);
            
        } catch (Exception e) {
            log.error("AI 报告生成失败: {}", e.getMessage());
            return "AI 报告生成失败: " + e.getMessage();
        }
    }
    
    /**
     * 使用 AI 生成审查报告摘要（批量审查，不包含完整代码）
     * 
     * @param fileCount 审查的文件数量
     * @param context 审查上下文
     * @param issues 检测到的问题列表
     * @return AI 生成的报告文档
     */
    private String generateAIReportSummary(int fileCount, String context, List<Issue> issues) {
        try {
            // 将问题列表格式化为文本
            String issuesText = formatIssuesForAI(issues);
            
            return reportGenerator.generateReportSummary(fileCount, context, issuesText);
            
        } catch (Exception e) {
            log.error("AI 报告生成失败: {}", e.getMessage());
            return "AI 报告生成失败: " + e.getMessage();
        }
    }
    
    /**
     * 将问题列表格式化为 AI 可读的摘要文本
     * 采用摘要形式，只保留关键信息，避免 Token 超限
     */
    private String formatIssuesForAI(List<Issue> issues) {
        if (issues.isEmpty()) {
            return "未发现明显问题。";
        }
        
        StringBuilder sb = new StringBuilder();
        
        // 1. 总体统计
        long criticalCount = issues.stream().filter(i -> i.getSeverity() == Issue.Severity.CRITICAL).count();
        long warningCount = issues.stream().filter(i -> i.getSeverity() == Issue.Severity.WARNING).count();
        long infoCount = issues.stream().filter(i -> i.getSeverity() == Issue.Severity.INFO).count();
        
        sb.append("【问题统计】\n");
        sb.append(String.format("- 严重问题: %d个\n", criticalCount));
        sb.append(String.format("- 警告问题: %d个\n", warningCount));
        sb.append(String.format("- 改进建议: %d个\n", infoCount));
        sb.append(String.format("- 总计: %d个问题\n\n", issues.size()));
        
        // 2. 按问题类型聚合（显示 Top 10 问题类型）
        sb.append("【主要问题类型】\n");
        Map<String, Long> typeCount = issues.stream()
            .collect(Collectors.groupingBy(issue -> issue.getType().name(), Collectors.counting()));
        typeCount.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
            .limit(10)
            .forEach(e -> sb.append(String.format("- %s: %d个\n", e.getKey(), e.getValue())));
        sb.append("\n");
        
        // 3. 按文件聚合问题数（显示问题最多的 Top 10 文件）
        sb.append("【问题集中文件】\n");
        Map<String, Long> fileCount = issues.stream()
            .collect(Collectors.groupingBy(Issue::getFile, Collectors.counting()));
        fileCount.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
            .limit(10)
            .forEach(e -> sb.append(String.format("- %s: %d个问题\n", e.getKey(), e.getValue())));
        sb.append("\n");
        
        // 4. 严重问题详情（最多显示 10 个）
        if (criticalCount > 0) {
            sb.append("【严重问题详情】（需优先修复）\n");
            issues.stream()
                .filter(i -> i.getSeverity() == Issue.Severity.CRITICAL)
                .limit(10)
                .forEach(i -> sb.append(formatIssueBrief(i)).append("\n"));
            if (criticalCount > 10) {
                sb.append(String.format("... 还有 %d 个严重问题\n", criticalCount - 10));
            }
            sb.append("\n");
        }
        
        // 5. 警告问题样例（最多显示 5 个）
        if (warningCount > 0) {
            sb.append("【警告问题样例】\n");
            issues.stream()
                .filter(i -> i.getSeverity() == Issue.Severity.WARNING)
                .limit(5)
                .forEach(i -> sb.append(formatIssueBrief(i)).append("\n"));
            sb.append("\n");
        }
        
        sb.append("【说明】以上为问题摘要，完整问题列表请查看详细报告。");
        
        return sb.toString();
    }
    
    /**
     * 格式化单个问题（精简版）
     */
    private String formatIssueBrief(Issue issue) {
        // 截断过长的描述
        String desc = issue.getDescription();
        if (desc.length() > 50) {
            desc = desc.substring(0, 50) + "...";
        }
        return String.format("- %s:%d [%s] %s",
                issue.getFile(),
                issue.getLine(),
                issue.getType(),
                desc);
    }

    /**
     * 生成审查摘要
     * 
     * @param issues 发现的问题列表
     * @param context 审查上下文
     * @return 摘要文本
     */
    private String generateSummary(List<Issue> issues, String context) {
        int critical = (int) issues.stream().filter(i -> i.getSeverity() == Issue.Severity.CRITICAL).count();
        int warning = (int) issues.stream().filter(i -> i.getSeverity() == Issue.Severity.WARNING).count();
        int info = (int) issues.stream().filter(i -> i.getSeverity() == Issue.Severity.INFO).count();
        
        StringBuilder summary = new StringBuilder();
        
        if (critical > 0) {
            summary.append("发现 ").append(critical).append(" 个严重问题需要立即修复；");
        }
        if (warning > 0) {
            summary.append("发现 ").append(warning).append(" 个警告建议优化；");
        }
        if (info > 0) {
            summary.append("发现 ").append(info).append(" 条改进建议。");
        }
        
        if (issues.isEmpty()) {
            summary.append("代码质量良好，未发现明显问题。");
        }
        
        return summary.toString();
    }

    // ==================== AI 报告生成接口 ====================

    /**
     * ReportGenerator 接口
     * AI 只负责汇总检测结果，生成审查报告文档
     */
    interface ReportGenerator {

        @SystemMessage("""
            你是一位资深的技术文档撰写专家，负责将代码审查结果整理成专业的审查报告。
            
            你的任务：
            1. 基于已检测出的问题列表，生成结构化的审查报告
            2. 不对代码进行新的检测，只做汇总和分析
            3. 报告应专业、清晰、可操作
            
            报告格式要求：
            
            ## 审查概览
            - 审查范围：代码变更摘要
            - 问题统计：严重/警告/建议 数量
            - 整体评价：代码质量总体评估
            
            ## 关键问题（按优先级排序）
            对每个严重和警告级别的问题：
            - 问题描述
            - 影响范围
            - 修复建议
            - 参考规范
            
            ## 改进建议
            - 代码风格优化
            - 设计模式建议
            - 性能优化方向
            
            ## 正面评价
            - 代码中的良好实践
            - 值得保持的优点
            
            注意：
            - 如果问题列表为空，给出积极的评价
            - 保持客观、建设性的语气
            - 优先关注安全和稳定性问题
            """)
        @UserMessage("""
            请基于以下检测结果生成代码审查报告：
            
            【审查上下文】
            {{context}}
            
            【代码内容】
            ```java
            {{code}}
            ```
            
            【检测到的问题】
            {{issues}}
            
            请生成完整的审查报告。
            """)
        String generateReport(@V("code") String code, @V("context") String context, @V("issues") String issues);
        
        @SystemMessage("""
            你是一位资深的技术文档撰写专家，负责将代码审查结果整理成简洁专业的审查报告摘要。
            
            你的任务：
            1. 基于已检测出的问题摘要，生成结构化的审查报告
            2. 不对代码进行新的检测，只做汇总和分析
            3. 报告应简洁、专业、突出重点
            
            报告格式要求（请严格按以下格式输出）：
            
            # 代码审查报告
            
            ## 📊 审查概览
            - **审查范围**：共审查 {{fileCount}} 个文件
            - **问题统计**：严重 X 个 / 警告 Y 个 / 建议 Z 个
            - **整体评价**：一句话评价代码质量（优秀/良好/一般/需改进）
            
            ## 🔴 严重问题（需优先修复）
            列出最多 5 个最严重的 issues：
            1. **问题类型** @ 文件名:行号 - 简要描述
            2. ...
            
            ## ⚠️ 主要问题类型
            列出问题最多的 3-5 种类型及数量
            
            ## 📁 问题集中文件
            列出问题最多的 3-5 个文件
            
            ## 💡 改进建议
            2-3 条整体性的改进建议
            
            ## ✅ 正面评价
            1-2 句积极的评价
            
            ---
            📋 **查看完整报告**：报告中包含所有问题的详细列表
            
            注意：
            - 保持简洁，重点突出严重问题
            - 如果问题列表为空，给出积极的评价
            - 保持客观、建设性的语气
            """)
        @UserMessage("""
            请基于以下问题摘要生成代码审查报告：
            
            【审查上下文】
            {{context}}
            
            【审查范围】
            共审查 {{fileCount}} 个文件
            
            【问题摘要】
            {{issues}}
            
            请生成简洁的审查报告摘要。
            """)
        String generateReportSummary(@V("fileCount") int fileCount, @V("context") String context, @V("issues") String issues);
    }
}

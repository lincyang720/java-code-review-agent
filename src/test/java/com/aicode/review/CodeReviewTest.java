package com.aicode.review;

import com.aicode.review.model.Issue;
import com.aicode.review.model.PRInfo;
import com.aicode.review.model.ReviewReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 代码审查功能测试
 *
 * @author AI Code Review
 */
class CodeReviewTest {

    /**
     * 测试审查报告生成
     */
    @Test
    void testReviewReportCreation() {
        // 创建 PR 信息
        PRInfo prInfo = PRInfo.builder()
                .provider("github")
                .owner("test-owner")
                .repo("test-repo")
                .prNumber(123)
                .title("Test PR")
                .author("test-author")
                .sourceBranch("feature/test")
                .targetBranch("main")
                .build();

        // 创建问题
        Issue issue1 = Issue.builder()
                .severity(Issue.Severity.CRITICAL)
                .type(Issue.Type.BUG)
                .file("src/main/java/Test.java")
                .line(42)
                .description("空指针异常风险")
                .suggestion("添加空检查")
                .code("if (obj != null) { obj.doSomething(); }")
                .build();

        Issue issue2 = Issue.builder()
                .severity(Issue.Severity.WARNING)
                .type(Issue.Type.QUALITY)
                .file("src/main/java/Test.java")
                .line(100)
                .description("方法过长")
                .suggestion("考虑拆分方法")
                .build();

        // 创建审查报告
        ReviewReport report = ReviewReport.builder()
                .prInfo(prInfo)
                .summary("发现 1 个严重问题，1 个警告")
                .issues(List.of(issue1, issue2))
                .build();

        // 验证
        assertNotNull(report);
        assertEquals(2, report.getIssueCount());
        assertEquals(1, report.getCriticalCount());
        assertEquals(1, report.getWarningCount());
        assertEquals(0, report.getInfoCount());
    }

    /**
     * 测试 Markdown 生成
     */
    @Test
    void testMarkdownGeneration() {
        Issue issue = Issue.builder()
                .severity(Issue.Severity.CRITICAL)
                .type(Issue.Type.BUG)
                .file("Test.java")
                .line(42)
                .description("空指针异常")
                .suggestion("添加空检查")
                .code("if (obj != null) { }")
                .build();

        ReviewReport report = ReviewReport.builder()
                .summary("发现 1 个严重问题")
                .issues(List.of(issue))
                .build();

        String markdown = report.toMarkdown();

        // 验证 Markdown 内容
        assertTrue(markdown.contains("AI 代码审查报告"));
        assertTrue(markdown.contains("🚨"));
        assertTrue(markdown.contains("空指针异常"));
        assertTrue(markdown.contains("Test.java"));
    }

    /**
     * 测试空报告
     */
    @Test
    void testEmptyReport() {
        ReviewReport report = ReviewReport.builder()
                .summary("代码审查通过，未发现问题")
                .issues(List.of())
                .build();

        assertEquals(0, report.getIssueCount());
        assertEquals(0, report.getCriticalCount());
    }

    /**
     * 测试错误报告
     */
    @Test
    void testErrorReport() {
        ReviewReport report = ReviewReport.error("API 调用失败");

        assertEquals(ReviewReport.Status.FAILED, report.getStatus());
        assertNotNull(report.getErrorMessage());
        assertTrue(report.getSummary().contains("API 调用失败"));
    }
}

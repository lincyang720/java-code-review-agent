package com.aicode.review;

import com.aicode.review.agent.ReviewAgent;
import com.aicode.review.model.Issue;
import com.aicode.review.model.ReviewReport;
import com.aicode.review.service.CodeReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 代码审查服务测试
 *
 * @author AI Code Review Team
 */
@SpringBootTest
public class CodeReviewServiceTest {

    @Autowired
    private ReviewAgent reviewAgent;

    @Autowired
    private CodeReviewService codeReviewService;

    /**
     * 测试空指针检测
     */
    @Test
    public void testNullPointerDetection() {
        String code = """
            public class Test {
                public void process(User user) {
                    String name = user.getName();
                    if (name.equals("admin")) {
                        System.out.println("Admin");
                    }
                }
            }
            """;

        ReviewReport report = reviewAgent.review(code, "测试空指针检测");

        assertNotNull(report);
        assertTrue(report.getIssueCount() > 0, "应该发现空指针问题");

        boolean foundNullPointer = report.getIssues().stream()
                .anyMatch(i -> i.getDescription().contains("空指针") ||
                              i.getDescription().contains("null"));
        assertTrue(foundNullPointer, "应该检测到空指针风险");
    }

    /**
     * 测试 SQL 注入检测
     */
    @Test
    public void testSQLInjectionDetection() {
        String code = """
            public class Test {
                public void query(String username) throws SQLException {
                    String sql = "SELECT * FROM users WHERE name = '" + username + "'";
                    Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery(sql);
                }
            }
            """;

        ReviewReport report = reviewAgent.review(code, "测试 SQL 注入检测");

        assertNotNull(report);

        boolean foundSQLInjection = report.getIssues().stream()
                .anyMatch(i -> i.getType() == Issue.Type.SECURITY);
        assertTrue(foundSQLInjection, "应该检测到 SQL 注入风险");
    }

    /**
     * 测试并发安全问题检测
     */
    @Test
    public void testConcurrencyIssue() {
        String code = """
            public class Test {
                private ArrayList<String> list = new ArrayList<>();
                private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                
                public void add(String item) {
                    list.add(item);
                }
                
                public String format(Date date) {
                    return sdf.format(date);
                }
            }
            """;

        ReviewReport report = reviewAgent.review(code, "测试并发安全检测");

        assertNotNull(report);

        boolean foundConcurrency = report.getIssues().stream()
                .anyMatch(i -> i.getDescription().contains("线程") ||
                              i.getDescription().contains("并发") ||
                              i.getDescription().contains("SimpleDateFormat"));
        assertTrue(foundConcurrency, "应该检测到并发安全问题");
    }

    /**
     * 测试 GitLab 连接
     */
    @Test
    public void testGitLabConnection() {
        String result = codeReviewService.testGitLabConnection();
        System.out.println("GitLab 连接测试结果: " + result);
        // 注意：这个测试需要实际的网络连接和配置
        assertNotNull(result);
    }

    /**
     * 测试代码审查报告生成
     */
    @Test
    public void testReviewReportGeneration() {
        String code = """
            public class Calculator {
                public int divide(int a, int b) {
                    return a / b;  // 可能除零
                }
                
                public void process(List<String> items) {
                    for (int i = 0; i <= items.size(); i++) {
                        System.out.println(items.get(i));  // 可能越界
                    }
                }
            }
            """;

        ReviewReport report = reviewAgent.review(code, "测试报告生成");

        assertNotNull(report);
        assertNotNull(report.getSummary());
        assertNotNull(report.getIssues());

        // 验证 Markdown 输出
        String markdown = report.toMarkdown();
        assertNotNull(markdown);
        assertTrue(markdown.contains("## 🔍 AI 代码审查报告"));

        System.out.println("审查报告 Markdown:");
        System.out.println(markdown);
    }
}

package com.aicode.review.agent;

import com.aicode.review.model.Issue;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码审查工具类
 * 
 * 提供各种静态代码分析工具，供 LangChain4j Agent 调用。
 */
@Slf4j
@Component
public class ReviewTools {

    private final JavaParser javaParser;

    private static final Pattern NULL_POINTER_PATTERN = Pattern.compile(
            "(\\w+)\\s*\\.\\s*\\w+.*(==\\s*null|null\\s*==)?"
    );
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(Statement|prepareStatement|createStatement).*\\+.*\\w+|executeQuery\\s*\\(.*\\+|execute\\s*\\(.*\\+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HARDCODED_SECRET_PATTERN = Pattern.compile(
            "(password|secret|key|token|api[_-]?key|access[_-]?key|private[_-]?key)\\s*[=:]\\s*[\"'][^\"']{4,}[\"']",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SYSTEM_OUT_PATTERN = Pattern.compile(
            "System\\.(out|err)\\.(print|println)"
    );
    private static final Pattern TODO_PATTERN = Pattern.compile(
            "//\\s*TODO.*|/\\*\\s*TODO.*",
            Pattern.CASE_INSENSITIVE
    );
    // 安全规则：XSS 漏洞
    private static final Pattern XSS_PATTERN = Pattern.compile(
            "(response\\.getWriter\\(\\)\\.print|out\\.print|<%=.*getParameter|innerHTML|document\\.write).*",
            Pattern.CASE_INSENSITIVE
    );
    // 安全规则：路径遍历
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
            "(new\\s+File\\s*\\(.*getParameter|FileInputStream\\s*\\(.*getParameter|getRealPath|getPathTranslated)",
            Pattern.CASE_INSENSITIVE
    );
    // 安全规则：不安全的随机数
    private static final Pattern INSECURE_RANDOM_PATTERN = Pattern.compile(
            "new\\s+Random\\s*\\(\\)|Math\\.random\\(\\)"
    );
    // 安全规则：弱加密算法
    private static final Pattern WEAK_CRYPTO_PATTERN = Pattern.compile(
            "(MessageDigest\\.getInstance\\s*\\(\\s*[\"'])(MD5|SHA1|SHA-1)",
            Pattern.CASE_INSENSITIVE
    );
    // 安全规则：不安全的反序列化
    private static final Pattern UNSAFE_DESER_PATTERN = Pattern.compile(
            "(ObjectInputStream|readObject\\s*\\(\\))",
            Pattern.CASE_INSENSITIVE
    );
    // 安全规则：不安全的 URL 跳转
    private static final Pattern OPEN_REDIRECT_PATTERN = Pattern.compile(
            "(sendRedirect\\s*\\(.*getParameter|forward\\s*\\(.*getParameter|location\\s*=.*getParameter)",
            Pattern.CASE_INSENSITIVE
    );
    // 安全规则：命令注入
    private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile(
            "(Runtime\\.getRuntime\\(\\)\\.exec|ProcessBuilder)\\s*\\(.*\\+",
            Pattern.CASE_INSENSITIVE
    );
    // 阿里巴巴规范：魔法数字
    private static final Pattern MAGIC_NUMBER_PATTERN = Pattern.compile(
            "[^a-zA-Z0-9_](-?\\d+)[^a-zA-Z0-9_].*[^=]\\s*(==|!=|>|<|>=|<=)\\s*(-?\\d+)"
    );
    // 阿里巴巴规范：异常打印
    private static final Pattern EXCEPTION_PRINT_PATTERN = Pattern.compile(
            "printStackTrace\\s*\\(\\)|e\\.printStackTrace"
    );
    // 阿里巴巴规范：线程池创建
    private static final Pattern EXECUTORS_PATTERN = Pattern.compile(
            "Executors\\.(newFixedThreadPool|newCachedThreadPool|newSingleThreadExecutor)"
    );

    public ReviewTools() {
        this.javaParser = new JavaParser();
    }

    /**
     * 检测空指针异常风险
     */
    @Tool
    public List<Issue> detectNullPointer(String code) {
        log.debug("执行空指针检测");
        List<Issue> issues = new ArrayList<>();
        
        String[] lines = code.split("\\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            
            if (trimmed.matches(".*\\w+\\.get.*\\(\\)\\.\\w+.*") && 
                !trimmed.contains("Optional") &&
                !trimmed.contains("!= null") &&
                !trimmed.contains("== null")) {
                
                issues.add(Issue.builder()
                        .severity(Issue.Severity.WARNING)
                        .type(Issue.Type.BUG)
                        .line(i + 1)
                        .description("可能存在空指针风险：链式调用未进行空值检查")
                        .suggestion("建议使用 Optional 或提前进行空值检查")
                        .code(trimmed)
                        .ruleId("NULL_POINTER_RISK")
                        .build());
            }
            
            if (trimmed.matches(".*\\(String|Object|List|Map|Set\\s+\\w+\\).*") &&
                trimmed.contains(".equals(")) {
                
                issues.add(Issue.builder()
                        .severity(Issue.Severity.WARNING)
                        .type(Issue.Type.BUG)
                        .line(i + 1)
                        .description("使用 equals() 进行字符串比较时，常量应放在前面")
                        .suggestion("建议使用 Objects.equals() 或将常量放在前面")
                        .code(trimmed)
                        .ruleId("STRING_EQUALS_RISK")
                        .build());
            }
            
            if (trimmed.contains("intValue()") || trimmed.contains("longValue()")) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.WARNING)
                        .type(Issue.Type.BUG)
                        .line(i + 1)
                        .description("自动拆箱可能导致 NullPointerException")
                        .suggestion("在使用包装类型前进行空值检查")
                        .code(trimmed)
                        .ruleId("AUTO_UNBOXING_RISK")
                        .build());
            }
        }
        
        log.debug("空指针检测完成，发现 {} 个问题", issues.size());
        return issues;
    }

    /**
     * 检测并发安全问题
     */
    @Tool
    public List<Issue> detectConcurrency(String code) {
        log.debug("执行并发安全检测");
        List<Issue> issues = new ArrayList<>();
        
        String[] lines = code.split("\\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            
            if (trimmed.matches(".*(private|protected|public)\\s+(ArrayList|HashMap|HashSet|LinkedList)\\s*<.*>\\s+\\w+.*") ||
                trimmed.matches(".*(private|protected|public)\\s+(ArrayList|HashMap|HashSet|LinkedList)\\s+\\w+.*")) {
                
                issues.add(Issue.builder()
                        .severity(Issue.Severity.WARNING)
                        .type(Issue.Type.BUG)
                        .line(i + 1)
                        .description("类成员使用非线程安全的集合")
                        .suggestion("建议使用 ConcurrentHashMap、CopyOnWriteArrayList")
                        .code(trimmed)
                        .ruleId("NON_THREAD_SAFE_COLLECTION")
                        .build());
            }
            
            if (trimmed.matches(".*(private|protected|public)\\s+SimpleDateFormat\\s+\\w+.*")) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.CRITICAL)
                        .type(Issue.Type.BUG)
                        .line(i + 1)
                        .description("SimpleDateFormat 不是线程安全的")
                        .suggestion("使用 ThreadLocal<DateFormat> 或 DateTimeFormatter")
                        .code(trimmed)
                        .ruleId("SIMPLE_DATE_FORMAT_THREAD_UNSAFE")
                        .build());
            }
            
            if (trimmed.matches(".*public\\s+synchronized\\s+.*")) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.INFO)
                        .type(Issue.Type.QUALITY)
                        .line(i + 1)
                        .description("方法级别 synchronized 可能导致性能问题")
                        .suggestion("考虑使用更细粒度的锁")
                        .code(trimmed)
                        .ruleId("BROAD_SYNCHRONIZED")
                        .build());
            }
        }
        
        log.debug("并发安全检测完成，发现 {} 个问题", issues.size());
        return issues;
    }

    /**
     * 检测安全漏洞（OWASP Top 10）
     */
    @Tool
    public List<Issue> detectSecurity(String code) {
        log.debug("执行安全漏洞检测");
        List<Issue> issues = new ArrayList<>();
        
        String[] lines = code.split("\\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            
            // SQL 注入检测
            Matcher sqlMatcher = SQL_INJECTION_PATTERN.matcher(line);
            if (sqlMatcher.find()) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.CRITICAL)
                        .type(Issue.Type.SECURITY)
                        .line(i + 1)
                        .description("【SQL 注入】字符串拼接 SQL 语句，存在 SQL 注入风险")
                        .suggestion("使用 PreparedStatement 预编译语句，参数用 ? 占位")
                        .code(trimmed)
                        .ruleId("SQL_INJECTION")
                        .build());
            }
            
            // 硬编码密钥检测
            Matcher secretMatcher = HARDCODED_SECRET_PATTERN.matcher(line);
            if (secretMatcher.find()) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.CRITICAL)
                        .type(Issue.Type.SECURITY)
                        .line(i + 1)
                        .description("【敏感信息泄露】代码中硬编码了密码/密钥等敏感信息")
                        .suggestion("使用环境变量、配置文件或密钥管理服务（如 Vault、KMS）")
                        .code("***")
                        .ruleId("HARDCODED_SECRET")
                        .build());
            }
            
            // XSS 漏洞检测
            Matcher xssMatcher = XSS_PATTERN.matcher(line);
            if (xssMatcher.find()) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.CRITICAL)
                        .type(Issue.Type.SECURITY)
                        .line(i + 1)
                        .description("【XSS 漏洞】用户输入直接输出到页面，存在跨站脚本攻击风险")
                        .suggestion("使用 OWASP Java Encoder 或框架自带的转义功能")
                        .code(trimmed)
                        .ruleId("XSS_VULNERABILITY")
                        .build());
            }
            
            // 路径遍历检测
            Matcher pathMatcher = PATH_TRAVERSAL_PATTERN.matcher(line);
            if (pathMatcher.find()) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.CRITICAL)
                        .type(Issue.Type.SECURITY)
                        .line(i + 1)
                        .description("【路径遍历】用户输入直接用于文件路径，可能导致目录遍历攻击")
                        .suggestion("校验文件路径，使用白名单限制访问目录，移除 ../ 等特殊字符")
                        .code(trimmed)
                        .ruleId("PATH_TRAVERSAL")
                        .build());
            }
            
            // 不安全的随机数
            Matcher randomMatcher = INSECURE_RANDOM_PATTERN.matcher(line);
            if (randomMatcher.find()) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.WARNING)
                        .type(Issue.Type.SECURITY)
                        .line(i + 1)
                        .description("【不安全的随机数】Random/Math.random 不适用于安全场景")
                        .suggestion("使用 SecureRandom 生成安全随机数")
                        .code(trimmed)
                        .ruleId("INSECURE_RANDOM")
                        .build());
            }
            
            // 弱加密算法
            Matcher cryptoMatcher = WEAK_CRYPTO_PATTERN.matcher(line);
            if (cryptoMatcher.find()) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.CRITICAL)
                        .type(Issue.Type.SECURITY)
                        .line(i + 1)
                        .description("【弱加密算法】使用 MD5/SHA1 已被破解，不安全")
                        .suggestion("使用 SHA-256、SHA-3 或 bcrypt/Argon2 等强哈希算法")
                        .code(trimmed)
                        .ruleId("WEAK_CRYPTO")
                        .build());
            }
            
            // 不安全的反序列化
            Matcher deserMatcher = UNSAFE_DESER_PATTERN.matcher(line);
            if (deserMatcher.find()) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.CRITICAL)
                        .type(Issue.Type.SECURITY)
                        .line(i + 1)
                        .description("【反序列化漏洞】ObjectInputStream.readObject 存在远程代码执行风险")
                        .suggestion("使用 JSON（Jackson/Gson）或 Protocol Buffers 替代 Java 序列化")
                        .code(trimmed)
                        .ruleId("UNSAFE_DESERIALIZATION")
                        .build());
            }
            
            // URL 跳转漏洞
            Matcher redirectMatcher = OPEN_REDIRECT_PATTERN.matcher(line);
            if (redirectMatcher.find()) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.WARNING)
                        .type(Issue.Type.SECURITY)
                        .line(i + 1)
                        .description("【开放重定向】用户输入直接用于跳转地址，可能导致钓鱼攻击")
                        .suggestion("使用白名单校验跳转地址，或使用内部映射表")
                        .code(trimmed)
                        .ruleId("OPEN_REDIRECT")
                        .build());
            }
            
            // 命令注入
            Matcher cmdMatcher = COMMAND_INJECTION_PATTERN.matcher(line);
            if (cmdMatcher.find()) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.CRITICAL)
                        .type(Issue.Type.SECURITY)
                        .line(i + 1)
                        .description("【命令注入】用户输入拼接执行系统命令，存在远程代码执行风险")
                        .suggestion("避免执行系统命令，如需执行使用参数化方式并严格校验")
                        .code(trimmed)
                        .ruleId("COMMAND_INJECTION")
                        .build());
            }
        }
        
        log.debug("安全漏洞检测完成，发现 {} 个问题", issues.size());
        return issues;
    }

    /**
     * 分析代码复杂度
     */
    @Tool
    public Map<String, Object> analyzeComplexity(String code) {
        log.debug("执行代码复杂度分析");
        Map<String, Object> result = new HashMap<>();
        
        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(new StringReader(code));
            
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                
                List<Map<String, Object>> methods = new ArrayList<>();
                cu.findAll(MethodDeclaration.class).forEach(method -> {
                    Map<String, Object> methodInfo = new HashMap<>();
                    methodInfo.put("name", method.getNameAsString());
                    methodInfo.put("lineCount", calculateMethodLines(method));
                    methodInfo.put("cyclomaticComplexity", estimateCyclomaticComplexity(method));
                    methods.add(methodInfo);
                });
                
                result.put("methods", methods);
                result.put("totalMethods", methods.size());
                
            } else {
                result.put("totalLines", code.split("\\n").length);
            }
        } catch (Exception e) {
            log.warn("代码解析失败: {}", e.getMessage());
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * 检测资源泄露风险
     */
    @Tool
    public List<Issue> detectResourceLeak(String code) {
        log.debug("执行资源泄露检测");
        List<Issue> issues = new ArrayList<>();

        String[] lines = code.split("\\n");

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();

            // 检测未用 try-with-resources 的流/连接
            if ((trimmed.contains("new FileInputStream") || trimmed.contains("new FileOutputStream")
                    || trimmed.contains("new BufferedReader") || trimmed.contains("new Connection")
                    || trimmed.contains("getConnection()"))
                    && !trimmed.startsWith("try") && !trimmed.contains("try (")) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.WARNING)
                        .type(Issue.Type.BUG)
                        .line(i + 1)
                        .description("资源未使用 try-with-resources 管理，可能导致资源泄露")
                        .suggestion("使用 try-with-resources 语句确保资源自动关闭")
                        .code(trimmed)
                        .ruleId("RESOURCE_LEAK")
                        .build());
            }

            // 检测 finally 块中未关闭资源（没有在 try-with-resources 中）
            if (trimmed.contains(".close()") && !trimmed.startsWith("//")) {
                // 在 finally 手动关闭是一种信号，可能已经有资源泄露风险
                issues.add(Issue.builder()
                        .severity(Issue.Severity.INFO)
                        .type(Issue.Type.QUALITY)
                        .line(i + 1)
                        .description("手动关闭资源，建议改为 try-with-resources")
                        .suggestion("Java 7+ 支持 try-with-resources，可自动关闭 AutoCloseable 资源")
                        .code(trimmed)
                        .ruleId("MANUAL_CLOSE")
                        .build());
            }
        }

        log.debug("资源泄露检测完成，发现 {} 个问题", issues.size());
        return issues;
    }

    /**
     * 检测代码规范问题（阿里巴巴 Java 开发规范）
     */
    @Tool
    public List<Issue> detectCodeStyle(String code) {
        log.debug("执行代码规范检测");
        List<Issue> issues = new ArrayList<>();
        
        String[] lines = code.split("\\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            
            // System.out 检测
            Matcher sysOutMatcher = SYSTEM_OUT_PATTERN.matcher(line);
            if (sysOutMatcher.find()) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.INFO)
                        .type(Issue.Type.QUALITY)
                        .line(i + 1)
                        .description("【日志规范】使用 System.out.println 输出日志")
                        .suggestion("使用 SLF4J + Logback 等日志框架，便于统一管理和级别控制")
                        .code(trimmed)
                        .ruleId("SYSTEM_OUT_PRINT")
                        .build());
            }
            
            // TODO 标记检测
            Matcher todoMatcher = TODO_PATTERN.matcher(line);
            if (todoMatcher.find()) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.INFO)
                        .type(Issue.Type.QUALITY)
                        .line(i + 1)
                        .description("【代码管理】代码中包含 TODO 标记")
                        .suggestion("确保在合并前完成 TODO 事项，或创建 Issue 跟踪")
                        .code(trimmed)
                        .ruleId("TODO_MARKER")
                        .build());
            }
            
            // 魔法数字检测
            Matcher magicMatcher = MAGIC_NUMBER_PATTERN.matcher(line);
            if (magicMatcher.find() && !trimmed.contains("//")) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.INFO)
                        .type(Issue.Type.QUALITY)
                        .line(i + 1)
                        .description("【命名规范】使用魔法数字，可读性差")
                        .suggestion("将数字提取为具名常量，如 MAX_RETRY_COUNT = 3")
                        .code(trimmed)
                        .ruleId("MAGIC_NUMBER")
                        .build());
            }
            
            // 异常打印检测
            if (EXCEPTION_PRINT_PATTERN.matcher(line).find()) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.WARNING)
                        .type(Issue.Type.QUALITY)
                        .line(i + 1)
                        .description("【异常处理】使用 printStackTrace() 打印异常")
                        .suggestion("使用日志框架记录异常，如 log.error(\"操作失败\", e)")
                        .code(trimmed)
                        .ruleId("EXCEPTION_PRINT")
                        .build());
            }
            
            // Executors 创建线程池检测
            if (EXECUTORS_PATTERN.matcher(line).find()) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.WARNING)
                        .type(Issue.Type.QUALITY)
                        .line(i + 1)
                        .description("【并发规范】使用 Executors 创建线程池")
                        .suggestion("使用 ThreadPoolExecutor 手动创建，明确指定队列类型和拒绝策略")
                        .code(trimmed)
                        .ruleId("EXECUTORS_USAGE")
                        .build());
            }
            
            // 大括号换行检测（简单判断）
            if (trimmed.matches(".*\\{\\s*") && !trimmed.startsWith("//") && !trimmed.startsWith("*")) {
                // 检查是否是行尾大括号
                if (!trimmed.equals("{") && !trimmed.endsWith("{")) {
                    issues.add(Issue.builder()
                            .severity(Issue.Severity.INFO)
                            .type(Issue.Type.STYLE)
                            .line(i + 1)
                            .description("【代码格式】大括号前缺少空格")
                            .suggestion("大括号前加空格，如 if (condition) {")
                            .code(trimmed)
                            .ruleId("BRACE_FORMAT")
                            .build());
                }
            }
            
            // 行宽检测
            if (line.length() > 120) {
                issues.add(Issue.builder()
                        .severity(Issue.Severity.INFO)
                        .type(Issue.Type.STYLE)
                        .line(i + 1)
                        .description("【代码格式】行宽超过 120 字符")
                        .suggestion("换行或提取变量，保持代码可读性")
                        .code(trimmed.substring(0, Math.min(50, trimmed.length())) + "...")
                        .ruleId("LINE_TOO_LONG")
                        .build());
            }
        }
        
        return issues;
    }

    private int calculateMethodLines(MethodDeclaration method) {
        if (method.getBody().isPresent()) {
            return method.getBody().get().toString().split("\\n").length;
        }
        return 0;
    }

    private int estimateCyclomaticComplexity(MethodDeclaration method) {
        int complexity = 1;
        String methodBody = method.toString();
        complexity += countOccurrences(methodBody, "if");
        complexity += countOccurrences(methodBody, "while");
        complexity += countOccurrences(methodBody, "for");
        complexity += countOccurrences(methodBody, "case");
        complexity += countOccurrences(methodBody, "catch");
        return complexity;
    }

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}

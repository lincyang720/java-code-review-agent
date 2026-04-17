package com.aicode.review.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 代码解析工具
 *
 * 使用 JavaParser 解析 Java 代码，提取代码结构信息。
 *
 * @author AI Code Review Team
 */
@Slf4j
@Component
public class CodeParser {

    private final JavaParser javaParser;

    public CodeParser() {
        this.javaParser = new JavaParser();
    }

    /**
     * 解析 Java 代码
     *
     * @param code Java 代码字符串
     * @return 解析结果
     */
    public ParseResult<CompilationUnit> parse(String code) {
        try {
            return javaParser.parse(new StringReader(code));
        } catch (Exception e) {
            log.error("代码解析失败", e);
            return null;
        }
    }

    /**
     * 提取类信息
     *
     * @param code Java 代码
     * @return 类信息 Map
     */
    public Map<String, Object> extractClassInfo(String code) {
        Map<String, Object> info = new HashMap<>();

        ParseResult<CompilationUnit> result = parse(code);
        if (result == null || !result.isSuccessful() || result.getResult().isEmpty()) {
            info.put("error", "解析失败");
            return info;
        }

        CompilationUnit cu = result.getResult().get();

        // 包名
        cu.getPackageDeclaration().ifPresent(pkg ->
            info.put("package", pkg.getNameAsString())
        );

        // 类名
        cu.getPrimaryTypeName().ifPresent(name ->
            info.put("className", name)
        );

        // 方法列表
        List<String> methods = cu.findAll(MethodDeclaration.class).stream()
                .map(MethodDeclaration::getNameAsString)
                .collect(Collectors.toList());
        info.put("methods", methods);
        info.put("methodCount", methods.size());

        // 导入语句
        List<String> imports = cu.getImports().stream()
                .map(i -> i.getNameAsString())
                .collect(Collectors.toList());
        info.put("imports", imports);

        // 注释数量
        long commentCount = cu.getAllContainedComments().size();
        info.put("commentCount", commentCount);

        return info;
    }

    /**
     * 计算方法复杂度指标
     *
     * @param code Java 代码
     * @return 复杂度指标
     */
    public Map<String, Object> calculateMetrics(String code) {
        Map<String, Object> metrics = new HashMap<>();

        ParseResult<CompilationUnit> result = parse(code);
        if (result == null || !result.isSuccessful()) {
            metrics.put("error", "解析失败");
            return metrics;
        }

        CompilationUnit cu = result.getResult().orElse(null);
        if (cu == null) {
            metrics.put("error", "无解析结果");
            return metrics;
        }

        // 方法数量
        int methodCount = cu.findAll(MethodDeclaration.class).size();
        metrics.put("methodCount", methodCount);

        // 代码行数（粗略统计）
        int lines = code.split("\\n").length;
        metrics.put("totalLines", lines);

        // 非空行数
        long nonEmptyLines = code.lines()
                .filter(line -> !line.trim().isEmpty())
                .count();
        metrics.put("nonEmptyLines", nonEmptyLines);

        // 注释行数
        long commentLines = cu.getAllContainedComments().stream()
                .mapToInt(c -> c.getContent().split("\\n").length)
                .sum();
        metrics.put("commentLines", commentLines);

        // 注释率
        double commentRatio = lines > 0 ? (double) commentLines / lines : 0;
        metrics.put("commentRatio", String.format("%.2f%%", commentRatio * 100));

        return metrics;
    }

    /**
     * 提取方法列表及基本信息
     *
     * @param code Java 代码
     * @return 方法信息列表
     */
    public List<Map<String, Object>> extractMethods(String code) {
        ParseResult<CompilationUnit> result = parse(code);
        if (result == null || !result.isSuccessful()) {
            return List.of();
        }

        CompilationUnit cu = result.getResult().orElse(null);
        if (cu == null) {
            return List.of();
        }

        return cu.findAll(MethodDeclaration.class).stream()
                .map(method -> {
                    Map<String, Object> methodInfo = new HashMap<>();
                    methodInfo.put("name", method.getNameAsString());
                    methodInfo.put("returnType", method.getType().asString());
                    methodInfo.put("parameters", method.getParameters().size());
                    methodInfo.put("modifiers", method.getModifiers().toString());

                    // 方法行数（如果方法体存在）
                    method.getBody().ifPresent(body -> {
                        int lines = body.toString().split("\\n").length;
                        methodInfo.put("lineCount", lines);
                    });

                    return methodInfo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 判断代码是否为有效的 Java 代码
     *
     * @param code 代码字符串
     * @return 是否有效
     */
    public boolean isValidJavaCode(String code) {
        ParseResult<CompilationUnit> result = parse(code);
        return result != null && result.isSuccessful();
    }
}

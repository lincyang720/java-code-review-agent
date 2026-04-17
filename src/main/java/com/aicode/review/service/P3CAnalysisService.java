package com.aicode.review.service;

import com.aicode.review.model.Issue;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.pmd.PMD;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.Rule;
import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.RuleSet;
import net.sourceforge.pmd.RuleSetFactory;
import net.sourceforge.pmd.RuleSetNotFoundException;
import net.sourceforge.pmd.RuleSets;
import net.sourceforge.pmd.RuleViolation;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;
import net.sourceforge.pmd.util.datasource.DataSource;
import net.sourceforge.pmd.util.datasource.FileDataSource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Alibaba P3C 代码规范分析服务
 * <p>
 * 基于 P3C-PMD 规则库进行代码规范检测，覆盖阿里巴巴 Java 开发手册的全部规范。
 *
 * @author AI Code Review Team
 */
@Slf4j
@Service
public class P3CAnalysisService {

    private final RuleSets p3cRuleSets;
    private final RuleSetFactory ruleSetFactory;

    public P3CAnalysisService() {
        this.ruleSetFactory = new RuleSetFactory();
        this.p3cRuleSets = loadP3CRuleSets();
    }

    /**
     * 加载 P3C 规则集
     */
    private RuleSets loadP3CRuleSets() {
        try {
            // P3C 规则分类 - 从 p3c-pmd jar 包中加载
            String[] ruleSetPaths = {
                "rulesets/java/ali-comment.xml",
                "rulesets/java/ali-concurrent.xml", 
                "rulesets/java/ali-constant.xml",
                "rulesets/java/ali-exception.xml",
                "rulesets/java/ali-flowcontrol.xml",
                "rulesets/java/ali-naming.xml",
                "rulesets/java/ali-oop.xml",
                "rulesets/java/ali-orm.xml",
                "rulesets/java/ali-other.xml",
                "rulesets/java/ali-set.xml"
            };
            
            List<RuleSet> ruleSets = new ArrayList<>();
            
            for (String ruleSetPath : ruleSetPaths) {
                try {
                    RuleSet ruleSet = ruleSetFactory.createRuleSet(ruleSetPath);
                    ruleSets.add(ruleSet);
                    log.debug("加载 P3C 规则集: {}", ruleSetPath);
                } catch (RuleSetNotFoundException e) {
                    log.warn("找不到规则集文件: {}", ruleSetPath);
                } catch (Exception e) {
                    log.warn("加载规则集失败: {} - {}", ruleSetPath, e.getMessage());
                }
            }
            
            RuleSets result = new RuleSets(ruleSets);
            log.info("P3C 规则集加载完成，共 {} 条规则", result.getAllRules().size());
            return result;
            
        } catch (Exception e) {
            log.error("加载 P3C 规则集失败", e);
            return new RuleSets();
        }
    }

    /**
     * 分析代码字符串
     *
     * @param code     Java 代码字符串
     * @param fileName 文件名（用于报告）
     * @return 发现的问题列表
     */
    public List<Issue> analyze(String code, String fileName) {
        List<Issue> issues = new ArrayList<>();
        
        if (p3cRuleSets.getAllRules().isEmpty()) {
            log.warn("P3C 规则集未加载，跳过分析");
            return issues;
        }
        
        // 检查代码是否适合 P3C 分析
        CodeValidationResult validation = validateCodeForP3C(code);
        if (!validation.isValid()) {
            log.debug("代码不适合 P3C 分析: {}", validation.getReason());
            // 记录到报告中
            issues.add(Issue.builder()
                    .severity(Issue.Severity.INFO)
                    .type(Issue.Type.QUALITY)
                    .file(fileName)
                    .line(0)
                    .description("【P3C分析跳过】" + validation.getReason())
                    .suggestion("P3C 规则检测需要完整的 Java 类文件。如需完整规范检测，请确保提交的是完整文件而非代码片段。")
                    .ruleId("P3C_SKIPPED")
                    .build());
            return issues;
        }
        
        Path tempFile = null;
        try {
            // 创建临时文件
            tempFile = Files.createTempFile("p3c-analysis-", ".java");
            Files.writeString(tempFile, code);
            
            // 执行 PMD 分析
            issues = analyzeFile(tempFile.toFile(), fileName);
            
        } catch (Exception e) {
            log.error("P3C 分析失败", e);
            // 记录分析失败到报告中
            issues.add(Issue.builder()
                    .severity(Issue.Severity.INFO)
                    .type(Issue.Type.QUALITY)
                    .file(fileName)
                    .line(0)
                    .description("【P3C分析失败】" + e.getMessage())
                    .suggestion("P3C 分析过程中发生错误，但其他检测（自定义规则、AI分析）仍会正常执行。")
                    .ruleId("P3C_ERROR")
                    .build());
        } finally {
            // 清理临时文件
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("删除临时文件失败", e);
                }
            }
        }
        
        return issues;
    }
    
    /**
     * 验证代码是否适合 P3C 分析
     */
    private CodeValidationResult validateCodeForP3C(String code) {
        if (code == null || code.trim().isEmpty()) {
            return new CodeValidationResult(false, "代码为空");
        }
        
        String trimmed = code.trim();
        
        // 检查是否是 diff 格式（包含 package 但没有类定义）
        if (trimmed.startsWith("package ")) {
            // 检查是否有类/接口/枚举定义
            boolean hasTypeDef = trimmed.contains(" class ") || 
                                trimmed.contains(" interface ") || 
                                trimmed.contains(" enum ") ||
                                trimmed.contains("\nclass ") ||
                                trimmed.contains("\ninterface ") ||
                                trimmed.contains("\nenum ");
            if (!hasTypeDef) {
                return new CodeValidationResult(false, "代码片段包含 package 声明但缺少类定义，可能是 diff 片段");
            }
        }
        
        // 检查是否有类定义
        boolean hasClassDef = trimmed.contains(" class ") || 
                             trimmed.contains(" interface ") || 
                             trimmed.contains(" enum ") ||
                             trimmed.contains("\nclass ") ||
                             trimmed.contains("\ninterface ") ||
                             trimmed.contains("\nenum ");
        if (!hasClassDef) {
            return new CodeValidationResult(false, "代码缺少类/接口/枚举定义，无法应用 P3C 规范检测");
        }
        
        return new CodeValidationResult(true, null);
    }
    
    /**
     * 代码验证结果
     */
    private static class CodeValidationResult {
        private final boolean valid;
        private final String reason;
        
        public CodeValidationResult(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getReason() {
            return reason;
        }
    }

    /**
     * 分析单个文件
     */
    private List<Issue> analyzeFile(File file, String fileName) {
        List<Issue> issues = new ArrayList<>();
        
        try {
            PMDConfiguration configuration = new PMDConfiguration();
            configuration.setDefaultLanguageVersion(
                    LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("17")
            );
            
            RuleContext ctx = new RuleContext();
            Report report = new Report();
            ctx.setReport(report);
            ctx.setSourceCodeFilename(fileName);
            LanguageVersion version = LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getVersion("17");
            ctx.setLanguageVersion(version);
            
            // 创建数据源
            DataSource dataSource = new FileDataSource(file);
            
            // 运行 PMD 分析
            PMD pmd = new PMD(configuration);
            pmd.getSourceCodeProcessor().processSourceCode(
                dataSource.getInputStream(), 
                p3cRuleSets, 
                ctx
            );
            
            // 转换结果
            for (RuleViolation violation : report) {
                Issue issue = convertViolation(violation);
                if (issue != null) {
                    issues.add(issue);
                }
            }
            
            log.info("P3C 分析完成 [{}]，发现 {} 个问题", fileName, issues.size());
            
        } catch (Exception e) {
            log.error("PMD 分析失败", e);
        }
        
        return issues;
    }

    /**
     * 将 PMD 违规转换为 Issue
     */
    private Issue convertViolation(RuleViolation violation) {
        try {
            Rule rule = violation.getRule();
            String ruleName = rule.getName();
            String description = violation.getDescription();
            
            // 映射严重程度
            Issue.Severity severity = mapP3CSeverity(ruleName);
            
            // 映射问题类型
            Issue.Type type = mapP3CType(ruleName);
            
            return Issue.builder()
                    .severity(severity)
                    .type(type)
                    .file(violation.getFilename())
                    .line(violation.getBeginLine())
                    .description("【P3C规范】" + description)
                    .suggestion(getP3CSuggestion(ruleName))
                    .ruleId("P3C_" + ruleName)
                    .build();
                    
        } catch (Exception e) {
            log.warn("转换违规记录失败", e);
            return null;
        }
    }

    /**
     * 映射 P3C 严重程度
     */
    private Issue.Severity mapP3CSeverity(String ruleName) {
        // 严重级别规则（强制）
        String[] criticalRules = {
            "AvoidApacheBeanUtilsCopy",
            "AvoidCallStaticSimpleDateFormat", 
            "AvoidNewDateGetTime",
            "AvoidUseTimer",
            "ClassMustHaveAuthor",
            "ConcurrentExceptionWithModifyOriginSubList",
            "DontModifyInForeachCircle",
            "EqualsAvoidNull",
            "IbatisMethodQueryForList",
            "LowerCamelCaseVariableNaming",
            "NeedBrace",
            "PackageNaming",
            "PojoMustOverrideToString",
            "PojoNoDefaultValue",
            "StringConcat",
            "TestClassShouldEndWithTest",
            "UpperCamelCaseClassNaming"
        };
        
        for (String criticalRule : criticalRules) {
            if (ruleName.contains(criticalRule)) {
                return Issue.Severity.CRITICAL;
            }
        }
        
        return Issue.Severity.WARNING;
    }

    /**
     * 映射 P3C 问题类型
     */
    private Issue.Type mapP3CType(String ruleName) {
        if (ruleName.contains("Naming") || ruleName.contains("Case")) {
            return Issue.Type.STYLE;
        }
        if (ruleName.contains("Exception")) {
            return Issue.Type.BUG;
        }
        if (ruleName.contains("Concurrent") || ruleName.contains("Thread") || ruleName.contains("Lock")) {
            return Issue.Type.BUG;
        }
        if (ruleName.contains("Comment") || ruleName.contains("Author")) {
            return Issue.Type.QUALITY;
        }
        return Issue.Type.QUALITY;
    }

    /**
     * 获取 P3C 规则建议
     */
    private String getP3CSuggestion(String ruleName) {
        return switch (ruleName) {
            case "LowerCamelCaseVariableNaming" -> 
                "变量名应使用 lowerCamelCase 风格，如：localValue、getHttpMessage";
            case "UpperCamelCaseClassNaming" -> 
                "类名应使用 UpperCamelCase 风格，如：XmlService、TcpUdpDeal";
            case "PackageNaming" -> 
                "包名统一使用小写，点分隔符之间有且仅有一个自然语义的英语单词";
            case "ClassMustHaveAuthor" -> 
                "类注释必须包含 @author 标签，标明作者信息";
            case "AvoidApacheBeanUtilsCopy" -> 
                "避免使用 Apache BeanUtils 进行属性拷贝，推荐使用 Spring BeanUtils 或 MapStruct";
            case "DontModifyInForeachCircle" -> 
                "不要在 foreach 循环里进行元素的 remove/add 操作，使用 Iterator 或 removeIf";
            case "NeedBrace" -> 
                "if/for/while/do 语句必须使用大括号，即使只有一行代码";
            case "EqualsAvoidNull" -> 
                "调用 equals 方法时，常量应放在前面，避免空指针：\"expected\".equals(variable)";
            case "StringConcat" -> 
                "循环体内字符串拼接使用 StringBuilder，避免创建大量临时对象";
            case "AvoidCallStaticSimpleDateFormat" -> 
                "SimpleDateFormat 是线程不安全的，不要定义为 static 变量，使用 DateTimeFormatter";
            case "PojoMustOverrideToString" -> 
                "POJO 类必须重写 toString 方法，便于日志排查问题";
            case "IbatisMethodQueryForList" -> 
                "iBATIS 自带的 queryForList(String statementName, int start, int size) 不推荐使用";
            case "AvoidNewDateGetTime" -> 
                "获取当前毫秒数：System.currentTimeMillis() 而不是 new Date().getTime()";
            case "ConcurrentExceptionWithModifyOriginSubList" -> 
                "ArrayList 的 subList 返回的是原列表的视图，修改会导致 ConcurrentModificationException";
            default -> "参考阿里巴巴 Java 开发手册：https://github.com/alibaba/p3c";
        };
    }

    /**
     * 获取已加载的规则数量
     */
    public int getRuleCount() {
        return p3cRuleSets.getAllRules().size();
    }

    /**
     * 检查 P3C 是否可用
     */
    public boolean isAvailable() {
        return !p3cRuleSets.getAllRules().isEmpty();
    }
}

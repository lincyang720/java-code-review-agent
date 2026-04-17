package com.aicode.review.client;

import com.aicode.review.config.StaticAnalysisConfig;
import com.aicode.review.model.Issue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * SpotBugs 执行器
 * 
 * 用于运行 SpotBugs 静态分析并解析结果
 */
@Slf4j
@Component
public class SpotBugsRunner {
    
    private final StaticAnalysisConfig config;
    
    public SpotBugsRunner(StaticAnalysisConfig config) {
        this.config = config;
    }
    
    /**
     * 检查 SpotBugs 是否启用
     */
    public boolean isEnabled() {
        return config.getSpotbugs().isEnabled();
    }
    
    /**
     * 运行 SpotBugs 分析
     * 
     * @param classPath 类文件路径
     * @param sourcePath 源代码路径（可选）
     * @return 发现的问题列表
     */
    public List<Issue> analyze(String classPath, String sourcePath) {
        List<Issue> issues = new ArrayList<>();
        
        if (!isEnabled()) {
            log.debug("SpotBugs 未启用");
            return issues;
        }
        
        Path outputXml = null;
        try {
            // 创建临时输出文件
            outputXml = Files.createTempFile("spotbugs-", ".xml");
            
            // 构建命令
            List<String> command = new ArrayList<>();
            command.add(config.getSpotbugs().getExecutablePath());
            command.add("-textui");
            command.add("-effort:" + config.getSpotbugs().getEffort());
            command.add("-threshold:" + config.getSpotbugs().getThreshold());
            command.add("-xml");
            command.add("-output");
            command.add(outputXml.toString());
            command.add(classPath);
            
            // 执行命令
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // 读取输出
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("SpotBugs: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0 && exitCode != 1) { // 1 表示发现了 bug
                log.warn("SpotBugs 执行返回非零退出码: {}", exitCode);
            }
            
            // 解析结果
            if (Files.exists(outputXml)) {
                issues = parseXmlReport(outputXml.toFile());
            }
            
        } catch (Exception e) {
            log.error("运行 SpotBugs 分析失败", e);
        } finally {
            // 清理临时文件
            if (outputXml != null) {
                try {
                    Files.deleteIfExists(outputXml);
                } catch (Exception e) {
                    log.warn("删除临时文件失败", e);
                }
            }
        }
        
        return issues;
    }
    
    /**
     * 解析 SpotBugs XML 报告
     */
    private List<Issue> parseXmlReport(File xmlFile) {
        List<Issue> issues = new ArrayList<>();
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);
            
            NodeList bugInstances = doc.getElementsByTagName("BugInstance");
            
            for (int i = 0; i < bugInstances.getLength(); i++) {
                Element bug = (Element) bugInstances.item(i);
                
                String type = bug.getAttribute("type");
                String category = bug.getAttribute("category");
                String priority = bug.getAttribute("priority");
                
                // 获取源码位置
                NodeList sourceLines = bug.getElementsByTagName("SourceLine");
                String fileName = "";
                int lineNumber = 0;
                
                if (sourceLines.getLength() > 0) {
                    Element sourceLine = (Element) sourceLines.item(0);
                    fileName = sourceLine.getAttribute("sourcepath");
                    String startLine = sourceLine.getAttribute("start");
                    if (!startLine.isEmpty()) {
                        lineNumber = Integer.parseInt(startLine);
                    }
                }
                
                // 获取消息
                NodeList messages = bug.getElementsByTagName("ShortMessage");
                String message = type;
                if (messages.getLength() > 0) {
                    message = messages.item(0).getTextContent();
                }
                
                Issue issue = Issue.builder()
                        .severity(mapPriority(priority))
                        .type(mapCategory(category))
                        .file(fileName)
                        .line(lineNumber)
                        .description("【SpotBugs】" + message)
                        .suggestion("参考 SpotBugs 文档: https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html")
                        .ruleId("SPOTBUGS_" + type)
                        .build();
                
                issues.add(issue);
            }
            
            log.info("SpotBugs 发现 {} 个问题", issues.size());
            
        } catch (Exception e) {
            log.error("解析 SpotBugs 报告失败", e);
        }
        
        return issues;
    }
    
    private Issue.Severity mapPriority(String priority) {
        return switch (priority) {
            case "1" -> Issue.Severity.CRITICAL;  // High
            case "2" -> Issue.Severity.WARNING;   // Medium
            case "3", "4", "5" -> Issue.Severity.INFO; // Low
            default -> Issue.Severity.WARNING;
        };
    }
    
    private Issue.Type mapCategory(String category) {
        return switch (category.toUpperCase()) {
            case "CORRECTNESS", "MT_CORRECTNESS" -> Issue.Type.BUG;
            case "SECURITY" -> Issue.Type.SECURITY;
            case "PERFORMANCE" -> Issue.Type.PERFORMANCE;
            case "STYLE", "BAD_PRACTICE", "I18N", "EXPERIMENTAL" -> Issue.Type.QUALITY;
            default -> Issue.Type.QUALITY;
        };
    }
}

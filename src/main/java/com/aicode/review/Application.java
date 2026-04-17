package com.aicode.review;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 代码审查 Agent 应用程序入口
 *
 * 基于 Spring AI + LangChain4j 构建的智能代码审查系统
 * 支持 GitHub/GitLab Webhook 集成，自动对 Pull Request 进行代码审查
 *
 * @author AI Code Review Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableAsync  // 启用异步处理，用于异步执行代码审查任务
public class Application {

    /**
     * 应用程序入口方法
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 在 Spring 启动前加载 .env 文件
        loadDotEnv();

        SpringApplication.run(Application.class, args);
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║     Java Code Review Agent 启动成功！                    ║");
        System.out.println("║     基于 Spring AI + LangChain4j 的智能代码审查系统        ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
    }

    /**
     * 加载 .env 文件到系统环境变量
     * 在 Spring 上下文创建前执行，确保配置类能读取到变量
     * 
     * 支持两种模式：
     * 1. 开发模式：从项目根目录加载 .env
     * 2. JAR 运行模式：从 JAR 所在目录加载 .env
     */
    private static void loadDotEnv() {
        try {
            // 尝试多个可能的 .env 文件位置
            String[] possiblePaths = {
                    ".",                          // 当前目录
                    "..",                         // 上级目录
                    System.getProperty("user.dir") // 用户工作目录
            };

            boolean loaded = false;
            for (String path : possiblePaths) {
                java.io.File envFile = new java.io.File(path, ".env");
                if (envFile.exists()) {
                    Dotenv dotenv = Dotenv.configure()
                            .directory(path)
                            .ignoreIfMissing()
                            .ignoreIfMalformed()
                            .load();

                    dotenv.entries().forEach(entry -> {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        // 只设置尚未存在的环境变量
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, value);
                        }
                    });

                    System.out.println("[INFO] 已加载 .env 文件: " + envFile.getAbsolutePath());
                    loaded = true;
                    break;
                }
            }

            if (!loaded) {
                System.out.println("[WARN] 未找到 .env 文件，将使用系统环境变量或 application.yml 配置");
            }
            
            // 特殊处理：如果 .env 中设置了 SERVER_PORT，同步设置到 server.port
            String serverPort = System.getProperty("SERVER_PORT");
            if (serverPort != null && System.getProperty("server.port") == null) {
                System.setProperty("server.port", serverPort);
                System.out.println("[INFO] 设置服务器端口: " + serverPort);
            }
        } catch (Exception e) {
            System.out.println("[WARN] 无法加载 .env 文件: " + e.getMessage());
        }
    }
}


package com.aicode.review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 
 * 存储 PR 的代码变更信息，包括修改的文件、新增/删除的代码行等。
 * 这是代码审查的核心输入数据。
 * 
 * @author AI Code Review Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PRDifferences {

    /**
     * PR 编号
     */
    private String prNumber;

    /**
     * PR 标题
     */
    private String title;

    /**
     * PR 描述
     */
    private String description;

    /**
     * 源分支
     */
    private String sourceBranch;

    /**
     * 目标分支
     */
    private String targetBranch;

    /**
     * 仓库名称 (格式: owner/repo)
     */
    private String repository;

    /**
     * 提交者
     */
    private String author;

    /**
     * 变更的文件列表
     */
    private List<FileDiff> files;

    /**
     * 关联的 PR 信息
     */
    private PRInfo prInfo;

    /**
     * 原始 diff 文本（兼容字段，同 rawDiff）
     */
    private String diff;

    /**
     * 变更文件数量（快捷缓存字段）
     */
    private int fileCount;

    /**
     * 变更总行数（快捷缓存字段）
     */
    private int totalLines;

    /**
     * 原始 diff 文本
     */
    private String rawDiff;

    /**
     * 文件差异详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileDiff {
        /**
         * 文件路径
         */
        private String filePath;

        /**
         * 文件状态: added, modified, removed, renamed
         */
        private String status;

        /**
         * 新增行数
         */
        private int additions;

        /**
         * 删除行数
         */
        private int deletions;

        /**
         * 变更内容（patch）
         */
        private String patch;

        /**
         * 文件完整内容（如果可获取）
         */
        private String content;

        /**
         * 文件类型（根据扩展名）
         */
        private String fileType;

        /**
         * 是否为二进制文件
         */
        private boolean binary;

        /**
         * 获取文件扩展名
         * 
         * @return 文件扩展名，如 "java", "xml"
         */
        public String getExtension() {
            if (filePath == null || !filePath.contains(".")) {
                return "";
            }
            return filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();
        }

        /**
         * 判断是否为 Java 文件
         * 
         * @return 如果是 .java 文件返回 true
         */
        public boolean isJavaFile() {
            return "java".equals(getExtension());
        }

        /**
         * 判断是否为测试文件
         * 
         * @return 如果文件路径包含 test 或 Test 返回 true
         */
        public boolean isTestFile() {
            return filePath != null && 
                   (filePath.contains("/test/") || 
                    filePath.contains("/tests/") ||
                    filePath.contains("Test.java"));
        }
    }

    /**
     * 获取 Java 文件变更列表
     * 
     * @return 所有 Java 文件的变更
     */
    public List<FileDiff> getJavaFileDiffs() {
        if (files == null) {
            return List.of();
        }
        return files.stream()
                .filter(FileDiff::isJavaFile)
                .toList();
    }

    /**
     * 获取非测试 Java 文件变更列表
     * 
     * @return 生产代码的 Java 文件变更
     */
    public List<FileDiff> getProductionJavaFileDiffs() {
        if (files == null) {
            return List.of();
        }
        return files.stream()
                .filter(FileDiff::isJavaFile)
                .filter(f -> !f.isTestFile())
                .toList();
    }

    /**
     * 获取总新增行数
     * 
     * @return 所有文件新增行数之和
     */
    public int getTotalAdditions() {
        if (files == null) {
            return 0;
        }
        return files.stream().mapToInt(FileDiff::getAdditions).sum();
    }

    /**
     * 获取总删除行数
     * 
     * @return 所有文件删除行数之和
     */
    public int getTotalDeletions() {
        if (files == null) {
            return 0;
        }
        return files.stream().mapToInt(FileDiff::getDeletions).sum();
    }

    /**
     * 获取变更文件总数
     * 
     * @return 变更的文件数量
     */
    public int getChangedFilesCount() {
        return files == null ? 0 : files.size();
    }

    /**
     * 获取审查上下文信息
     * 
     * @return 格式化的上下文字符串
     */
    public String getContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("PR: ").append(title).append("\n");
        sb.append("作者: ").append(author).append("\n");
        sb.append("分支: ").append(sourceBranch).append(" -> ").append(targetBranch).append("\n");
        sb.append("文件: ").append(getChangedFilesCount()).append(" 个\n");
        sb.append("新增: +").append(getTotalAdditions()).append(" 行\n");
        sb.append("删除: -").append(getTotalDeletions()).append(" 行\n");
        if (description != null && !description.isEmpty()) {
            sb.append("描述: ").append(description).append("\n");
        }
        return sb.toString();
    }
}

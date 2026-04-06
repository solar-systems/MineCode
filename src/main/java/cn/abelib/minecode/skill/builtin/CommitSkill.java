package cn.abelib.minecode.skill.builtin;

import cn.abelib.minecode.skill.Skill;
import cn.abelib.minecode.skill.SkillContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * Git 提交技能
 *
 * @author Abel
 */
public class CommitSkill implements Skill {

    private SkillContext context;

    @Override
    public String name() {
        return "commit";
    }

    @Override
    public String description() {
        return "Git 提交代码";
    }

    @Override
    public String usage() {
        return "/commit [message]";
    }

    @Override
    public boolean requiresArgs() {
        return false;
    }

    @Override
    public String execute(SkillContext context, String args) {
        this.context = context;

        try {
            // 1. 检查 git 状态
            String status = runCommand("git status --porcelain");
            if (status.isBlank()) {
                return "✗ 没有需要提交的更改";
            }

            // 2. 获取提交信息
            String message = args;
            if (message == null || message.isBlank()) {
                message = generateCommitMessage(status);
            }

            // 3. 添加所有更改
            runCommand("git add -A");

            // 4. 提交
            String result = runCommand("git commit -m \"" + escapeMessage(message) + "\"");

            if (result.contains("nothing to commit")) {
                return "✗ 没有需要提交的更改";
            }

            return "✓ 提交成功: " + message;

        } catch (Exception e) {
            return "✗ 提交失败: " + e.getMessage();
        }
    }

    private String generateCommitMessage(String status) {
        String[] lines = status.split("\n");
        int added = 0, modified = 0, deleted = 0;

        for (String line : lines) {
            if (line.isBlank()) continue;
            char c = line.charAt(0);
            if (c == 'A' || c == '?') added++;
            else if (c == 'M') modified++;
            else if (c == 'D') deleted++;
        }

        StringBuilder msg = new StringBuilder("chore: ");
        if (added > 0) msg.append("添加 ").append(added).append(" 文件, ");
        if (modified > 0) msg.append("修改 ").append(modified).append(" 文件, ");
        if (deleted > 0) msg.append("删除 ").append(deleted).append(" 文件");

        return msg.toString().replaceAll(", $", "");
    }

    private String escapeMessage(String message) {
        return message.replace("\"", "\\\"").replace("'", "'\\''");
    }

    private String runCommand(String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        pb.directory(context != null && context.getWorkingDirectory() != null
                ? context.getWorkingDirectory().toFile()
                : new File("."));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        process.waitFor();
        return output.toString();
    }
}

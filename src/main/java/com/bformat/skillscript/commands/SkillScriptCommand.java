package com.bformat.skillscript.commands;

import com.bformat.skillscript.SkillScript; // 메인 클래스 참조 변경
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

// 클래스 이름 변경
public class SkillScriptCommand implements CommandExecutor {

    private final SkillScript plugin; // 메인 클래스 타입 변경

    // 생성자에서 받는 타입 변경
    public SkillScriptCommand(SkillScript plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 권한 노드 변경
        if (!sender.hasPermission("skillscript.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            // TODO: config.yml 리로드 로직
            // plugin.reloadConfig();

            plugin.getScriptManager().loadScripts();
            // 메시지 변경
            sender.sendMessage(ChatColor.GREEN + "SkillScript scripts reloaded.");
            return true;
        }

        // 메시지 변경
        sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " reload");
        return true;
    }
}
package com.bformat.skillscript.commands;

import com.bformat.skillscript.script.ScriptManager;
import com.bformat.skillscript.script.ScriptRunner; // ScriptRunner 임포트
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class CastCommand implements CommandExecutor {

    private final ScriptManager scriptManager;
    private final ScriptRunner scriptRunner; // ScriptRunner 멤버 추가

    // 생성자 수정: ScriptRunner 주입 받음
    public CastCommand(ScriptManager scriptManager, ScriptRunner scriptRunner) {
        this.scriptManager = scriptManager;
        this.scriptRunner = scriptRunner;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }
        Player caster = (Player) sender;

        if (args.length == 0) {
            caster.sendMessage(ChatColor.RED + "Usage: /" + label + " <scriptName> [args...]");
            return true;
        }

        String scriptName = args[0];
        Map<String, Object> scriptData = scriptManager.getScriptData(scriptName);

        if (scriptData == null) {
            caster.sendMessage(ChatColor.RED + "Unknown script: " + scriptName);
            return true;
        }

        List<Map<String, Object>> onCastActions = scriptManager.getTriggerActions(scriptData, "OnCast");

        if (onCastActions == null || onCastActions.isEmpty()) {
            caster.sendMessage(ChatColor.YELLOW + "Script '" + scriptName + "' has no actions defined for OnCast trigger.");
            return true;
        }

        // --- 실행 로직 변경 ---
        // ScriptRunner에게 실행 위임
        caster.sendMessage(ChatColor.GREEN + "Casting skill: " + scriptName);
        scriptRunner.runScript(caster, onCastActions); // ScriptRunner의 메소드 호출

        return true;
    }

    // executeActions 메소드는 이제 ScriptRunner로 이동했으므로 제거
    // private void executeActions(...) { ... }
}
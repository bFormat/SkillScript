package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState; // ExecutionState 임포트 추가
import com.bformat.skillscript.execution.ExecutionStatus;
import com.bformat.skillscript.lang.Action;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender; // Allow sending to Console
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger; // Logger 임포트 추가

public class SendMessageAction implements Action {

    @Override
    public ExecutionStatus execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action] ";

        // --- 메시지 파싱 (필수) ---
        Optional<String> messageOpt = getStringParameter(params, "message");
        if (messageOpt.isEmpty()) {
            logger.warning(pluginPrefix + "SendMessageAction: Missing 'message' parameter.");
            return ExecutionStatus.ERROR("SendMessageAction: Missing 'message' parameter.");
        }
        String rawMessage = messageOpt.get(); // 원본 메시지 문자열

        // --- 대상 결정 ---
        CommandSender target = resolveTarget(context, params.get("target")); // 기존 헬퍼 사용

        // --- 메시지 전송 ---
        if (target != null) {
            // *** 플레이스홀더 처리 ***
            String processedMessage = processPlaceholders(rawMessage, context, logger, pluginPrefix);

            // 색상 코드 적용
            String formattedMessage = ChatColor.translateAlternateColorCodes('&', processedMessage);

            // 최종 메시지 전송
            target.sendMessage(formattedMessage);
            logger.fine(pluginPrefix + "Sent message to " + target.getName() + ": " + formattedMessage + " (Raw: " + rawMessage + ")");
        } else {
            logger.warning(pluginPrefix + "SendMessageAction: Could not determine a valid target to send the message to.");
        }

        return ExecutionStatus.COMPLETED;
    }


    /**
     * 파라미터와 컨텍스트를 기반으로 메시지를 받을 대상(Player or CommandSender)을 결정합니다.
     * @param context The execution context.
     * @param targetParam The raw 'target' parameter value.
     * @return The resolved CommandSender, or null if no valid target found.
     */
    private CommandSender resolveTarget(ExecutionContext context, Object targetParam) {
        if (targetParam instanceof CommandSender) { // Covers Player and ConsoleCommandSender
            return (CommandSender) targetParam;
        } else if (targetParam instanceof Entity && targetParam instanceof CommandSender) {
            // Handle cases where an Entity is passed, check if it's also a CommandSender (like Player)
            return (CommandSender) targetParam;
        } else if (targetParam instanceof String) {
            String targetString = (String) targetParam;
            if ("Caster".equalsIgnoreCase(targetString)) {
                return context.getCaster(); // Caster is always a Player in this context
            }
            // TODO: Add logic for target selectors (@p, @a, etc.) or variable lookups if needed
            // Example: Check for player name
            Player namedPlayer = context.getCaster().getServer().getPlayerExact(targetString);
            if (namedPlayer != null) {
                return namedPlayer;
            }
            // Could add Console check?
            // if ("Console".equalsIgnoreCase(targetString)) { return context.getCaster().getServer().getConsoleSender(); }

        }

        // Fallback 1: CurrentTarget from context, if it's a CommandSender
        Object currentTarget = context.getCurrentTarget();
        if (currentTarget instanceof CommandSender) {
            return (CommandSender) currentTarget;
        }

        // Fallback 2: Default to Caster
        return context.getCaster();
    }

    // getStringParameter helper is inherited from Action interface
}
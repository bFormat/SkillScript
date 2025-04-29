package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState;
import com.bformat.skillscript.execution.ExecutionStatus;
import com.bformat.skillscript.lang.Action;
import org.bukkit.util.Vector; // Vector 임포트

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class GetDirectionAction implements Action {
    @Override
    public ExecutionStatus execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action] ";

        Optional<String> targetIdentifierOpt = getStringParameter(params, "target"); // 예: "@Caster", "@Target"
        Optional<String> variableNameOpt = getStringParameter(params, "variable");
        boolean normalize = getBooleanParameter(params, "normalize", true); // 기본적으로 정규화

        if (targetIdentifierOpt.isEmpty() || variableNameOpt.isEmpty()) {
            logger.warning(pluginPrefix + "GetDirectionAction: Missing 'target' or 'variable' parameter.");
            return ExecutionStatus.ERROR("GetDirectionAction: Missing 'target' or 'variable' parameter.");
        }

        String targetIdentifier = targetIdentifierOpt.get();
        String variableName = variableNameOpt.get();

        // Vector 해석 시도 (주로 방향 관련 @키워드 또는 변수)
        Optional<Vector> directionOpt = context.resolveVector(targetIdentifier);

        if (directionOpt.isPresent()) {
            Vector direction = directionOpt.get().clone(); // 복제
            if (normalize) {
                try {
                    direction.normalize(); // 정규화
                } catch (IllegalArgumentException e) {
                    logger.warning(pluginPrefix + "GetDirectionAction: Cannot normalize zero vector for target '" + targetIdentifier + "'. Storing zero vector.");
                    direction = new Vector(0,0,0); // 정규화 불가 시 0벡터 저장
                }
            }
            context.setVariable(variableName, direction);
            logger.fine(pluginPrefix + "GetDirectionAction: Stored direction of '" + targetIdentifier + "' (Normalized: " + normalize + ") into variable '" + variableName + "'.");
        } else {
            logger.warning(pluginPrefix + "GetDirectionAction: Could not resolve direction for target '" + targetIdentifier + "'.");
        }

        return ExecutionStatus.COMPLETED;
    }
}
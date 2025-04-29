package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState; // ExecutionState 임포트
import com.bformat.skillscript.execution.ExecutionStatus;
import com.bformat.skillscript.lang.Action;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IfConditionAction implements Action {

    @Override
    @SuppressWarnings("unchecked")
    public ExecutionStatus execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action IfCond] ";

        Object conditionObject = params.get("condition");
        Optional<List<Map<String, Object>>> thenBlockOpt = getListOfMapsParameter(params, "Then");
        Optional<List<Map<String, Object>>> elseBlockOpt = getListOfMapsParameter(params, "Else");

        if (conditionObject == null) {
            logger.warning(pluginPrefix + "Missing 'condition' parameter.");
            return ExecutionStatus.ERROR("IfConditionAction: Missing 'condition' parameter.");
        }

        boolean conditionResult = false;
        try {
            if (conditionObject instanceof Boolean) {
                conditionResult = (Boolean) conditionObject;
            } else if (conditionObject instanceof Number) {
                conditionResult = ((Number) conditionObject).doubleValue() != 0.0;
            } else if (conditionObject instanceof String) {
                String conditionStr = (String) conditionObject;
                // <<< 수정: getBooleanParameter 호출 시 context 전달 >>>
                // 또는 여기서 직접 context.resolveNumericValue 시도
                Optional<Double> numericResult = context.resolveNumericValue(conditionStr);
                if (numericResult.isPresent()) {
                    conditionResult = numericResult.get() != 0.0;
                    logger.finer(pluginPrefix + "Condition string '" + conditionStr + "' resolved to numeric " + numericResult.get() + ", resulting in " + conditionResult);
                } else {
                    // 숫자 해석 실패 시 "true"/"false" 확인
                    if ("true".equalsIgnoreCase(conditionStr)) conditionResult = true;
                    else if ("false".equalsIgnoreCase(conditionStr)) conditionResult = false;
                    else {
                        logger.warning(pluginPrefix + "Condition string '" + conditionStr + "' could not be resolved to Boolean or Number. Evaluating as false.");
                        conditionResult = false;
                    }
                }
            } else {
                logger.warning(pluginPrefix + "Unsupported condition type: " + conditionObject.getClass().getName() + ". Evaluating as false.");
                conditionResult = false;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, pluginPrefix + "Error evaluating condition: " + conditionObject, e);
            return ExecutionStatus.ERROR("IfConditionAction: Error evaluating condition: " + conditionObject);
        }

        logger.fine(pluginPrefix + "Condition evaluated to " + conditionResult);

        List<Map<String, Object>> actionsToExecute = conditionResult
                ? thenBlockOpt.orElse(Collections.emptyList())
                : elseBlockOpt.orElse(Collections.emptyList());

        // logger.fine(pluginPrefix + "Preparing " + (conditionResult ? "'Then'" : "'Else'") + " block (" + actionsToExecute.size() + " actions).");
        state.startConditionalBlock(actionsToExecute); // Pushes the block if not empty

        return ExecutionStatus.COMPLETED;
    }
}
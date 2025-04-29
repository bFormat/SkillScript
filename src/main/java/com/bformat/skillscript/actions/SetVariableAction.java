package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState;
import com.bformat.skillscript.execution.ExecutionStatus;
import com.bformat.skillscript.lang.Action;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class SetVariableAction implements Action {

    @Override
    public ExecutionStatus execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action] ";

        // --- 변수 이름 파싱 (필수) ---
        // Use Action helper, variable name is required
        Optional<String> variableNameOpt = getStringParameter(params, "name");

        if (variableNameOpt.isEmpty() || variableNameOpt.get().isBlank()) {
            logger.warning(pluginPrefix + "SetVariableAction: Missing or empty 'name' parameter.");
            return ExecutionStatus.ERROR("SetVariableAction: Missing or empty 'name' parameter."); // State modification not needed
        }
        String variableName = variableNameOpt.get();

        // --- 변수 값 가져오기 ---
        // The value can be complex, so get it directly. Could be null.
        Object variableValue = params.get("value");

        // TODO: Implement value processing/evaluation if needed
        // e.g., evaluate strings like "Caster.Location", "Target.Health + 10"
        Object processedValue = variableValue; // Currently, use the value as-is

        // --- 변수 설정 ---
        // Allow setting null values explicitly if 'value' key exists but is null
        if (params.containsKey("value")) {
            context.setVariable(variableName, processedValue); // setVariable handles lowercase internally
            logger.fine(pluginPrefix + "SetVariableAction: Set variable '" + variableName + "' to: " + processedValue);
        } else {
            // If 'value' key is completely missing
            logger.warning(pluginPrefix + "SetVariableAction: Missing 'value' parameter for variable '" + variableName + "'.");
        }

        // This action completes immediately. No state modification needed.
        return ExecutionStatus.COMPLETED;
    }

    // getStringParameter helper is inherited from Action interface
}
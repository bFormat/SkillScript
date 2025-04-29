package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState;
import com.bformat.skillscript.execution.ExecutionStatus;
import com.bformat.skillscript.lang.Action;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class ForLoopAction implements Action {

    @Override
    public ExecutionStatus execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action] ";

        // --- 파라미터 파싱 ---
        Optional<String> variableNameOpt = getStringParameter(params, "variable");
        Optional<Object> fromValueOpt = Optional.ofNullable(params.get("from"));
        Optional<Object> toValueOpt = Optional.ofNullable(params.get("to"));
        Optional<Object> stepValueOpt = Optional.ofNullable(params.get("step"));
        Optional<Object> overValueOpt = Optional.ofNullable(params.get("over")); // Can be List or var name
        Optional<List<Map<String, Object>>> loopBodyOpt = getListOfMapsParameter(params, "Do");

        if (loopBodyOpt.isEmpty()) {
            logger.warning(pluginPrefix + "ForLoopAction: Missing or invalid 'Do' block (must be a list of actions).");
            return ExecutionStatus.ERROR("ForLoopAction: Missing or invalid 'Do' block (must be a list of actions)."); // Cannot loop without a body
        }
        List<Map<String, Object>> loopBody = loopBodyOpt.get();

        String variableName = variableNameOpt.orElse(null); // Can be null if iterating without assignment

        // --- 루프 타입 결정 및 상태 설정 ---

        // 1. List Iterator Loop (`over` 파라미터 사용)
        if (overValueOpt.isPresent()) {
            Object overValue = overValueOpt.get();
            List<?> listToIterate = null;

            if (overValue instanceof List) {
                listToIterate = (List<?>) overValue;
            } else if (overValue instanceof String) {
                // Try resolving as a variable containing a list
                Object listVar = context.getVariable((String) overValue);
                if (listVar instanceof List) {
                    listToIterate = (List<?>) listVar;
                } else {
                    logger.warning(pluginPrefix + "ForLoopAction: Variable '" + overValue + "' specified in 'over' is not a List.");
                    return ExecutionStatus.ERROR("ForLoopAction: Variable '" + overValue + "' specified in 'over' is not a List.");
                }
            } else {
                logger.warning(pluginPrefix + "ForLoopAction: Invalid 'over' parameter type. Expected List or variable name (String).");
                return ExecutionStatus.ERROR("ForLoopAction: Invalid 'over' parameter type. Expected List or variable name (String).");
            }

            if (variableName == null) {
                logger.warning(pluginPrefix + "ForLoopAction: 'variable' parameter is required when using 'over'.");
                return ExecutionStatus.ERROR("ForLoopAction: 'variable' parameter is required when using 'over'.");
            }

            // ExecutionState에 루프 시작 요청
            state.startListIteratorLoop(variableName, listToIterate, loopBody, context);
            logger.fine(pluginPrefix + "ForLoopAction: Initializing list iterator loop for variable '" + variableName + "'.");

            // 2. Numeric Loop (`from`, `to` 파라미터 사용)
        } else if (fromValueOpt.isPresent() || toValueOpt.isPresent()) { // At least one numeric range param
            if (variableName == null) {
                logger.warning(pluginPrefix + "ForLoopAction: 'variable' parameter is required for numeric loops (using 'from'/'to').");
                return ExecutionStatus.ERROR("ForLoopAction: 'variable' parameter is required for numeric loops (using 'from'/'to').");
            }
            if (toValueOpt.isEmpty()) {
                logger.warning(pluginPrefix + "ForLoopAction: 'to' parameter is required for numeric loops.");
                return ExecutionStatus.ERROR("ForLoopAction: 'to' parameter is required for numeric loops.");
            }

            // Resolve numeric values (handle potential expressions)
            double fromVal = resolveNumericParam(fromValueOpt.orElse(0.0), context, logger, pluginPrefix, "from").orElse(0.0);
            Optional<Double> toValOpt = resolveNumericParam(toValueOpt.get(), context, logger, pluginPrefix, "to");
            double stepVal = resolveNumericParam(stepValueOpt.orElse(1.0), context, logger, pluginPrefix, "step").orElse(1.0);

            if (toValOpt.isEmpty()) {
                logger.warning(pluginPrefix + "ForLoopAction: Could not resolve 'to' parameter to a numeric value.");
                return ExecutionStatus.ERROR("ForLoopAction: Could not resolve 'to' parameter to a numeric value."); // Cannot proceed without 'to' value
            }
            if (stepVal == 0) {
                logger.warning(pluginPrefix + "ForLoopAction: 'step' cannot be zero.");
                return ExecutionStatus.ERROR("ForLoopAction: 'step' cannot be zero.");
            }


            // ExecutionState에 루프 시작 요청
            state.startNumericLoop(variableName, fromVal, toValOpt.get(), stepVal, loopBody, context);
            logger.fine(pluginPrefix + "ForLoopAction: Initializing numeric loop for variable '" + variableName + "'.");

            // 3. 잘못된 파라미터 조합
        } else {
            logger.warning(pluginPrefix + "ForLoopAction: Invalid parameters. Use 'over' for list iteration or 'from'/'to' (with optional 'step') for numeric loops.");
            return ExecutionStatus.ERROR("ForLoopAction: Invalid parameters. Use 'over' for list iteration or 'from'/'to' (with optional 'step') for numeric loops.");
        }

        // ForLoopAction 자체는 상태 설정 후 즉시 완료. 실제 반복은 ScriptTask에서 처리.
        return ExecutionStatus.COMPLETED;
    }


    // Helper to resolve numeric parameters (could be Double, Integer, String expression, etc.)
    private Optional<Double> resolveNumericParam(Object paramValue, ExecutionContext context, Logger logger, String prefix, String paramName) {
        if (paramValue instanceof Number) {
            return Optional.of(((Number) paramValue).doubleValue());
        } else if (paramValue instanceof String) {
            // Try parsing as direct double first
            try {
                return Optional.of(Double.parseDouble((String) paramValue));
            } catch (NumberFormatException e) {
                // If not a direct double, try resolving as expression/variable
                return context.resolveNumericValue((String) paramValue);
                // resolveNumericValue already logs warnings if it fails
            }
        } else {
            logger.warning(prefix + "ForLoopAction: Invalid type for numeric parameter '" + paramName + "'. Expected Number or String, got " + (paramValue != null ? paramValue.getClass().getName() : "null") );
            return Optional.empty();
        }
    }
}
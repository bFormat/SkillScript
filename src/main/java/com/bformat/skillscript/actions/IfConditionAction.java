package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState; // ExecutionState 임포트 추가
import com.bformat.skillscript.lang.Action;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IfConditionAction implements Action {

    @Override
    @SuppressWarnings("unchecked") // Suppress warnings for casting List<Map>
    public void execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action] ";

        // --- 파라미터 가져오기 ---
        Object conditionObject = params.get("condition");
        Object thenObject = params.get("Then"); // Case-sensitive key "Then"
        Object elseObject = params.get("Else"); // Case-sensitive key "Else"

        if (conditionObject == null) {
            logger.warning(pluginPrefix + "IfConditionAction: Missing 'condition' parameter.");
            return; // Cannot proceed without condition
        }

        // --- 조건 평가 로직 ---
        boolean conditionResult = false;
        try {
            // TODO: Implement more complex condition evaluation (e.g., expression parsing)
            // Simple evaluation for now:
            if (conditionObject instanceof Boolean) {
                conditionResult = (Boolean) conditionObject;
            } else if (conditionObject instanceof String) {
                // Evaluate "true" (case-insensitive) as true, everything else as false
                conditionResult = Boolean.parseBoolean((String) conditionObject);
                // Consider adding checks for "1", "yes" etc. if needed
            } else if (conditionObject instanceof Number) {
                // Consider non-zero numbers as true (common scripting behavior)
                conditionResult = ((Number) conditionObject).doubleValue() != 0.0;
            } else {
                // Treat other types as false, or log a warning
                logger.warning(pluginPrefix + "IfConditionAction: Unsupported condition type: " + conditionObject.getClass().getName() + ". Evaluating as false.");
                conditionResult = false;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, pluginPrefix + "IfConditionAction: Error evaluating condition: " + conditionObject, e);
            return; // Stop execution on evaluation error
        }

        logger.fine(pluginPrefix + "IfConditionAction: Condition evaluated to " + conditionResult);

        // --- 조건 결과에 따른 블록 선택 ---
        List<Map<String, Object>> actionsToExecute = Collections.emptyList(); // Default to empty list

        if (conditionResult) {
            // Process 'Then' block
            if (thenObject instanceof List) {
                try {
                    // Check if list elements are maps (basic check)
                    List<?> potentialList = (List<?>) thenObject;
                    if (!potentialList.isEmpty() && !(potentialList.get(0) instanceof Map)) {
                        logger.warning(pluginPrefix + "IfConditionAction: 'Then' block contains non-Map elements. Skipping.");
                    } else {
                        actionsToExecute = (List<Map<String, Object>>) thenObject;
                    }
                } catch (ClassCastException e) {
                    logger.warning(pluginPrefix + "IfConditionAction: Invalid format for 'Then' block. Expected List<Map<String, Object>>.");
                }
            } else if (thenObject != null) {
                logger.warning(pluginPrefix + "IfConditionAction: Invalid format for 'Then' block. Expected a List.");
            }
            // If thenObject is null, actionsToExecute remains empty (no 'Then' block)
        } else {
            // Process 'Else' block
            if (elseObject instanceof List) {
                try {
                    // Check if list elements are maps (basic check)
                    List<?> potentialList = (List<?>) elseObject;
                    if (!potentialList.isEmpty() && !(potentialList.get(0) instanceof Map)) {
                        logger.warning(pluginPrefix + "IfConditionAction: 'Else' block contains non-Map elements. Skipping.");
                    } else {
                        actionsToExecute = (List<Map<String, Object>>) elseObject;
                    }
                } catch (ClassCastException e) {
                    logger.warning(pluginPrefix + "IfConditionAction: Invalid format for 'Else' block. Expected List<Map<String, Object>>.");
                }
            } else if (elseObject != null) {
                logger.warning(pluginPrefix + "IfConditionAction: Invalid format for 'Else' block. Expected a List.");
            }
            // If elseObject is null, actionsToExecute remains empty (no 'Else' block)
        }

        // --- 중첩 액션 실행 (현재 구조의 한계점) ---
        if (!actionsToExecute.isEmpty()) {
            logger.fine(pluginPrefix + "IfConditionAction: Preparing to execute nested actions (" + actionsToExecute.size() + ")...");
            // !!! WARNING: Directly executing nested actions here is problematic with the current ScriptTask design !!!
            // This can lead to recursion if the nested actions also have control flow or delays.
            // A proper solution requires modifying ScriptTask to handle nested states, perhaps using a stack.
            // For now, we log a warning and DO NOT execute directly.
            // The ExecutionState needs to be manipulated to insert these actions into the flow.
            logger.warning(pluginPrefix + "IfConditionAction: Executing nested actions within the same task tick is NOT YET SUPPORTED correctly. Skipping nested execution.");

            // === Placeholder for future implementation using ExecutionState modification ===
            // Option 1: Replace current list (might lose subsequent actions in parent block)
            // state.setCurrentActionList(actionsToExecute);
            // state.setCurrentActionIndex(0);

            // Option 2: Insert actions (more complex state management needed)
            // state.insertActions(actionsToExecute); // Needs implementation in ExecutionState

            // Option 3: Push a new state onto a stack (Requires stack in ScriptTask/ExecutionState)
            // state.pushNestedState(actionsToExecute); // Needs implementation

        } else {
            logger.fine(pluginPrefix + "IfConditionAction: No actions to execute for the " + (conditionResult ? "'Then'" : "'Else'") + " branch.");
        }

        // This action itself completes immediately, but the nested execution part needs proper handling via ExecutionState/ScriptTask.
    }
}
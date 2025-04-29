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
        final String pluginPrefix = "[SkillScript Action] ";

        // --- 파라미터 가져오기 ---
        Object conditionObject = params.get("condition");
        Optional<List<Map<String, Object>>> thenBlockOpt = getListOfMapsParameter(params, "Then");
        Optional<List<Map<String, Object>>> elseBlockOpt = getListOfMapsParameter(params, "Else");

        if (conditionObject == null) {
            logger.warning(pluginPrefix + "IfConditionAction: Missing 'condition' parameter.");
            return ExecutionStatus.ERROR("IfConditionAction: Missing 'condition' parameter.");
        }

        // --- 조건 평가 로직 (표현식 지원 강화) ---
        boolean conditionResult = false;
        try {
            // --- 표현식 파싱 시도 ---
            // 1. Boolean 직접 타입
            if (conditionObject instanceof Boolean) {
                conditionResult = (Boolean) conditionObject;
            }
            // 2. 숫자 타입 (0 아니면 true)
            else if (conditionObject instanceof Number) {
                conditionResult = ((Number) conditionObject).doubleValue() != 0.0;
            }
            // 3. 문자열 - Boolean 파싱 또는 숫자/셀렉터 해석 시도
            else if (conditionObject instanceof String) {
                String conditionStr = (String) conditionObject;
                // 먼저 "true"/"false" (대소문자 무시) 확인
                if ("true".equalsIgnoreCase(conditionStr)) {
                    conditionResult = true;
                } else if ("false".equalsIgnoreCase(conditionStr)) {
                    conditionResult = false;
                } else {
                    // Boolean 아니면 숫자로 해석 시도 (0 아니면 true)
                    Optional<Double> numericResult = context.resolveNumericValue(conditionStr);
                    if (numericResult.isPresent()) {
                        conditionResult = numericResult.get() != 0.0;
                        logger.finer(pluginPrefix + "IfConditionAction: Condition string '" + conditionStr + "' resolved to numeric " + numericResult.get() + ", resulting in " + conditionResult);
                    } else {
                        // Boolean/숫자 아니면 false 처리 및 경고
                        logger.warning(pluginPrefix + "IfConditionAction: Condition string '" + conditionStr + "' could not be resolved to Boolean or Number. Evaluating as false.");
                        conditionResult = false;
                    }
                }
            }
            // 4. 기타 타입은 false 처리
            else {
                logger.warning(pluginPrefix + "IfConditionAction: Unsupported condition type: " + conditionObject.getClass().getName() + ". Evaluating as false.");
                conditionResult = false;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, pluginPrefix + "IfConditionAction: Error evaluating condition: " + conditionObject, e);
            return ExecutionStatus.ERROR("IfConditionAction: Error evaluating condition: " + conditionObject); // 평가 오류 시 중단
        }

        logger.fine(pluginPrefix + "IfConditionAction: Condition evaluated to " + conditionResult);

        // --- 조건 결과에 따른 블록 선택 및 상태 변경 ---
        List<Map<String, Object>> actionsToExecute = Collections.emptyList();

        if (conditionResult) {
            actionsToExecute = thenBlockOpt.orElse(Collections.emptyList());
            logger.fine(pluginPrefix + "IfConditionAction: Condition is true, preparing 'Then' block (" + actionsToExecute.size() + " actions).");
        } else {
            actionsToExecute = elseBlockOpt.orElse(Collections.emptyList());
            logger.fine(pluginPrefix + "IfConditionAction: Condition is false, preparing 'Else' block (" + actionsToExecute.size() + " actions).");
        }

        // *** ExecutionState를 통해 다음 실행 블록 설정 ***
        state.startConditionalBlock(actionsToExecute);

        // IfConditionAction 자체는 상태 설정 후 즉시 완료됨
        return ExecutionStatus.COMPLETED;
    }
}
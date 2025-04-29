package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState;
import com.bformat.skillscript.execution.ExecutionStatus;
import com.bformat.skillscript.lang.Action;
import org.bukkit.Location; // 필요 타입 임포트
import org.bukkit.util.Vector;
import org.bukkit.entity.Entity;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher; // Regex 임포트
import java.util.regex.Pattern;

public class SetVariableAction implements Action {

    // 다른 액션에서 변수 참조 문자열 해석 시 사용될 패턴 ({var:name})
    // SetVariable 자체에서는 사용하지 않지만, Action 인터페이스에서 가져옴 (참고용)
    // private static final Pattern VAR_PATTERN = Pattern.compile("\\{var:([^}]+)\\}");

    @Override
    public ExecutionStatus execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action SetVar] ";

        // --- 변수 이름 파싱 (필수) ---
        Optional<String> variableNameOpt = getStringParameter(params, "name");
        if (variableNameOpt.isEmpty() || variableNameOpt.get().isBlank()) {
            logger.warning(pluginPrefix + "Missing or empty 'name' parameter.");
            return ExecutionStatus.ERROR("SetVariableAction: Missing or empty 'name' parameter.");
        }
        String variableName = variableNameOpt.get();

        // --- 변수 값 가져오기 (YAML에 정의된 원본 값) ---
        if (!params.containsKey("value")) {
            logger.warning(pluginPrefix + "Missing 'value' parameter for variable '" + variableName + "'. Setting to null.");
            context.setVariable(variableName, null); // value 키가 없으면 null로 설정
            return ExecutionStatus.COMPLETED;
        }
        Object rawValue = params.get("value");

        // --- 값 해석 로직 ---
        Object processedValue;
        if (rawValue instanceof String) {
            String valueStr = (String) rawValue;
            logger.fine(pluginPrefix + "Input value for '" + variableName + "' is String: \"" + valueStr + "\". Attempting to resolve...");

            // 1. @키워드 또는 변수 이름으로 해석 시도 (가장 일반적)
            // 타입을 알 수 없으므로 여러 타입으로 시도
            Optional<Location> locOpt = context.resolveLocation(valueStr);
            if (locOpt.isPresent()) {
                processedValue = locOpt.get(); // Location 객체 저장
                logger.fine(pluginPrefix + "Resolved '" + valueStr + "' as Location variable/keyword.");
            } else {
                Optional<Vector> vecOpt = context.resolveVector(valueStr);
                if (vecOpt.isPresent()) {
                    processedValue = vecOpt.get(); // Vector 객체 저장
                    logger.fine(pluginPrefix + "Resolved '" + valueStr + "' as Vector variable/keyword.");
                } else {
                    Optional<Entity> entOpt = context.resolveEntity(valueStr);
                    if (entOpt.isPresent()) {
                        processedValue = entOpt.get(); // Entity 객체 저장
                        logger.fine(pluginPrefix + "Resolved '" + valueStr + "' as Entity variable/keyword.");
                    } else {
                        Optional<Double> numOpt = context.resolveNumericValue(valueStr);
                        if (numOpt.isPresent()) {
                            processedValue = numOpt.get(); // Double 객체 저장
                            logger.fine(pluginPrefix + "Resolved '" + valueStr + "' as Numeric variable/keyword.");
                        } else {
                            // 어떤 해석에도 해당하지 않으면 문자열 리터럴로 간주
                            processedValue = valueStr;
                            logger.fine(pluginPrefix + "Could not resolve '" + valueStr + "' as variable/keyword. Storing as String literal.");
                        }
                    }
                }
            }
            // 참고: 여기서 {var:...} 패턴을 굳이 해석할 필요는 없음.
            // 다른 변수를 참조하려면 그냥 변수 이름("otherVar")을 쓰면 resolveXxx가 처리함.
        } else {
            // String이 아닌 다른 타입(Number, Boolean, List, Map 등)은 그대로 사용
            processedValue = rawValue;
            logger.fine(pluginPrefix + "Input value for '" + variableName + "' is not String ("+ (rawValue != null ? rawValue.getClass().getSimpleName() : "null") +"). Storing as is.");
        }

        // --- 변수 설정 ---
        context.setVariable(variableName, processedValue);
        logger.info(pluginPrefix + "Set variable '" + variableName + "' to: " + processedValue + (rawValue instanceof String && rawValue != processedValue ? " (Resolved from: \"" + rawValue + "\")" : ""));

        return ExecutionStatus.COMPLETED;
    }
}
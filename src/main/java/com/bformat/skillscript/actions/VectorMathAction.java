package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState;
import com.bformat.skillscript.execution.ExecutionStatus;
import com.bformat.skillscript.lang.Action;
import org.bukkit.util.Vector;

import java.util.List; // List 임포트 추가
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class VectorMathAction implements Action {

    // ... (execute 메소드는 이전 버전과 동일하게 유지) ...
    @Override
    public ExecutionStatus execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action VectorMath] ";

        Optional<String> variableNameOpt = getStringParameter(params, "variable");
        Optional<Object> vector1ParamOpt = Optional.ofNullable(params.get("vector1"));
        Optional<String> operationOpt = getStringParameter(params, "operation");

        if (variableNameOpt.isEmpty() || vector1ParamOpt.isEmpty() || operationOpt.isEmpty()) {
            logger.warning(pluginPrefix + "Missing required parameters: 'variable', 'vector1', 'operation'.");
            return ExecutionStatus.ERROR("VectorMathAction: Missing required parameters: variable, vector1, operation.");
        }

        String variableName = variableNameOpt.get();
        String operation = operationOpt.get().toUpperCase();
        Object vector1Raw = vector1ParamOpt.get();

        Optional<Vector> vector1Opt = resolveVector(vector1Raw, context, logger, pluginPrefix);
        if (vector1Opt.isEmpty()) {
            return ExecutionStatus.ERROR("VectorMathAction: Could not resolve 'vector1'.");
        }
        Vector vector1 = vector1Opt.get().clone();

        Vector resultVector = null;

        try {
            switch (operation) {
                case "MULTIPLY_SCALAR":
                case "SCALE":
                    Optional<Object> scalarOperandOpt = Optional.ofNullable(params.get("operand"));
                    if (scalarOperandOpt.isEmpty()) {
                        logger.warning(pluginPrefix + "Missing 'operand' parameter for operation: " + operation);
                        return ExecutionStatus.ERROR("VectorMathAction: Missing 'operand' for " + operation);
                    }
                    Optional<Double> scalarOpt = resolveScalar(scalarOperandOpt.get(), context, logger, pluginPrefix);
                    if (scalarOpt.isEmpty()) {
                        return ExecutionStatus.ERROR("VectorMathAction: Could not resolve scalar operand for " + operation);
                    }
                    resultVector = vector1.multiply(scalarOpt.get());
                    logger.fine(pluginPrefix + "Calculated " + vector1Opt.get() + " * " + scalarOpt.get() + " = " + resultVector);
                    break;

                case "ADD":
                case "SUBTRACT":
                    Optional<Object> vectorOperandOpt = Optional.ofNullable(params.get("operand"));
                    if (vectorOperandOpt.isEmpty()) {
                        logger.warning(pluginPrefix + "Missing 'operand' parameter for operation: " + operation);
                        return ExecutionStatus.ERROR("VectorMathAction: Missing 'operand' for " + operation);
                    }
                    Optional<Vector> vector2Opt = resolveVector(vectorOperandOpt.get(), context, logger, pluginPrefix);
                    if (vector2Opt.isEmpty()) {
                        return ExecutionStatus.ERROR("VectorMathAction: Could not resolve vector operand for " + operation);
                    }
                    if (operation.equals("ADD")) {
                        resultVector = vector1.add(vector2Opt.get());
                        logger.fine(pluginPrefix + "Calculated " + vector1Opt.get() + " + " + vector2Opt.get() + " = " + resultVector);
                    } else { // SUBTRACT
                        resultVector = vector1.subtract(vector2Opt.get());
                        logger.fine(pluginPrefix + "Calculated " + vector1Opt.get() + " - " + vector2Opt.get() + " = " + resultVector);
                    }
                    break;

                case "NORMALIZE":
                    try {
                        resultVector = vector1.normalize();
                        logger.fine(pluginPrefix + "Normalized " + vector1Opt.get() + " to " + resultVector);
                    } catch (IllegalArgumentException e) {
                        logger.warning(pluginPrefix + "Cannot normalize a zero-length vector. Returning zero vector.");
                        resultVector = new Vector(0, 0, 0);
                    }
                    break;

                default:
                    logger.warning(pluginPrefix + "Unsupported operation: " + operation);
                    return ExecutionStatus.ERROR("VectorMathAction: Unsupported operation '" + operation + "'.");
            }

            if (resultVector != null) {
                context.setVariable(variableName, resultVector);
                logger.info(pluginPrefix + "Stored result in variable '" + variableName + "'. Value: " + resultVector);
            } else {
                logger.warning(pluginPrefix + "Result vector was null after operation '" + operation + "'. Variable '" + variableName + "' not set.");
            }

        } catch (Exception e) {
            logger.severe(pluginPrefix + "Error during vector math operation '" + operation + "': " + e.toString());
            e.printStackTrace();
            return ExecutionStatus.ERROR("VectorMathAction: Error during operation '" + operation + "': " + e.getMessage());
        }

        return ExecutionStatus.COMPLETED;
    }


    // --- Helper Methods ---

    /**
     * Helper to resolve Vector from Object (direct value, variable name, special keyword, or List [x, y, z]).
     * Relies on ExecutionContext.resolveVector for string resolution.
     */
    private Optional<Vector> resolveVector(Object rawValue, ExecutionContext context, Logger logger, String prefix) {
        if (rawValue instanceof Vector) {
            return Optional.of((Vector) rawValue); // 직접 Vector 객체
        } else if (rawValue instanceof String) {
            // 문자열이면 context.resolveVector 호출
            Optional<Vector> resolved = context.resolveVector((String) rawValue);
            if (resolved.isEmpty()) {
                logger.warning(prefix + "Could not resolve string '" + rawValue + "' to Vector using context.resolveVector.");
            }
            return resolved;
        } else if (rawValue instanceof List) { // <<< 리스트 파싱 로직 추가 >>>
            List<?> list = (List<?>) rawValue;
            if (list.size() == 3) {
                try {
                    // 리스트의 각 요소를 숫자로 해석 (resolveScalar 헬퍼 사용)
                    double x = resolveScalar(list.get(0), context, logger, prefix).orElseThrow(() -> new NumberFormatException("Could not parse X value from list"));
                    double y = resolveScalar(list.get(1), context, logger, prefix).orElseThrow(() -> new NumberFormatException("Could not parse Y value from list"));
                    double z = resolveScalar(list.get(2), context, logger, prefix).orElseThrow(() -> new NumberFormatException("Could not parse Z value from list"));
                    return Optional.of(new Vector(x, y, z));
                } catch (IndexOutOfBoundsException | ClassCastException | NumberFormatException e) {
                    logger.warning(prefix + "Error parsing Vector from List " + rawValue + ": " + e.getMessage());
                    return Optional.empty();
                }
            } else {
                logger.warning(prefix + "Could not parse Vector from List: List size is not 3. List: " + rawValue);
                return Optional.empty();
            }
        }
        // TODO: Add parsing for Map {x,y,z}?
        logger.warning(prefix + "Could not resolve value to Vector. Unsupported type: " + (rawValue != null ? rawValue.getClass().getName() : "null") + ", value: " + rawValue);
        return Optional.empty();
    }

    /**
     * Helper to resolve Scalar (Double) from Object (direct value, variable name, special keyword/numeric property, or numeric string).
     * Relies on ExecutionContext.resolveNumericValue for string resolution.
     */
    private Optional<Double> resolveScalar(Object rawValue, ExecutionContext context, Logger logger, String prefix) {
        if (rawValue instanceof Number) {
            return Optional.of(((Number) rawValue).doubleValue());
        } else if (rawValue instanceof String) {
            Optional<Double> resolved = context.resolveNumericValue((String) rawValue);
            if(resolved.isEmpty()) {
                try {
                    return Optional.of(Double.parseDouble((String) rawValue));
                } catch (NumberFormatException e) {
                    logger.warning(prefix + "Could not resolve string '" + rawValue + "' to scalar using context.resolveNumericValue or direct parsing.");
                    return Optional.empty();
                }
            }
            return resolved;
        }
        logger.warning(prefix + "Could not resolve value to scalar. Unsupported type: " + (rawValue != null ? rawValue.getClass().getName() : "null") + ", value: " + rawValue);
        return Optional.empty();
    }
}
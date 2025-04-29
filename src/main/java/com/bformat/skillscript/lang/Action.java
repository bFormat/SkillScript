package com.bformat.skillscript.lang;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState;
import com.bformat.skillscript.execution.ExecutionStatus; // ExecutionStatus 임포트
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player; // Added for specific checks
import org.bukkit.util.Vector;

import java.util.ArrayList; // ArrayList 임포트 추가
import java.util.Collections; // Collections 임포트 추가
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID; // For UUID parsing
import java.util.function.Function; // Function 임포트 추가
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;    // Matcher 임포트 추가
import java.util.regex.Pattern;   // Pattern 임포트 추가

/**
 * Functional interface representing a single executable action within a SkillScript.
 * Actions are registered in the ActionRegistry and executed by a ScriptTask.
 */
@FunctionalInterface
public interface Action {

    /**
     * Executes the logic of this action.
     * Implementations can read from the ExecutionContext and modify the ExecutionState.
     * The return value indicates the outcome (completed, delayed, error).
     *
     * @param context The current script execution context.
     * @param state   The current script execution state.
     * @param params  Parameters defined for this action instance.
     * @return An ExecutionStatus indicating the result.
     */
    ExecutionStatus execute(ExecutionContext context, ExecutionState state, Map<String, Object> params);

    // --- Parameter Parsing Helper Methods ---

    default <T> Optional<T> getParameter(Map<String, Object> params, String key, Class<T> type) {
        Object value = params.get(key);
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    default String getStringParameter(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    default Optional<String> getStringParameter(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof String && !((String) value).isBlank()) {
            return Optional.of((String) value);
        }
        return Optional.empty();
    }

    /**
     * Gets an Integer parameter, parsing from Number or String representations (including variables/keywords).
     * @param params The parameter map.
     * @param key The parameter key.
     * @param defaultValue The default value if parsing fails or key is missing.
     * @param context The ExecutionContext for resolving variables/keywords in strings. <<< ADDED
     * @return The parameter value as an int, or the default value.
     */
    default int getIntParameter(Map<String, Object> params, String key, int defaultValue, ExecutionContext context) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            String valueStr = (String) value;
            // 1. 변수/키워드 해석 시도 (가장 중요!)
            Optional<Double> resolved = context.resolveNumericValue(valueStr);
            if (resolved.isPresent()) {
                // 로그 추가: 변수 해석 성공 확인
                System.out.println("[DEBUG getIntParameter] Resolved variable '" + valueStr + "' to: " + resolved.get());
                return resolved.get().intValue();
            }
            // 2. 직접 파싱 시도
            try {
                int parsed = Integer.parseInt(valueStr);
                // 로그 추가: 직접 파싱 성공 확인
                System.out.println("[DEBUG getIntParameter] Parsed string '" + valueStr + "' to int: " + parsed);
                return parsed;
            } catch (NumberFormatException e) {
                System.err.println("[SkillScript Action Helper] Could not parse int parameter '" + key + "' with value: " + valueStr + " - Neither a variable/keyword nor a direct integer.");
            }
        }
        // 로그 추가: 기본값 사용 확인
        System.out.println("[DEBUG getIntParameter] Using default value " + defaultValue + " for key '" + key + "'");
        return defaultValue;
    }

    /**
     * Gets a Double parameter, parsing from Number or String representations (including variables/keywords).
     * @param params The parameter map.
     * @param key The parameter key.
     * @param defaultValue The default value if parsing fails or key is missing.
     * @param context The ExecutionContext for resolving variables/keywords in strings. <<< ADDED
     * @return The parameter value as a double, or the default value.
     */
    default double getDoubleParameter(Map<String, Object> params, String key, double defaultValue, ExecutionContext context) { // <<< context 추가
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            String valueStr = (String) value;
            try {
                // 1. 변수/키워드 해석 시도
                Optional<Double> resolved = context.resolveNumericValue(valueStr);
                if (resolved.isPresent()) {
                    return resolved.get();
                }
                // 2. 직접 파싱 시도
                return Double.parseDouble(valueStr);
            } catch (NumberFormatException | NullPointerException e) {
                System.err.println("[SkillScript Action Helper] Could not parse double parameter '" + key + "' with value: " + valueStr + " - " + e.getMessage());
                /* Fall through */
            }
        }
        return defaultValue;
    }

    /**
     * Gets a Float parameter, parsing from Number or String representations (including variables/keywords).
     * @param params The parameter map.
     * @param key The parameter key.
     * @param defaultValue The default value if parsing fails or key is missing.
     * @param context The ExecutionContext for resolving variables/keywords in strings. <<< ADDED
     * @return The parameter value as a float, or the default value.
     */
    default float getFloatParameter(Map<String, Object> params, String key, float defaultValue, ExecutionContext context) { // <<< context 추가
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        } else if (value instanceof String) {
            String valueStr = (String) value;
            try {
                // 1. 변수/키워드 해석 시도
                Optional<Double> resolved = context.resolveNumericValue(valueStr);
                if (resolved.isPresent()) {
                    return resolved.get().floatValue(); // Double을 float로 변환
                }
                // 2. 직접 파싱 시도
                return Float.parseFloat(valueStr);
            } catch (NumberFormatException | NullPointerException e) {
                System.err.println("[SkillScript Action Helper] Could not parse float parameter '" + key + "' with value: " + valueStr + " - " + e.getMessage());
                /* Fall through */
            }
        }
        return defaultValue;
    }

    /**
     * Gets a Boolean parameter, accepting Boolean values or specific String representations ("true", "false", or resolvable numeric variables/keywords).
     * @param params The parameter map.
     * @param key The parameter key.
     * @param defaultValue The default value if parsing fails or key is missing.
     * @param context The ExecutionContext for resolving variables/keywords in strings. <<< ADDED
     * @return The parameter value as a boolean, or the default value.
     */
    default boolean getBooleanParameter(Map<String, Object> params, String key, boolean defaultValue, ExecutionContext context) { // <<< context 추가
        Object value = params.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value; // 직접 Boolean 값
        } else if (value instanceof String) {
            String stringValue = (String) value;
            // 1. 변수/키워드 해석 시도 (0 아니면 true)
            Optional<Double> resolved = context.resolveNumericValue(stringValue);
            if(resolved.isPresent()) {
                return resolved.get() != 0.0;
            }
            // 2. "true"/"false" 문자열 직접 비교 (대소문자 무시)
            if ("true".equalsIgnoreCase(stringValue)) return true;
            if ("false".equalsIgnoreCase(stringValue)) return false;
            // 그 외 문자열은 기본값으로 처리 (또는 에러 로깅)
            System.err.println("[SkillScript Action Helper] Could not parse boolean parameter '" + key + "' with value: " + stringValue);
        }
        // Boolean/String 아니거나 파싱 실패 시 기본값
        return defaultValue;
    }

    // --- Location/Vector/Entity Helpers (이전 버전 - context 해석 사용) ---
    default Optional<Location> getLocationParameter(Map<String, Object> params, String key, ExecutionContext context) {
        Object value = params.get(key);
        if (value instanceof Location) {
            return Optional.of(((Location) value).clone());
        } else if (value instanceof Entity) {
            return Optional.of(((Entity)value).getLocation().clone());
        } else if (value instanceof String) {
            return context.resolveLocation((String) value); // 문자열이면 context에 위임
        }
        // System.err.println("[SkillScript Action Helper] Could not resolve Location parameter '" + key + "'. Unsupported type: " + (value != null ? value.getClass().getName() : "null") + ", value: " + value);
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    default Optional<Vector> getVectorParameter(Map<String, Object> params, String key, ExecutionContext context) {
        Object value = params.get(key);
        if (value instanceof Vector) {
            return Optional.of(((Vector) value).clone());
        } else if (value instanceof String) {
            return context.resolveVector((String) value); // 문자열이면 context에 위임
        } else if (value instanceof Map) {
            Map<String, Object> map = castToMapSo(value);
            if (map != null) {
                try {
                    double x = resolveDoubleFromObject(map.get("x"), context, 0.0);
                    double y = resolveDoubleFromObject(map.get("y"), context, 0.0);
                    double z = resolveDoubleFromObject(map.get("z"), context, 0.0);
                    return Optional.of(new Vector(x, y, z));
                } catch (ClassCastException | NullPointerException e) { /* Logged in resolveDouble */ }
            }
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.size() == 3) {
                try {
                    double x = resolveDoubleFromObject(list.get(0), context, 0.0);
                    double y = resolveDoubleFromObject(list.get(1), context, 0.0);
                    double z = resolveDoubleFromObject(list.get(2), context, 0.0);
                    return Optional.of(new Vector(x, y, z));
                } catch (IndexOutOfBoundsException | NullPointerException e) { /* Logged in resolveDouble */ }
            }
        }
        // <<< 불필요한 오류 로그 제거 (정상적으로 null/기본값 처리되므로) >>>
        // System.err.println("[SkillScript Action Helper] Could not resolve Vector parameter '" + key + "'. Unsupported type: " + (value != null ? value.getClass().getName() : "null") + ", value: " + value);
        return Optional.empty();
    }

    private double resolveDoubleFromObject(Object obj, ExecutionContext context, double defaultValue) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        } else if (obj instanceof String) {
            Optional<Double> resolved = context.resolveNumericValue((String) obj);
            if (resolved.isPresent()) return resolved.get();
            else {
                try { return Double.parseDouble((String) obj); }
                catch (NumberFormatException e) { /* Fall through */ }
            }
        }
        // System.err.println("[SkillScript Action Helper] Could not resolve object to double: " + obj); // Optional log
        return defaultValue;
    }

    default Optional<Entity> getEntityParameter(Map<String, Object> params, String key, ExecutionContext context) {
        Object value = params.get(key);
        if (value instanceof Entity) {
            return Optional.of((Entity) value);
        } else if (value instanceof String) {
            return context.resolveEntity((String) value); // 문자열이면 context에 위임
        }
        // System.err.println("[SkillScript Action Helper] Could not resolve Entity parameter '" + key + "'. Unsupported type: " + (value != null ? value.getClass().getName() : "null") + ", value: " + value);
        return Optional.empty();
    }

    default Optional<List<String>> getStringListParameter(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof List) {
            List<?> potentialList = (List<?>) value;
            List<String> stringList = new ArrayList<>(); // 결과 리스트

            // 리스트의 모든 요소가 String인지 확인
            for (Object item : potentialList) {
                if (item instanceof String) {
                    stringList.add((String) item);
                } else {
                    // String이 아닌 요소 발견 시 경고 로그 (선택적) 및 실패 처리
                    System.err.println("[SkillScript Action Helper] Warning: List for key '" + key + "' contains a non-String element: " + (item != null ? item.getClass().getName() : "null"));
                    return Optional.empty(); // 유효하지 않은 리스트
                }
            }
            // 모든 요소가 String이거나 리스트가 비어있으면 성공
            return Optional.of(Collections.unmodifiableList(stringList)); // 불변 리스트 반환 (선택적)
            // 또는 return Optional.of(stringList); // 가변 리스트 반환
        }
        return Optional.empty(); // 값이 List가 아님
    }

    // Helper for getVectorParameter Map parsing
    private static Number getNumberFromMap(Map<String, Object> map, String mapKey, Number defaultNum) {
        Object mapValue = map.get(mapKey);
        if (mapValue instanceof Number) {
            return (Number) mapValue;
        } else if (mapValue instanceof String) {
            try { return Double.parseDouble((String) mapValue); }
            catch (NumberFormatException ignored) {}
        }
        return defaultNum;
    }

    /**
     * Gets a Material parameter by parsing its name (case-insensitive).
     *
     * @param params The parameter map.
     * @param key    The parameter key.
     * @return An Optional containing the Material if found, otherwise Optional.empty().
     */
    default Optional<Material> getMaterialParameter(Map<String, Object> params, String key) {
        // Use the Optional-returning getStringParameter
        return getStringParameter(params, key).flatMap(name -> {
            try {
                // Material.matchMaterial is generally more robust than valueOf
                return Optional.ofNullable(Material.matchMaterial(name.toUpperCase().replace(" ", "_")));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });
    }



    /**
     * Gets a parameter that is expected to be a Map<String, Object>.
     * This handles the necessary unchecked cast due to type erasure.
     *
     * @param params The parameter map.
     * @param key    The parameter key.
     * @return An Optional containing the Map<String, Object> if found and is a Map, otherwise Optional.empty().
     */
    default Optional<Map<String, Object>> getMapParameter(Map<String, Object> params, String key) {
        Object value = params.get(key);
        Map<String, Object> castedMap = castToMapSo(value);
        return Optional.ofNullable(castedMap);
    }

    /**
     * Gets a parameter that is expected to be a List<Map<String, Object>> (e.g., a list of actions).
     * Performs basic checks and handles the unchecked cast.
     *
     * @param params The parameter map.
     * @param key The parameter key.
     * @return An Optional containing the List<Map<String, Object>> if found and valid, otherwise Optional.empty().
     */
    @SuppressWarnings("unchecked")
    default Optional<List<Map<String, Object>>> getListOfMapsParameter(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof List) {
            List<?> potentialList = (List<?>) value;
            // Basic validation: Check if not empty and first element is a Map
            if (!potentialList.isEmpty() && !(potentialList.get(0) instanceof Map)) {
                // Log warning or handle error: List contains non-Map elements
                System.err.println("[SkillScript Action Helper] Warning: List for key '" + key + "' contains non-Map elements.");
                return Optional.empty();
            }
            // Optional: Iterate and check ALL elements are Maps for more safety
            // for(Object item : potentialList) { if (!(item instanceof Map)) return Optional.empty(); }

            try {
                // Perform the unchecked cast
                return Optional.of((List<Map<String, Object>>) potentialList);
            } catch (ClassCastException e) {
                // Should be rare if the instanceof check passes, but possible with raw types
                System.err.println("[SkillScript Action Helper] Warning: ClassCastException casting List for key '" + key + "'.");
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * 문자열 내의 SkillScript 변수 및 셀렉터 플레이스홀더를 실제 값으로 치환합니다.
     * 플레이스홀더 형식: {var:변수명} 또는 {sel:셀렉터.속성}
     * 예: "Player {var:playerName} has {sel:@Caster.Health} HP."
     *
     * @param inputString 처리할 원본 문자열.
     * @param context     변수 및 셀렉터 값 해석에 사용될 ExecutionContext.
     * @param logger      오류 로깅을 위한 Logger (선택적).
     * @param pluginPrefix 로깅 시 사용할 플러그인 접두사 (선택적).
     * @return 플레이스홀더가 실제 값으로 치환된 문자열. 값을 찾을 수 없으면 플레이스홀더가 그대로 남거나 빈 문자열로 대체될 수 있음 (정책 결정 필요).
     */
    default String processPlaceholders(String inputString, ExecutionContext context, Logger logger, String pluginPrefix) {
        if (inputString == null || inputString.isEmpty() || !inputString.contains("{")) {
            return inputString; // 처리할 플레이스홀더 없음
        }

        // 플레이스홀더 패턴: {type:identifier}
        // type: var 또는 sel
        // identifier: 변수명 또는 셀렉터 문자열 (공백 허용 안 함)
        final Pattern placeholderPattern = Pattern.compile("\\{(var|sel):([^\\s}]+)\\}");
        Matcher matcher = placeholderPattern.matcher(inputString);
        StringBuffer sb = new StringBuffer(); // 결과 문자열을 효율적으로 빌드

        while (matcher.find()) {
            String type = matcher.group(1); // "var" 또는 "sel"
            String identifier = matcher.group(2); // 변수명 또는 셀렉터
            String replacement = matcher.group(0); // 기본값: 원래 플레이스홀더 문자열

            try {
                if ("var".equals(type)) {
                    // 변수 값 가져오기
                    Object varValue = context.getVariable(identifier);
                    if (varValue != null) {
                        // --- 변수 타입별 포맷팅 추가 ---
                        if (varValue instanceof Location) {
                            Location loc = (Location) varValue;
                            replacement = String.format("%s, %.1f, %.1f, %.1f",
                                    loc.getWorld() != null ? loc.getWorld().getName() : "unknown",
                                    loc.getX(), loc.getY(), loc.getZ());
                        } else if (varValue instanceof Vector) {
                            Vector vec = (Vector) varValue;
                            replacement = String.format("%.2f, %.2f, %.2f", vec.getX(), vec.getY(), vec.getZ());
                        } else if (varValue instanceof Double) { // Double 타입 포맷팅 (기존 sel 처리 로직과 유사하게)
                            double val = (Double) varValue;
                            if (val == Math.floor(val) && !Double.isInfinite(val)) {
                                replacement = String.valueOf((long) val); // 정수면 .0 제거
                            } else {
                                replacement = String.format("%.2f", val); // 기본 소수점 2자리
                            }
                        } else {
                            // 그 외 타입은 기본 toString() 사용
                            replacement = String.valueOf(varValue);
                        }
                        // ------------------------------
                    } else {
                        if (logger != null) logger.warning(pluginPrefix + "Placeholder processing: Variable not found: " + identifier);
                    }
                } else if ("sel".equals(type)) {
                    // --- 셀렉터 처리 부분은 변경 없음 ---
                    Optional<Double> numericValue = context.resolveNumericValue(identifier);
                    if (numericValue.isPresent()) {
                        // ... (기존 숫자 포맷팅) ...
                        double val = numericValue.get();
                        if (val == Math.floor(val) && !Double.isInfinite(val)) {
                            replacement = String.valueOf((long) val);
                        } else {
                            replacement = String.format("%.2f", val);
                        }
                    } else {
                        Optional<Entity> entityValue = context.resolveEntity(identifier);
                        if (entityValue.isPresent()) {
                            replacement = entityValue.get().getName();
                        } else {
                            Optional<Location> locationValue = context.resolveLocation(identifier);
                            if (locationValue.isPresent()) {
                                // Location 포맷팅 적용
                                Location loc = locationValue.get();
                                replacement = String.format("%s, %.1f, %.1f, %.1f",
                                        loc.getWorld() != null ? loc.getWorld().getName() : "unknown",
                                        loc.getX(), loc.getY(), loc.getZ());
                            } else {
                                Optional<Vector> vectorValue = context.resolveVector(identifier);
                                if(vectorValue.isPresent()) {
                                    // Vector 포맷팅 적용
                                    Vector vec = vectorValue.get();
                                    replacement = String.format("%.2f, %.2f, %.2f", vec.getX(), vec.getY(), vec.getZ());
                                } else {
                                    // 셀렉터 못 찾음 (기존 로직)
                                    if (logger != null) logger.warning(pluginPrefix + "Placeholder processing: Selector could not be resolved: " + identifier);
                                }
                            }
                        }
                    }
                    // ---------------------------------
                }
            } catch (Exception e) {
                if (logger != null) logger.log(Level.SEVERE, pluginPrefix + "Error processing placeholder: " + matcher.group(0), e);
                // 오류 발생 시 플레이스홀더 유지
            }

            // 찾은 값으로 치환 (정규식 특수문자 이스케이프 처리)
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb); // 나머지 문자열 추가

        return sb.toString();
    }

    // Logger, pluginPrefix 없이 간단히 호출하는 오버로드 메소드
    default String processPlaceholders(String inputString, ExecutionContext context) {
        return processPlaceholders(inputString, context, null, "");
    }


    // --- Private Helper for Casting ---

    /**
     * Safely casts an Object to Map<String, Object> if possible.
     * @param obj The object to cast.
     * @return The casted Map, or null if obj is not a Map.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToMapSo(Object obj) {
        // ... (castToMapSo 구현 유지) ...
        if (obj instanceof Map) {
            try {
                // Basic check for String keys, more robust checks could be added
                ((Map<?,?>)obj).keySet().forEach(k -> {
                    if (!(k instanceof String)) throw new ClassCastException("Map key is not a String: " + k);
                });
                return (Map<String, Object>) obj;
            } catch (ClassCastException e) {
                System.err.println("[SkillScript Action Helper] Warning: Failed to cast Object to Map<String, Object>. Ensure keys are Strings and values match Object type. Details: " + e.getMessage());
                return null;
            }
        }
        return null;
    }
}
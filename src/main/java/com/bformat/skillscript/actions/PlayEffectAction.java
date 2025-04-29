package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState;
import com.bformat.skillscript.execution.ExecutionStatus;
import com.bformat.skillscript.lang.Action;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayEffectAction implements Action {

    @Override
    public ExecutionStatus execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action PlayEffect] "; // Prefix 수정

        // --- 1. 위치 해석 ---
        Optional<Location> baseLocationOpt = getLocationParameter(params, "location", context);
        if (baseLocationOpt.isEmpty()) {
            logger.warning(pluginPrefix + "Missing or invalid 'location' parameter.");
            return ExecutionStatus.ERROR("PlayEffectAction: Missing or invalid 'location' parameter.");
        }
        Location baseLocation = baseLocationOpt.get(); // 헬퍼가 clone 처리 가정

        // --- 2. 위치 오프셋 (선택적) ---
        Optional<Vector> locationOffsetOpt = getVectorParameter(params, "offset", context); // 파티클 분포 오프셋과 다른 '위치' 오프셋
        Vector locationOffset = locationOffsetOpt.orElse(new Vector(0, 0, 0));

        // --- 3. 최종 위치 계산 ---
        Location finalEffectLocation = baseLocation.add(locationOffset);
        logger.fine(pluginPrefix + "Calculated final effect location: " + finalEffectLocation);

        World world = finalEffectLocation.getWorld();
        if (world == null) {
            logger.warning(pluginPrefix + "World is null for the final effect location.");
            return ExecutionStatus.ERROR("PlayEffectAction: World is null for the final effect location.");
        }

        // --- 4. 파티클 파싱 및 재생 ---
        Optional<String> particleNameOpt = getStringParameter(params, "particle");
        if (particleNameOpt.isPresent()) {
            String particleName = particleNameOpt.get();
            try {
                Particle particle = Particle.valueOf(particleName.toUpperCase());
                Optional<Map<String, Object>> particleDataOpt = getMapParameter(params, "particleData"); // particleData 맵 가져오기
                Map<String, Object> particleData = particleDataOpt.orElse(Map.of()); // 없으면 빈 맵

                int count = getIntParameter(particleData, "count", 1, context); // count 가져오기
                double speed = getDoubleParameter(particleData, "speed", 0.0, context); // speed 가져오기
                Object extraData = null;

                // --- 파티클 분포 오프셋 처리 (수정된 부분) ---
                double pOffsetX = 0, pOffsetY = 0, pOffsetZ = 0;
                Object offsetData = particleData.get("offset"); // particleData에서 "offset" 키의 값 가져오기

                if (offsetData instanceof Map) {
                    // 값이 Map 형태 ({x: ..., y: ..., z: ...})일 경우
                    @SuppressWarnings("unchecked") // 안전하지 않지만 YAML 구조를 신뢰
                    Map<String, Object> offsetMap = (Map<String, Object>) offsetData;
                    // 내부 헬퍼나 getDoubleParameter 등을 사용하여 숫자 값 추출
                    pOffsetX = getDoubleFromMap(offsetMap, "x", 0.0, context); // context 전달
                    pOffsetY = getDoubleFromMap(offsetMap, "y", 0.0, context);
                    pOffsetZ = getDoubleFromMap(offsetMap, "z", 0.0, context);
                    logger.finer(pluginPrefix + "Parsed distribution offset from Map: x=" + pOffsetX + ", y=" + pOffsetY + ", z=" + pOffsetZ);
                } else if (offsetData instanceof Vector) {
                    // 값이 Vector 객체일 경우 (변수 참조 등)
                    Vector offsetVec = (Vector) offsetData;
                    pOffsetX = offsetVec.getX();
                    pOffsetY = offsetVec.getY();
                    pOffsetZ = offsetVec.getZ();
                    logger.finer(pluginPrefix + "Parsed distribution offset from Vector object: " + offsetVec);
                } else if (offsetData instanceof String) {
                    // 값이 문자열일 경우 (변수 이름 또는 @키워드)
                    Optional<Vector> resolvedOffsetOpt = context.resolveVector((String) offsetData);
                    if (resolvedOffsetOpt.isPresent()) {
                        Vector offsetVec = resolvedOffsetOpt.get();
                        pOffsetX = offsetVec.getX();
                        pOffsetY = offsetVec.getY();
                        pOffsetZ = offsetVec.getZ();
                        logger.finer(pluginPrefix + "Resolved distribution offset from String '" + offsetData + "' to Vector: " + offsetVec);
                    } else {
                        logger.warning(pluginPrefix + "Could not resolve string '" + offsetData + "' in particleData.offset to a Vector. Using default offset (0,0,0).");
                    }
                }
                // offsetData가 다른 타입이거나 null이면 기본값(0,0,0) 사용
                // --------------------------------------------

                // ... (기존 extraData 파싱 로직 - DustOptions, Material 등) ...
                if (particle.getDataType() == Particle.DustOptions.class) {
                    Optional<Map<String, Object>> colorMapOpt = getMapParameter(particleData, "color");
                    if (colorMapOpt.isPresent()) {
                        Map<String, Object> colorMap = colorMapOpt.get();
                        // getIntFromMap/getFloatFromMap 헬퍼 사용 고려
                        int r = getIntFromMap(colorMap, "r", 255, context);
                        int g = getIntFromMap(colorMap, "g", 0, context);
                        int b = getIntFromMap(colorMap, "b", 0, context);
                        float size = getFloatFromMap(colorMap, "size", 1.0f, context);
                        extraData = new Particle.DustOptions(Color.fromRGB(r, g, b), size);
                    } else { extraData = new Particle.DustOptions(Color.RED, 1.0f); }
                }
                else if (particle.getDataType() == ItemStack.class || particle.getDataType() == Material.class) {
                    // Material 파라미터 가져올 때도 변수명 고려 가능 (getMaterialParameter 수정 필요)
                    Optional<Material> matOpt = getMaterialParameter(particleData, "material", context); // context 전달
                    if (matOpt.isPresent()) {
                        if (particle.getDataType() == ItemStack.class) { extraData = new ItemStack(matOpt.get()); }
                        else { extraData = matOpt.get().createBlockData(); }
                    } else { logger.warning(pluginPrefix + "Particle " + particleName + " requires 'material' in particleData."); }
                }

                // 최종 위치(finalEffectLocation)에 파티클 생성 (계산된 분포 오프셋 pOffsetX/Y/Z 사용)
                world.spawnParticle(particle, finalEffectLocation, count, pOffsetX, pOffsetY, pOffsetZ, speed, extraData);
                logger.finer(pluginPrefix + "Played particle " + particleName + " at: " + finalEffectLocation + " with distribution offset: " + pOffsetX + "," + pOffsetY + "," + pOffsetZ);

            } catch (IllegalArgumentException e) {
                logger.warning(pluginPrefix + "Invalid particle name: " + particleName);
            } catch (Exception e) {
                logger.log(Level.SEVERE, pluginPrefix + "Error processing particle " + particleName, e);
            }
        }

        // --- 5. 사운드 파싱 및 재생 ---
        Optional<String> soundNameOpt = getStringParameter(params, "sound");
        if (soundNameOpt.isPresent()) {
            String soundName = soundNameOpt.get();
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                Optional<Map<String, Object>> soundDataOpt = getMapParameter(params, "soundData");
                Map<String, Object> soundData = soundDataOpt.orElse(Map.of());

                // 사운드 데이터 값도 변수 해석 가능하게 수정
                float volume = getFloatFromMap(soundData, "volume", 1.0f, context);
                float pitch = getFloatFromMap(soundData, "pitch", 1.0f, context);

                world.playSound(finalEffectLocation, sound, volume, pitch);
                logger.finer(pluginPrefix + "Played sound " + soundName + " at: " + finalEffectLocation);

            } catch (IllegalArgumentException e) {
                logger.warning(pluginPrefix + "Invalid sound name: " + soundName);
            } catch (Exception e) {
                logger.log(Level.SEVERE, pluginPrefix + "Error processing sound " + soundName, e);
            }
        }
        return ExecutionStatus.COMPLETED;
    }

    // --- 추가: Map에서 숫자 값 추출 헬퍼 (변수 해석 기능 포함) ---

    private double getDoubleFromMap(Map<String, Object> map, String key, double defaultValue, ExecutionContext context) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            Optional<Double> resolved = context.resolveNumericValue((String) value);
            if (resolved.isPresent()) return resolved.get();
            try { return Double.parseDouble((String) value); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private int getIntFromMap(Map<String, Object> map, String key, int defaultValue, ExecutionContext context) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            Optional<Double> resolved = context.resolveNumericValue((String) value);
            if (resolved.isPresent()) return resolved.get().intValue();
            try { return Integer.parseInt((String) value); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private float getFloatFromMap(Map<String, Object> map, String key, float defaultValue, ExecutionContext context) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        } else if (value instanceof String) {
            Optional<Double> resolved = context.resolveNumericValue((String) value);
            if (resolved.isPresent()) return resolved.get().floatValue();
            try { return Float.parseFloat((String) value); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    // Material 파라미터 해석 헬퍼 (변수 이름 문자열 지원 추가)
    private Optional<Material> getMaterialParameter(Map<String, Object> map, String key, ExecutionContext context) {
        Object value = map.get(key);
        String materialName = null;
        if (value instanceof Material) {
            return Optional.of((Material) value);
        } else if (value instanceof String) {
            materialName = (String) value;
            // 변수 값 확인 (변수에 Material 이름 문자열이 저장된 경우)
            Object varValue = context.getVariable(materialName);
            if (varValue instanceof Material) return Optional.of((Material)varValue);
            if (varValue instanceof String) materialName = (String)varValue; // 변수 값으로 이름 대체
            // else: 변수가 없거나 타입이 다르면 원래 문자열(materialName) 사용
        }

        if (materialName != null) {
            try {
                return Optional.ofNullable(Material.matchMaterial(materialName.toUpperCase().replace(" ", "_")));
            } catch (IllegalArgumentException e) { /* Fall through */ }
        }
        return Optional.empty();
    }
}
package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState;
import com.bformat.skillscript.execution.ExecutionStatus;
import com.bformat.skillscript.lang.Action;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector; // Vector 임포트 확인

import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

// PlayEffectAtLocationAction은 이제 이 클래스로 통합됨
public class PlayEffectAction implements Action {

    @Override
    public ExecutionStatus execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action] ";

        // --- 1. 기본 위치 해석 ---
        // "location" 파라미터는 필수. 변수명, @키워드 등을 사용.
        Optional<Location> baseLocationOpt = getLocationParameter(params, "location", context);
        if (baseLocationOpt.isEmpty()) {
            logger.warning(pluginPrefix + "PlayEffectAction: Missing or invalid 'location' parameter. Use a variable name or keywords like '@CasterLocation', '@TargetLocation'.");
            return ExecutionStatus.ERROR("PlayEffectAction: Missing or invalid 'location' parameter. Use a variable name or keywords like '@CasterLocation', '@TargetLocation'.");
        }
        Location baseLocation = baseLocationOpt.get(); // 헬퍼가 이미 clone 처리

        // --- 2. 오프셋 벡터 해석 (선택적) ---
        // "offset" 파라미터로 Vector 변수명, @키워드, 리터럴 벡터 지정 가능.
        Optional<Vector> offsetVectorOpt = getVectorParameter(params, "offset", context);
        Vector offsetVector = offsetVectorOpt.orElse(new Vector(0, 0, 0)); // 오프셋 없으면 0벡터

        // --- 3. 최종 위치 계산 ---
        Location finalEffectLocation = baseLocation.add(offsetVector); // baseLocation은 clone된 상태이므로 원본 변경 없음
        logger.fine(pluginPrefix + "PlayEffectAction: Calculated final effect location: " + finalEffectLocation);

        World world = finalEffectLocation.getWorld();
        if (world == null) {
            logger.warning(pluginPrefix + "PlayEffectAction: World is null for the final effect location.");
            return ExecutionStatus.ERROR("PlayEffectAction: World is null for the final effect location.");
        }

        // --- 4. 파티클 파싱 및 재생 (at finalEffectLocation) ---
        Optional<String> particleNameOpt = getStringParameter(params, "particle");
        if (particleNameOpt.isPresent()) {
            String particleName = particleNameOpt.get();
            try {
                Particle particle = Particle.valueOf(particleName.toUpperCase());
                Optional<Map<String, Object>> particleDataOpt = getMapParameter(params, "particleData");
                Map<String, Object> particleData = particleDataOpt.orElse(Map.of());

                int count = getIntParameter(particleData, "count", 1);
                double speed = getDoubleParameter(particleData, "speed", 0.0);
                Object extraData = null;

                // --- 중요: 파티클 분산 오프셋 (Location 오프셋과 다름!) ---
                double particleOffsetX = 0, particleOffsetY = 0, particleOffsetZ = 0;
                // particleData 맵 안의 "offset" 키를 찾음
                Optional<Vector> particleDistOffsetOpt = getVectorParameter(particleData, "offset", context);
                if (particleDistOffsetOpt.isPresent()) {
                    Vector particleDistOffset = particleDistOffsetOpt.get();
                    particleOffsetX = particleDistOffset.getX();
                    particleOffsetY = particleDistOffset.getY();
                    particleOffsetZ = particleDistOffset.getZ();
                    logger.finer(pluginPrefix + "PlayEffectAction: Applying particle distribution offset: " + particleDistOffset);
                }
                // --------------------------------------------------

                // ... (기존 extraData 파싱 로직 - DustOptions, Material 등) ...
                if (particle.getDataType() == Particle.DustOptions.class) {
                    Optional<Map<String, Object>> colorMapOpt = getMapParameter(particleData, "color");
                    if (colorMapOpt.isPresent()) {
                        Map<String, Object> colorMap = colorMapOpt.get();
                        int r = getIntParameter(colorMap, "r", 255);
                        int g = getIntParameter(colorMap, "g", 0);
                        int b = getIntParameter(colorMap, "b", 0);
                        float size = getFloatParameter(colorMap, "size", 1.0f);
                        extraData = new Particle.DustOptions(Color.fromRGB(r, g, b), size);
                    } else { extraData = new Particle.DustOptions(Color.RED, 1.0f); }
                }
                else if (particle.getDataType() == ItemStack.class || particle.getDataType() == Material.class) {
                    Optional<Material> matOpt = getMaterialParameter(particleData, "material");
                    if (matOpt.isPresent()) {
                        if (particle.getDataType() == ItemStack.class) { extraData = new ItemStack(matOpt.get()); }
                        else { extraData = matOpt.get().createBlockData(); }
                    } else { logger.warning(pluginPrefix + "PlayEffectAction: Particle " + particleName + " requires 'material' in particleData."); }
                }
                // ... (기타 extraData 파싱) ...


                // 최종 위치(finalEffectLocation)에 파티클 생성 (분산 오프셋 적용)
                world.spawnParticle(particle, finalEffectLocation, count, particleOffsetX, particleOffsetY, particleOffsetZ, speed, extraData);
                logger.finer(pluginPrefix + "Played particle " + particleName + " at: " + finalEffectLocation);

            } catch (IllegalArgumentException e) {
                logger.warning(pluginPrefix + "PlayEffectAction: Invalid particle name: " + particleName);
            } catch (Exception e) {
                logger.log(Level.SEVERE, pluginPrefix + "PlayEffectAction: Error processing particle " + particleName, e);
            }
        }

        // --- 5. 사운드 파싱 및 재생 (at finalEffectLocation) ---
        Optional<String> soundNameOpt = getStringParameter(params, "sound");
        if (soundNameOpt.isPresent()) {
            String soundName = soundNameOpt.get();
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                Optional<Map<String, Object>> soundDataOpt = getMapParameter(params, "soundData");
                Map<String, Object> soundData = soundDataOpt.orElse(Map.of());

                float volume = getFloatParameter(soundData, "volume", 1.0f);
                float pitch = getFloatParameter(soundData, "pitch", 1.0f);

                // 최종 위치(finalEffectLocation)에 사운드 재생
                world.playSound(finalEffectLocation, sound, volume, pitch);
                logger.finer(pluginPrefix + "Played sound " + soundName + " at: " + finalEffectLocation);

            } catch (IllegalArgumentException e) {
                logger.warning(pluginPrefix + "PlayEffectAction: Invalid sound name: " + soundName);
            } catch (Exception e) {
                logger.log(Level.SEVERE, pluginPrefix + "PlayEffectAction: Error processing sound " + soundName, e);
            }
        }
        return ExecutionStatus.COMPLETED;
    }
}
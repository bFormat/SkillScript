package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState; // ExecutionState 임포트 추가
import com.bformat.skillscript.execution.ExecutionStatus;
import com.bformat.skillscript.lang.Action;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DamageAction implements Action {

    @Override
    public ExecutionStatus execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action] ";

        // --- 대상 엔티티 결정 (Using Action helpers and Context) ---
        Optional<Entity> targetEntityOpt = getEntityParameter(params, "target", context) // Try parsing "target" param
                .or(() -> Optional.ofNullable(context.getCurrentTargetAsEntity())); // Fallback to current context target

        if (targetEntityOpt.isEmpty()) {
            logger.warning(pluginPrefix + "DamageAction: Could not determine a valid target entity.");
            return ExecutionStatus.ERROR("DamageAction: Could not determine a valid target entity."); // State modification not needed
        }

        Entity targetEntity = targetEntityOpt.get();

        if (!(targetEntity instanceof Damageable)) {
            logger.fine(pluginPrefix + "DamageAction: Target is not Damageable: " + targetEntity.getType());
            return ExecutionStatus.COMPLETED; // State modification not needed
        }
        Damageable damageableTarget = (Damageable) targetEntity;

        // --- 피해량 파싱 ---
        // Use getDoubleParameter, ensure it's positive
        double amount = getDoubleParameter(params, "amount", -1.0, context);
        if (amount <= 0) {
            logger.warning(pluginPrefix + "DamageAction: Invalid or missing 'amount' parameter (must be > 0). Value was: " + params.get("amount"));
            return ExecutionStatus.ERROR("DamageAction: Invalid or missing 'amount' parameter (must be > 0). Value was: " + params.get("amount")); // State modification not needed
        }

        // --- 피해 속성 파싱 ---
        // Optional<String> damageTypeStr = getStringParameter(params, "type"); // Example: "FIRE", "MAGIC" - Bukkit DamageCause might be better
        boolean ignoreArmor = getBooleanParameter(params, "ignoreArmor", false, context);

        // --- 피해 발생원 결정 ---
        Optional<Entity> damageSourceOpt = getEntityParameter(params, "source", context) // Try parsing "source" param
                .or(() -> Optional.ofNullable(context.getCaster())); // Fallback to caster
        Entity damageSourceEntity = damageSourceOpt.orElse(null); // Can be null if caster is somehow null

        // --- 실제 피해 적용 ---
        try {
            if (ignoreArmor && damageableTarget instanceof LivingEntity) {
                // Standard Bukkit damage doesn't directly support ignoring armor easily.
                // You might need NMS or specific damage types/events for this.
                // For now, log a warning and apply standard damage.
                logger.fine(pluginPrefix + "DamageAction: 'ignoreArmor: true' requested, but applying standard damage via Bukkit API.");
                // Alternative: ((LivingEntity) damageableTarget).setNoDamageTicks(0); // Reset immunity ticks before damage? Risky.
            }

            // Apply damage using Bukkit API
            damageableTarget.damage(amount, damageSourceEntity); // Pass source entity if available
            logger.fine(pluginPrefix + "DamageAction: Applied " + amount + " damage to " + damageableTarget.getName() + (damageSourceEntity != null ? " from " + damageSourceEntity.getName() : ""));

        } catch (Exception e) {
            // Catch potential errors during the damage event or application
            logger.log(Level.SEVERE, pluginPrefix + "DamageAction: Error applying damage to " + damageableTarget.getName(), e);
        }

        // This action completes immediately. No state modification needed.

        return ExecutionStatus.COMPLETED;
    }

    // Helper methods like getDoubleParameter, getBooleanParameter, getEntityParameter are now in the Action interface
}
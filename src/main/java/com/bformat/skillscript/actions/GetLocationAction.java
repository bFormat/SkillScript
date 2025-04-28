package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState;
import com.bformat.skillscript.lang.Action;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player; // <-- Player도 필요합니다 (resolveEntity 내부에서 사용될 수 있음)

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class GetLocationAction implements Action {
    @Override
    public void execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action] ";

        Optional<String> targetIdentifierOpt = getStringParameter(params, "target"); // 예: "@Caster", "@Target", "myEntityVar"
        Optional<String> variableNameOpt = getStringParameter(params, "variable"); // 저장할 변수 이름

        if (targetIdentifierOpt.isEmpty() || variableNameOpt.isEmpty()) {
            logger.warning(pluginPrefix + "GetLocationAction: Missing 'target' or 'variable' parameter.");
            return;
        }

        String targetIdentifier = targetIdentifierOpt.get();
        String variableName = variableNameOpt.get();

        Optional<Location> locationOpt = Optional.empty();

        // 먼저 엔티티로 해석 시도
        Optional<Entity> entityOpt = context.resolveEntity(targetIdentifier);
        if (entityOpt.isPresent()) {
            locationOpt = Optional.of(entityOpt.get().getLocation());
        } else {
            // 엔티티가 아니면 Location 자체로 해석 시도
            locationOpt = context.resolveLocation(targetIdentifier);
        }


        if (locationOpt.isPresent()) {
            context.setVariable(variableName, locationOpt.get().clone()); // 복제해서 저장
            logger.fine(pluginPrefix + "GetLocationAction: Stored location of '" + targetIdentifier + "' into variable '" + variableName + "'.");
        } else {
            logger.warning(pluginPrefix + "GetLocationAction: Could not resolve location for target '" + targetIdentifier + "'.");
        }
    }
}
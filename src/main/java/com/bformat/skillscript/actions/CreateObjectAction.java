package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState; // ExecutionState 임포트 추가
import com.bformat.skillscript.execution.ExecutionStatus;
import com.bformat.skillscript.lang.Action;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CreateObjectAction implements Action {

    @Override
    public ExecutionStatus execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action] ";

        logger.fine(pluginPrefix + "Executing CreateObjectAction");

        // --- 파라미터 파싱 (Action 인터페이스 헬퍼 사용) ---
        // Required parameter: Location
        Optional<Location> initialLocationOpt = getLocationParameter(params, "initialLocation", context)
                .or(() -> Optional.ofNullable(context.getCurrentTargetAsLocation())); // Fallback to current target

        if (initialLocationOpt.isEmpty()) {
            logger.severe(pluginPrefix + "CreateObjectAction: Missing or invalid 'initialLocation' and couldn't resolve from current target. Cannot create object.");
            return ExecutionStatus.ERROR("CreateObjectAction: Missing or invalid 'initialLocation' and couldn't resolve from current target. Cannot create object."); // State modification not needed for immediate failure
        }
        Location initialLocation = initialLocationOpt.get();

        // Optional parameters
        Optional<String> objectIdOpt = getStringParameter(params, "objectId"); // Example: Optional ID
        int lifespan = getIntParameter(params, "lifespan", -1, context); // Ticks or similar unit
        Optional<Map<String, Object>> appearanceOpt = getMapParameter(params, "appearance"); // 변경 후
        Optional<Vector> initialVectorOpt = getVectorParameter(params, "initialVector", context);
        Optional<Map<String, Object>> shapeDefinitionOpt = getMapParameter(params, "shapeDefinition"); // 변경 후
        Optional<List<String>> tagsOpt = getStringListParameter(params, "tags"); // 변경 후 (새 헬퍼 사용)


        // --- 실제 오브젝트 생성 로직 (ObjectManager 사용 - Placeholder) ---
        logger.warning(pluginPrefix + "CreateObjectAction: Object creation logic is not implemented yet.");

        // Example usage of parsed optional parameters:
        objectIdOpt.ifPresent(id -> logger.fine(pluginPrefix + "Object ID specified: " + id));
        initialVectorOpt.ifPresent(vec -> logger.fine(pluginPrefix + "Initial vector specified: " + vec));

        /*
        try {
            // Object newObject = objectManager.create(initialLocation, lifespan, appearanceOpt.orElse(null), ...);
            Object newObject = null; // Placeholder for the created object

            if (newObject != null) {
                context.setCurrentObject(newObject); // Update context if needed
                // logger.fine(pluginPrefix + "CreateObjectAction: Created object with ID: " + newObject.getId());
                processNestedBehaviours(newObject, params, context); // Process nested behaviours if applicable
            } else {
                 logger.severe(pluginPrefix + "CreateObjectAction: Failed to create object.");
            }
        } catch (Exception e) {
             logger.log(Level.SEVERE, pluginPrefix + "CreateObjectAction: Error during object creation.", e);
        }
        */

        // This action currently completes immediately. No state modification needed.
        // If object creation involved delays or waiting, state.setDelay() might be used.

        return ExecutionStatus.COMPLETED;
    }

    // Helper to process potential nested behaviours defined within the CreateObject parameters
    @SuppressWarnings("unchecked")
    private void processNestedBehaviours(Object spellObject, Map<String, Object> params, ExecutionContext context) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action] ";

        logger.fine(pluginPrefix + "Processing nested behaviours for created object...");
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            // Example: Look for keys starting with "ObjectBehaviour." like "ObjectBehaviour.OnTick"
            if (key.toLowerCase().startsWith("objectbehaviour.")) {
                if (entry.getValue() instanceof List) {
                    try {
                        // Potentially attach these actions to the spellObject's event handlers
                        List<Map<String, Object>> nestedActions = (List<Map<String, Object>>) entry.getValue();
                        logger.fine(pluginPrefix + "CreateObjectAction: Found nested behaviour '" + key + "' with " + nestedActions.size() + " actions (logic not implemented).");
                        // Example: spellObject.attachBehaviour(key.substring("objectbehaviour.".length()), nestedActions);
                    } catch (ClassCastException e) {
                        logger.warning(pluginPrefix + "CreateObjectAction: Invalid format for nested behaviour '" + key + "'. Expected List<Map>.");
                    }
                } else {
                    logger.warning(pluginPrefix + "CreateObjectAction: Invalid format for nested behaviour '" + key + "'. Expected a List.");
                }
            }
        }
    }
}
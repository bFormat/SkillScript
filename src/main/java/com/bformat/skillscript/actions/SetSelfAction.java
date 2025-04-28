package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState; // ExecutionState 임포트 추가
import com.bformat.skillscript.lang.Action;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.logging.Logger;

public class SetSelfAction implements Action {

    @Override
    public void execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action] ";

        Player caster = context.getCaster();
        if (caster != null) {
            context.setCurrentTarget(caster);
            logger.fine(pluginPrefix + "SetSelfAction: Set CurrentTarget to Caster (" + caster.getName() + ")");
        } else {
            // This should ideally not happen if the context is created correctly
            logger.severe(pluginPrefix + "SetSelfAction: Caster is null in ExecutionContext!");
        }

        // This action completes immediately. No state modification needed.
    }
}
package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState;
import com.bformat.skillscript.execution.ExecutionStatus; // Import ExecutionStatus
import com.bformat.skillscript.lang.Action;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Action to pause the execution of the current script branch (or the entire script
 * if in a sequential block) for a specified number of server ticks.
 */
public class DelayAction implements Action {

    @Override
    public ExecutionStatus execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action Delay] ";

        // Get the duration parameter, expected in server ticks.
        int durationTicks = getIntParameter(params, "duration", -1);

        // Validate the duration.
        if (durationTicks < 0) {
            logger.warning(pluginPrefix + "Missing or invalid 'duration' parameter (must be a non-negative integer representing ticks). Defaulting to 0 ticks (no delay).");
            durationTicks = 0; // Treat invalid duration as no delay.
        }

        // Return the appropriate ExecutionStatus.
        if (durationTicks > 0) {
            // logger.info(pluginPrefix + "Requesting delay for " + durationTicks + " ticks.");
            // Return DELAY status. ScriptTask will handle pausing based on this.
            return ExecutionStatus.DELAY(durationTicks); // Use factory method
        } else {
            // logger.finest(pluginPrefix + "Duration is 0, executing immediately (COMPLETED).");
            // 0-tick delay means the action completes immediately.
            return ExecutionStatus.COMPLETED;
        }
    }
}
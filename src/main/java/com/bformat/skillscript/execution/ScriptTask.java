package com.bformat.skillscript.execution;

import com.bformat.skillscript.SkillScript;
import com.bformat.skillscript.actions.ActionRegistry;
import com.bformat.skillscript.lang.Action;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin; // Import Plugin

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a running instance of a SkillScript.
 * Each ScriptTask is processed by the central ScriptRunner every tick.
 */
public class ScriptTask {

    private final Plugin plugin; // Use Plugin interface type
    private final ActionRegistry actionRegistry;
    private final ExecutionContext executionContext;
    private final ExecutionState executionState;
    private final Logger logger;
    private final String pluginPrefix; // Include task ID in prefix for clarity

    private final UUID scriptTaskId;
    private final UUID casterUUID;
    private boolean cancelled = false;

    /**
     * Constructor for ScriptTask.
     * @param plugin The main plugin instance.
     * @param actionRegistry The ActionRegistry instance.
     * @param executionContext The context for this script execution.
     * @param initialActions The list of top-level actions to execute.
     * @param taskId A unique ID for this task instance.
     */
    public ScriptTask(SkillScript plugin, ActionRegistry actionRegistry,
                      ExecutionContext executionContext, List<Map<String, Object>> initialActions, UUID taskId) {
        this.plugin = plugin;
        this.actionRegistry = actionRegistry;
        this.executionContext = executionContext;
        this.scriptTaskId = taskId;
        this.casterUUID = executionContext.getCaster().getUniqueId();
        this.logger = plugin.getLogger(); // Use the plugin's logger
        this.pluginPrefix = "[SkillScript Task " + taskId.toString().substring(0, 4) + "] "; // Shortened Task ID prefix

        // Pass the logger to ExecutionState
        this.executionState = new ExecutionState(initialActions, this.logger);
        logger.info(this.pluginPrefix + "Task created for player " + executionContext.getCaster().getName() + ". Initial stack size: " + executionState.getExecutionStackSize());
    }

    /**
     * Executes one tick of logic for this script task.
     * This involves checking for completion, handling delays, and executing the next action(s).
     * @return true if the task should continue running, false if it has completed or encountered a fatal error.
     */
    public boolean tick() {
        // logger.finest(pluginPrefix + "Tick Start"); // Use finest for tick spam

        // --- Pre-checks ---
        if (cancelled) {
            logger.info(pluginPrefix + "Task removed (was cancelled).");
            return false; // Stop if cancelled
        }

        Player caster = plugin.getServer().getPlayer(casterUUID);
        if (caster == null || !caster.isOnline()) {
            logger.warning(pluginPrefix + "Caster " + casterUUID + " is no longer online. Cancelling task.");
            this.cancel(); // Mark as cancelled
            return false; // Stop execution
        }

        // --- Execution Stack Processing ---
        try {
            // --- 1. Handle Finished Frames ---
            // logger.finest(pluginPrefix + "Checking for finished frames... Stack size: " + executionState.getExecutionStackSize());
            // Loop while the stack isn't empty AND the top frame is finished
            while (!executionState.isExecutionFinished() && executionState.isCurrentFrameFinished()) {
                ControlFlowFrame poppedFrame = executionState.getCurrentFrame(); // Get frame before popping for logging
                logger.finest(pluginPrefix + "Popping finished frame type " + (poppedFrame != null ? poppedFrame.type : "NULL") + ". Stack size before pop: " + executionState.getExecutionStackSize());
                executionState.endCurrentBlock(executionContext); // Pops frame, handles loop continuation
                logger.finest(pluginPrefix + "Frame popped. Stack size after pop: " + executionState.getExecutionStackSize());
            }

            // --- 2. Check Overall Completion ---
            if (executionState.isExecutionFinished()) {
                logger.info(pluginPrefix + "Execution finished (stack empty). Task removed.");
                return false; // Script is done
            }
            // logger.finest(pluginPrefix + "Finished checking frames.");

            // --- 3. Check Global Delay (for sequential blocks) ---
            // isDelaying() now ticks the counter internally
            if (executionState.isDelaying()) {
                logger.finest(pluginPrefix + "Task is globally delaying.");
                return true; // Still delaying, continue task next tick
            }

            // --- 4. Process Current Frame ---
            ControlFlowFrame currentFrame = executionState.getCurrentFrame();
            // This null check should theoretically not be needed after the isExecutionFinished check, but belts and braces.
            if (currentFrame == null) {
                logger.severe(pluginPrefix + "CRITICAL - currentFrame is null after completion/delay checks! Cancelling.");
                this.cancel();
                return false;
            }
            logger.finest(pluginPrefix + "Processing frame type: " + currentFrame.type);

            // --- 5. Execute Actions Based on Frame Type ---
            if (currentFrame.type == FrameType.PARALLEL) {
                return processParallelFrame(currentFrame);
            } else {
                // For sequential frames, pass the frame itself to handle state changes correctly
                return processSequentialFrame(currentFrame); // <<< Pass the frame
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, pluginPrefix + "Unhandled exception during main tick processing! Cancelling.", e);
            this.cancel();
            return false; // Stop task on unhandled error
        } finally {
            // logger.finest(pluginPrefix + "Tick End");
        }
    }

    /**
     * Processes all branches within a PARALLEL frame for the current tick.
     * @param parallelFrame The current parallel control flow frame.
     * @return Always true, as the parallel frame completion is checked at the start of the next tick.
     */
    private boolean processParallelFrame(ControlFlowFrame parallelFrame) {
        logger.finest(pluginPrefix + "Processing PARALLEL frame.");
        if (parallelFrame.parallelBranches == null) {
            logger.severe(pluginPrefix + "CRITICAL - PARALLEL frame has null branches! Cancelling.");
            this.cancel();
            return false; // Indicate error
        }

        boolean anyBranchActive = false; // Track if any branch did something (for logging/debugging)
        for (int i = 0; i < parallelFrame.parallelBranches.size(); i++) {
            ParallelBranchState branch = parallelFrame.parallelBranches.get(i);
            if (branch == null) {
                logger.warning(pluginPrefix + "Branch " + i + " is NULL! Skipping.");
                continue;
            }
            if (branch.isFinished()) {
                // logger.finest(pluginPrefix + "Branch " + i + " is finished. Skipping.");
                continue;
            }

            anyBranchActive = true; // At least one branch is not finished

            // --- Check Branch Delay ---
            // isBranchDelaying() ticks the counter internally
            if (executionState.isBranchDelaying(i)) {
                logger.finest(pluginPrefix + "Branch " + i + " is delaying. Skipping action.");
                continue; // Skip action execution for this branch this tick
            }

            // --- Execute Next Action in Branch ---
            logger.finest(pluginPrefix + "Branch " + i + " getting next action. Index: " + branch.actionIndex);
            Map<String, Object> actionMap = branch.getNextActionMap();
            if (actionMap == null) {
                logger.finest(pluginPrefix + "Branch " + i + " has no more actions. Marking finished.");
                branch.finished = true; // Mark branch as done if no more actions
                continue;
            }

            String actionName = actionMap.keySet().iterator().next();
            logger.finest(pluginPrefix + "Branch " + i + " executing action: " + actionName);
            ExecutionStatus status = executeSingleAction(actionMap); // Execute the action
            logger.finest(pluginPrefix + "Branch " + i + " action status: " + status.getClass().getSimpleName());

            // --- Handle Action Status ---
            switch (status) {
                case ExecutionStatus.Completed completed -> {
                    executionState.incrementBranchActionIndex(i); // Move to next action
                    // Check if the increment made the branch finish
                    if (branch.isIndexPastEnd()) {
                        logger.finest(pluginPrefix + "Branch " + i + " finished after completing action.");
                        branch.finished = true;
                    }
                }
                case ExecutionStatus.Delay delay -> {
                    // Action requested a delay for this branch
                    logger.info(pluginPrefix + "Branch " + i + " action requested Delay for " + delay.ticks() + " ticks.");
                    // Set the delay ticks (setBranchDelay handles logging)
                    executionState.setBranchDelay(i, (int) delay.ticks());
                    // IMPORTANT: Increment index past the Delay action itself.
                    // The actual pausing happens via isBranchDelaying() in the next ticks.
                    executionState.incrementBranchActionIndex(i);
                    logger.finest(pluginPrefix + "Branch " + i + " index incremented past Delay action.");
                }
                case ExecutionStatus.Error error -> {
                    logger.severe(pluginPrefix + "Branch " + i + " action returned ERROR: " + error.message() + ". Marking branch finished.");
                    branch.finished = true; // Stop this branch on error
                }
            }
        } // End branch loop

        if (!anyBranchActive && !parallelFrame.isParallelFinished()) {
            // This case might happen if all branches become null or finish unexpectedly in the same tick.
            logger.warning(pluginPrefix + "Parallel frame has no active branches but isn't marked finished yet. Forcing finish check.");
            // Let the standard finish check handle it next tick.
        }

        // Parallel frame continues until isCurrentFrameFinished() detects all branches are done.
        return true;
    }

    /**
     * Processes the next action in a sequential (non-parallel) frame.
     * Handles the case where the action itself pushes a new frame onto the stack.
     *
     * @param sequentialFrame The current sequential control flow frame whose action is to be executed. <<< PARAM ADDED
     * @return true if the task should continue, false on fatal error or completion.
     */
    private boolean processSequentialFrame(ControlFlowFrame sequentialFrame) { // <<< PARAM ADDED
        logger.finest(pluginPrefix + "Processing SEQUENTIAL frame.");

        // Global delay was already checked in tick() method.
        // Get the action from the *passed-in* frame, not necessarily the top of the stack anymore.
        Map<String, Object> actionMap = null;
        if (!sequentialFrame.isNonParallelFinished() && sequentialFrame.actionList != null && sequentialFrame.actionIndex >= 0 && sequentialFrame.actionIndex < sequentialFrame.actionList.size()) {
            actionMap = sequentialFrame.actionList.get(sequentialFrame.actionIndex);
        }


        if (actionMap == null) {
            // This should ideally be caught by isCurrentFrameFinished() before calling this method.
            logger.warning(pluginPrefix + "Sequential action map is null unexpectedly (frame finished?). Frame index: " + sequentialFrame.actionIndex + ", List size: " + (sequentialFrame.actionList != null ? sequentialFrame.actionList.size() : "NULL") + ". Returning true for safety.");
            // Ensure the frame is marked finished if index is out of bounds.
            return true; // Let the finish check handle it next tick
        }

        String actionName = actionMap.keySet().iterator().next();
        logger.finest(pluginPrefix + "Executing action: " + actionName + " at index " + sequentialFrame.actionIndex);

        // *** Execute the action ***
        ExecutionStatus status = executeSingleAction(actionMap); // This might push a new frame

        logger.finest(pluginPrefix + "Action status: " + status.getClass().getSimpleName());

        // --- Handle Action Status ---
        // !!! IMPORTANT: Apply index/delay changes to the *original* sequentialFrame !!!
        switch (status) {
            case ExecutionStatus.Completed completed -> {
                sequentialFrame.actionIndex++; // Increment index of the frame whose action just ran
                logger.finest(pluginPrefix + "Sequential action completed. Incremented original frame index to " + sequentialFrame.actionIndex);
                return true; // Continue task
            }
            case ExecutionStatus.Delay delay -> {
                // Action requested a global delay
                logger.info(pluginPrefix + "Action requested global Delay for " + delay.ticks() + " ticks.");
                // Set the global delay ticks
                executionState.setDelay((int) delay.ticks());
                // IMPORTANT: Increment index of the *original* frame past the Delay action.
                sequentialFrame.actionIndex++;
                logger.finest(pluginPrefix + "Index of original frame incremented past Delay action to " + sequentialFrame.actionIndex);
                return true; // Continue task (delay check will pause next tick)
            }
            case ExecutionStatus.Error error -> {
                logger.severe(pluginPrefix + "Action returned ERROR: " + error.message() + ". Cancelling task.");
                this.cancel();
                return false; // Stop task on error
            }
            default -> { // Should not happen with sealed interface
                logger.severe(pluginPrefix + "Unknown ExecutionStatus received! Status: " + status + ". Cancelling task.");
                this.cancel();
                return false;
            }
        }
    }


    /**
     * Helper method to parse parameters and execute a single action.
     * @param actionMap The map representing the action and its parameters.
     * @return The ExecutionStatus returned by the action's execute method.
     */
    private ExecutionStatus executeSingleAction(Map<String, Object> actionMap) {
        if (actionMap == null || actionMap.isEmpty()) {
            logger.warning(pluginPrefix + "Attempted to execute null or empty action map.");
            return ExecutionStatus.COMPLETED; // Treat as no-op
        }

        // Assuming the action map has exactly one entry: { "actionName": {params...} }
        Map.Entry<String, Object> actionEntry = actionMap.entrySet().iterator().next();
        String actionFullName = actionEntry.getKey();
        Map<String, Object> params = parseParams(actionEntry.getValue()); // Parse parameters safely

        Action action = actionRegistry.getAction(actionFullName);

        if (action == null) {
            logger.warning(pluginPrefix + "Action implementation not found for '" + actionFullName + "'. Skipping.");
            return ExecutionStatus.COMPLETED; // Treat unknown actions as completed
        }

        try {
            logger.finest(pluginPrefix + "Executing Action: " + actionFullName);
            // Execute the action, passing context, state, and parsed parameters
            return action.execute(executionContext, executionState, params);
        } catch (Exception e) {
            // Catch unexpected errors during action execution
            String errorMsg = "Unhandled exception during execution of action '" + actionFullName + "'";
            logger.log(Level.SEVERE, pluginPrefix + errorMsg, e);
            // Return an error status to potentially stop the script/branch
            return ExecutionStatus.ERROR(errorMsg + ": " + e.getMessage());
        }
    }

    /**
     * Safely parses the parameters object from the action map.
     * Expected input is a Map<String, Object> or null.
     * @param paramsObject The object potentially containing parameters.
     * @return A Map<String, Object> of parameters, or an empty map if input is invalid or null.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(Object paramsObject) {
        if (paramsObject instanceof Map) {
            // Basic validation: check if keys are strings
            for(Object key : ((Map<?, ?>) paramsObject).keySet()){
                if(!(key instanceof String)){
                    logger.warning(pluginPrefix + "Invalid parameter map key type. Expected String, got " + key.getClass().getName() + ". Map: " + paramsObject);
                    // Optionally return empty or try to recover, returning empty for safety.
                    return new HashMap<>();
                }
            }
            try {
                // Cast is now safer after key check
                return (Map<String, Object>) paramsObject;
            } catch (ClassCastException e) {
                // Should be less likely now, but handle just in case
                logger.warning(pluginPrefix + "Parameter map cast failed despite key check. Map: " + paramsObject + ", Error: " + e.getMessage());
                return new HashMap<>();
            }
        }
        if (paramsObject == null) {
            return new HashMap<>(); // Null params is valid (no parameters)
        }
        // Log if it's not a Map or null
        logger.warning(pluginPrefix + "Invalid parameter format. Expected Map<String, Object> or null, got: " + paramsObject.getClass().getName() + ". Value: " + paramsObject);
        return new HashMap<>(); // Return empty map for other invalid types
    }

    /** Marks this task as cancelled. The task will stop processing on the next tick. */
    public void cancel() {
        if (!this.cancelled) {
            // logger.info(pluginPrefix + "Marking task as cancelled."); // Logged by runner on removal
            this.cancelled = true;
        }
    }

    /** Checks if the task has been cancelled. */
    public boolean isCancelled() {
        return this.cancelled;
    }

    /** Gets the unique ID of this script task instance. */
    public UUID getScriptTaskId() {
        return scriptTaskId;
    }

    /** Gets the UUID of the player who initiated this script task. */
    public UUID getCasterUUID() {
        return casterUUID;
    }
}
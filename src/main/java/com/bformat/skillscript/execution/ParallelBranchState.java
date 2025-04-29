package com.bformat.skillscript.execution;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger; // Added import

/**
 * Holds the execution state for a single branch within a parallel block.
 */
class ParallelBranchState {
    final List<Map<String, Object>> actionList;
    int actionIndex;
    // long delayEndTime = -1; // Removed time-based delay
    int delayTicksRemaining = 0; // Added: Remaining delay ticks (0 means no delay)
    boolean finished = false;
    private final Logger logger; // Added logger
    private final String logPrefix = "[SkillScript BranchState] "; // Added prefix

    /**
     * Constructor for ParallelBranchState.
     * @param actionList The list of actions for this branch.
     * @param logger The logger instance to use.
     */
    ParallelBranchState(List<Map<String, Object>> actionList, Logger logger) {
        this.actionList = actionList != null ? List.copyOf(actionList) : List.of(); // Use immutable list
        this.actionIndex = 0;
        // Ensure logger is not null
        this.logger = (logger != null) ? logger : Logger.getLogger(ParallelBranchState.class.getName());
        // Removed redundant logging here, state creation logged elsewhere
    }

    /**
     * Checks if this branch has finished executing all its actions.
     * @return true if finished, false otherwise.
     */
    boolean isFinished() {
        // this.logger.finest(logPrefix + "isFinished() check: flag=" + this.finished); // Fine-grained logging
        return this.finished;
    }

    /**
     * Gets the next action map to be executed in this branch.
     * Returns null if the branch is finished or the index is out of bounds.
     * @return The next action map, or null.
     */
    Map<String, Object> getNextActionMap() {
        // logger.finest(logPrefix + "getNextActionMap: Current index=" + actionIndex + ", List size=" + actionList.size() + ", finished=" + finished);
        if (!this.finished && actionIndex >= 0 && actionIndex < actionList.size()) {
            Map<String, Object> action = actionList.get(actionIndex);
            // logger.finest(logPrefix + "getNextActionMap: Returning action at index " + actionIndex);
            return action;
        }
        // logger.finest(logPrefix + "getNextActionMap: Conditions not met or index out of bounds. Returning null.");
        return null;
    }

    /**
     * Checks if the current action index is past the end of the action list.
     * @return true if the index is out of bounds, false otherwise.
     */
    boolean isIndexPastEnd() {
        boolean pastEnd = actionIndex >= actionList.size();
        // logger.finest(logPrefix + "isIndexPastEnd: index=" + actionIndex + ", size=" + actionList.size() + " -> " + pastEnd);
        return pastEnd;
    }

    /**
     * Sets the remaining delay ticks for this branch.
     * A duration <= 0 clears any existing delay.
     * @param durationTicks The number of ticks to delay.
     */
    void setDelay(int durationTicks) {
        if (durationTicks > 0) {
            this.delayTicksRemaining = durationTicks;
            // logger.info(logPrefix + "setDelay: Delay set for " + durationTicks + " ticks."); // More appropriate level
        } else {
            if (this.delayTicksRemaining > 0) { // Only log clearing if there was a delay
                // logger.info(logPrefix + "setDelay: Delay cleared (duration <= 0).");
            }
            this.delayTicksRemaining = 0;
        }
    }

    /**
     * Checks if the branch is currently delaying and decrements the tick counter if it is.
     * This should be called once per server tick.
     * @return true if the branch is still delaying *after* this tick check, false otherwise.
     */
    boolean tickDelay() {
        if (this.delayTicksRemaining > 0) {
            this.delayTicksRemaining--;
            if (this.delayTicksRemaining > 0) {
                // logger.finest(logPrefix + "tickDelay: Still delaying, " + this.delayTicksRemaining + " ticks remaining.");
                return true; // Still delaying for next tick
            } else {
                // logger.info(logPrefix + "tickDelay: Delay finished this tick."); // More appropriate level
                return false; // Delay just finished
            }
        }
        return false; // Was not delaying
    }
}
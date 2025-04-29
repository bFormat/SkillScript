package com.bformat.skillscript.execution;

import java.util.List;
import java.util.Map;
import java.util.ArrayDeque; // For potential stack implementation
import java.util.Deque;    // For potential stack implementation
import java.util.logging.Logger; // Logger 추가
import java.util.ArrayList; // ArrayList 추가


/**
 * Represents a single frame on the execution stack.
 * It holds the list of actions for the current block (e.g., main script, loop body, conditional block)
 * and the index of the next action to execute within that list.
 */
// ControlFlowFrame 수정
class ControlFlowFrame {
    final List<Map<String, Object>> actionList;
    int actionIndex;
    final FrameType type;
    LoopState loopState;
    final List<ParallelBranchState> parallelBranches;
    private final Logger logger; // Added logger
    private final String logPrefix = "[SkillScript Frame] "; // Added prefix

    // Constructor for non-parallel frames
    ControlFlowFrame(List<Map<String, Object>> actionList, FrameType type, Logger logger) { // Added logger parameter
        this.actionList = actionList != null ? actionList : List.of();
        this.actionIndex = 0;
        this.type = type;
        this.loopState = null;
        this.parallelBranches = null;
        this.logger = (logger != null) ? logger : Logger.getLogger(ControlFlowFrame.class.getName()); // Added logger assignment
        this.logger.info(logPrefix + "Non-Parallel Frame created: Type=" + type + ", ActionCount=" + this.actionList.size());
    }

    // Constructor for PARALLEL frames
    ControlFlowFrame(List<ParallelBranchState> branches, Logger logger) { // Added logger parameter
        this.actionList = null;
        this.actionIndex = -1;
        this.type = FrameType.PARALLEL;
        this.loopState = null;
        this.parallelBranches = branches;
        this.logger = (logger != null) ? logger : Logger.getLogger(ControlFlowFrame.class.getName()); // Added logger assignment
        this.logger.info(logPrefix + "PARALLEL Frame created with " + (branches != null ? branches.size() : 0) + " branches.");
    }

    // Checks if a non-parallel frame is finished
    boolean isNonParallelFinished() {
        boolean finished = actionIndex >= (actionList != null ? actionList.size() : 0);
        logger.info(logPrefix + "isNonParallelFinished (Type " + type + "): index=" + actionIndex + ", size=" + (actionList != null ? actionList.size() : "NULL") + " -> " + finished);
        return finished;
    }

    // Checks if a PARALLEL frame is finished (all branches done)
    boolean isParallelFinished() {
        logger.info(logPrefix + "isParallelFinished checking frame...");
        if (parallelBranches == null) {
            logger.warning(logPrefix + "isParallelFinished: parallelBranches is null! Returning true.");
            return true;
        }
        logger.info(logPrefix + "isParallelFinished: Checking " + parallelBranches.size() + " branches.");
        int i = 0;
        for (ParallelBranchState branch : parallelBranches) {
            if (branch == null) {
                logger.warning(logPrefix + "isParallelFinished: Branch " + i + " is NULL! Skipping check for this branch.");
                i++;
                continue; // Skip null branches, but maybe this indicates an error?
            }
            if (!branch.isFinished()) { // isFinished logs internally
                logger.info(logPrefix + "isParallelFinished: Branch " + i + " is NOT finished. Returning false.");
                return false; // Found an unfinished branch
            }
            logger.info(logPrefix + "isParallelFinished: Branch " + i + " IS finished.");
            i++;
        }
        logger.info(logPrefix + "isParallelFinished: All branches checked and are finished. Returning true.");
        return true; // All branches are finished
    }
}


/**
 * Holds the state specific to a running loop.
 * Associated with a ControlFlowFrame of type LOOP.
 */
// LoopState requires a Logger instance now
class LoopState {
    final String variableName;
    final FrameType loopType;
    double counter;
    final double endValue;
    final double step;
    final List<?> listToIterate;
    int listIteratorIndex;
    private final Logger logger;
    private final String logPrefix = "[SkillScript LoopState] ";

    // Numeric Loop Constructor
    LoopState(String variableName, double start, double end, double step, Logger logger) {
        this.variableName = variableName;
        this.loopType = FrameType.NUMERIC_LOOP;
        this.counter = start;
        this.endValue = end;
        this.step = step;
        this.listToIterate = null;
        this.listIteratorIndex = -1;
        this.logger = (logger != null) ? logger : Logger.getLogger(LoopState.class.getName());
        this.logger.info(logPrefix + "Numeric LoopState created for '" + variableName + "': start=" + start + ", end=" + end + ", step=" + step);
    }

    // List Iterator Loop Constructor
    LoopState(String variableName, List<?> list, Logger logger) {
        this.variableName = variableName;
        this.loopType = FrameType.LIST_ITERATOR_LOOP;
        this.listToIterate = list;
        this.listIteratorIndex = 0;
        this.counter = Double.NaN;
        this.endValue = Double.NaN;
        this.step = Double.NaN;
        this.logger = (logger != null) ? logger : Logger.getLogger(LoopState.class.getName());
        this.logger.info(logPrefix + "List Iterator LoopState created for '" + variableName + "' with list size " + (list != null ? list.size() : "NULL"));
    }

    boolean shouldContinue() {
        boolean result = false;
        if (loopType == FrameType.NUMERIC_LOOP) {
            if (step > 0) result = counter <= endValue;
            else if (step < 0) result = counter >= endValue;
            else result = false; // Step 0 case
            logger.info(logPrefix + "shouldContinue (Numeric '" + variableName + "'): counter=" + counter + ", end=" + endValue + ", step=" + step + " -> " + result);
        } else if (loopType == FrameType.LIST_ITERATOR_LOOP) {
            result = listToIterate != null && listIteratorIndex < listToIterate.size();
            logger.info(logPrefix + "shouldContinue (List '" + variableName + "'): index=" + listIteratorIndex + ", size=" + (listToIterate != null ? listToIterate.size() : "NULL") + " -> " + result);
        } else {
            logger.warning(logPrefix + "shouldContinue: Unknown loop type!");
        }
        return result;
    }

    Object advanceAndGetValue() {
        Object value = null;
        if (loopType == FrameType.NUMERIC_LOOP) {
            value = counter; // Get current value before advancing
            counter += step;
            logger.info(logPrefix + "advanceAndGetValue (Numeric '" + variableName + "'): Returning " + value + ", next counter=" + counter);
        } else if (loopType == FrameType.LIST_ITERATOR_LOOP) {
            if (listToIterate != null && listIteratorIndex < listToIterate.size()) {
                value = listToIterate.get(listIteratorIndex);
                listIteratorIndex++;
                logger.info(logPrefix + "advanceAndGetValue (List '" + variableName + "'): Returning element at index " + (listIteratorIndex-1) + " ("+value+"), next index=" + listIteratorIndex);
            } else {
                logger.warning(logPrefix + "advanceAndGetValue (List '" + variableName + "'): Index out of bounds or null list!");
            }
        } else {
            logger.warning(logPrefix + "advanceAndGetValue: Unknown loop type!");
        }
        return value;
    }

    Object getInitialValue() {
        Object value = null;
        if (loopType == FrameType.NUMERIC_LOOP) {
            value = counter;
            logger.info(logPrefix + "getInitialValue (Numeric '" + variableName + "'): Returning initial counter value: " + value);
        } else if (loopType == FrameType.LIST_ITERATOR_LOOP) {
            if (listToIterate != null && listIteratorIndex < listToIterate.size()) {
                value = listToIterate.get(listIteratorIndex); // Get element at index 0
                logger.info(logPrefix + "getInitialValue (List '" + variableName + "'): Returning initial element at index " + listIteratorIndex + ": " + value);
            } else {
                logger.warning(logPrefix + "getInitialValue (List '" + variableName + "'): List is null or empty, cannot get initial value.");
            }
        } else {
            logger.warning(logPrefix + "getInitialValue: Unknown loop type!");
        }
        return value;
    }
}

/**
 * Manages the execution flow state of a script, including the action stack,
 * control flow blocks (loops, conditionals, parallel), and delays.
 */
public class ExecutionState {

    private final Deque<ControlFlowFrame> executionStack = new ArrayDeque<>();
    // private long globalDelayEndTime = -1; // Removed time-based delay
    private int globalDelayTicksRemaining = 0; // Added: Remaining global delay ticks (for sequential blocks)
    private final Logger logger;
    private final String logPrefix = "[SkillScript State] ";

    /**
     * Constructor using a default logger.
     * @param initialActionList The initial list of actions for the script. Cannot be null.
     */
    public ExecutionState(List<Map<String, Object>> initialActionList) {
        this(initialActionList, Logger.getLogger(ExecutionState.class.getName())); // Use default logger
        // this.logger.info(logPrefix + "Initialized ExecutionState using default logger.");
    }

    /**
     * Constructor with a specific logger instance.
     * @param initialActionList The initial list of actions for the script. Cannot be null.
     * @param logger The logger instance to use. If null, a default logger is used.
     */
    public ExecutionState(List<Map<String, Object>> initialActionList, Logger logger) {
        // Ensure logger is not null
        this.logger = (logger != null) ? logger : Logger.getLogger(ExecutionState.class.getName());

        if (initialActionList == null) {
            this.logger.severe(logPrefix + "CRITICAL: Initial action list cannot be null.");
            throw new IllegalArgumentException("Initial action list cannot be null.");
        }
        // Push the initial block onto the stack, passing the logger to the frame
        executionStack.push(new ControlFlowFrame(List.copyOf(initialActionList), FrameType.BLOCK, this.logger));
        // this.logger.info(logPrefix + "Initialized ExecutionState. Initial stack size: " + executionStack.size());
    }

    /**
     * Gets the current size of the execution stack.
     * @return The number of frames currently on the stack.
     */
    public int getExecutionStackSize() {
        return executionStack.size();
    }

    /**
     * Gets the current control flow frame (the one at the top of the stack).
     * @return The current ControlFlowFrame, or null if the stack is empty.
     */
    public ControlFlowFrame getCurrentFrame() {
        return executionStack.peek();
    }

    /**
     * Checks if the entire script execution has finished (the stack is empty).
     * @return true if the execution stack is empty, false otherwise.
     */
    public boolean isExecutionFinished() {
        boolean isEmpty = executionStack.isEmpty();
        // logger.finest(logPrefix + "isExecutionFinished() called. Stack empty: " + isEmpty);
        return isEmpty;
    }

    /**
     * Checks if the *current* frame (top of the stack) has completed all its actions or branches.
     * @return true if the current frame is finished, false otherwise. Also returns true if the stack is empty.
     */
    public boolean isCurrentFrameFinished() {
        // logger.finest(logPrefix + "isCurrentFrameFinished() checking top frame...");
        ControlFlowFrame currentFrame = getCurrentFrame();
        if (currentFrame == null) {
            // logger.finest(logPrefix + "isCurrentFrameFinished: No current frame (stack likely empty). Returning true.");
            return true; // No frame means finished
        }
        // logger.finest(logPrefix + "isCurrentFrameFinished: Found frame of type " + currentFrame.type);

        boolean finished = switch (currentFrame.type) {
            case BLOCK, NUMERIC_LOOP, LIST_ITERATOR_LOOP -> currentFrame.isNonParallelFinished(); // Method logs internally if needed
            case PARALLEL -> currentFrame.isParallelFinished(); // Method logs internally if needed
        };
        // logger.finest(logPrefix + "isCurrentFrameFinished: Frame type " + currentFrame.type + ". Result: " + finished);
        return finished;
    }

    /**
     * Gets the next action map for a specific branch in a PARALLEL frame.
     * @param branchIndex The index of the branch.
     * @return The action map, or null if not applicable or no more actions.
     */
    public Map<String, Object> getBranchNextActionMap(int branchIndex) {
        ControlFlowFrame frame = getCurrentFrame();
        if (frame != null && frame.type == FrameType.PARALLEL && frame.parallelBranches != null && branchIndex >= 0 && branchIndex < frame.parallelBranches.size()) {
            ParallelBranchState branch = frame.parallelBranches.get(branchIndex);
            if(branch != null) {
                return branch.getNextActionMap(); // Delegate to branch state
            } else {
                // logger.warning(logPrefix + "getBranchNextActionMap: Branch at index " + branchIndex + " is NULL.");
                return null;
            }
        }
        // logger.warning(logPrefix + "getBranchNextActionMap: Conditions not met. Frame type: " + (frame != null ? frame.type : "NULL") + ", Branch index: " + branchIndex);
        return null;
    }

    /**
     * Gets the next action map for a non-parallel (sequential) frame.
     * @return The action map, or null if not applicable or no more actions.
     */
    public Map<String, Object> getNextActionMap() {
        ControlFlowFrame frame = getCurrentFrame();
        if (frame != null && frame.type != FrameType.PARALLEL) {
            if (frame.isNonParallelFinished()) { // Check finished status before accessing index
                // logger.finest(logPrefix + "getNextActionMap: Non-parallel frame is finished. Returning null.");
                return null;
            }
            if (frame.actionList != null && frame.actionIndex >= 0 && frame.actionIndex < frame.actionList.size()) {
                Map<String, Object> action = frame.actionList.get(frame.actionIndex);
                // logger.finest(logPrefix + "getNextActionMap: Returning action at index " + frame.actionIndex + " for non-parallel frame.");
                return action;
            } else {
                // logger.warning(logPrefix + "getNextActionMap: Index out of bounds or null action list for non-parallel frame. Index: " + frame.actionIndex + ", List size: " + (frame.actionList != null ? frame.actionList.size() : "NULL"));
                return null;
            }
        }
        // logger.warning(logPrefix + "getNextActionMap: Not a non-parallel frame. Type: " + (frame != null ? frame.type : "NULL"));
        return null;
    }

    /**
     * Increments the action index for a specific branch in a PARALLEL frame.
     * @param branchIndex The index of the branch.
     */
    public void incrementBranchActionIndex(int branchIndex) {
        ControlFlowFrame frame = getCurrentFrame();
        if (frame != null && frame.type == FrameType.PARALLEL && frame.parallelBranches != null && branchIndex >= 0 && branchIndex < frame.parallelBranches.size()) {
            ParallelBranchState branch = frame.parallelBranches.get(branchIndex);
            if (branch != null) {
                // int oldIndex = branch.actionIndex; // For logging if needed
                branch.actionIndex++;
                // logger.finest(logPrefix + "incrementBranchActionIndex: Incremented branch " + branchIndex + " index from " + oldIndex + " to " + branch.actionIndex);
            } else {
                // logger.warning(logPrefix + "incrementBranchActionIndex: Branch at index " + branchIndex + " is NULL.");
            }
        } else {
            // logger.warning(logPrefix + "incrementBranchActionIndex: Conditions not met or invalid index.");
        }
    }

    /**
     * Increments the action index for the current non-parallel frame.
     */
    public void incrementCurrentActionIndex() {
        ControlFlowFrame frame = getCurrentFrame();
        if (frame != null && frame.type != FrameType.PARALLEL) {
            // int oldIndex = frame.actionIndex; // For logging if needed
            frame.actionIndex++;
            // logger.finest(logPrefix + "incrementCurrentActionIndex: Incremented non-parallel frame index from " + oldIndex + " to " + frame.actionIndex);
        } else {
            // logger.warning(logPrefix + "incrementCurrentActionIndex: Cannot increment index for frame type: " + (frame != null ? frame.type : "NULL"));
        }
    }

    /**
     * Sets the delay for a specific branch in the current PARALLEL frame.
     * Delegates to the ParallelBranchState.
     * @param branchIndex The index of the branch.
     * @param durationTicks The delay duration in server ticks. A value <= 0 clears the delay.
     */
    public void setBranchDelay(int branchIndex, int durationTicks) { // Parameter type changed to int
        ControlFlowFrame frame = getCurrentFrame();
        if (frame != null && frame.type == FrameType.PARALLEL && frame.parallelBranches != null && branchIndex >= 0 && branchIndex < frame.parallelBranches.size()) {
            ParallelBranchState branch = frame.parallelBranches.get(branchIndex);
            if (branch != null) {
                branch.setDelay(durationTicks); // Delegate to ParallelBranchState's setDelay
                // logger.info(logPrefix + "setBranchDelay: Delegated delay setting for branch " + branchIndex + " (" + durationTicks + " ticks).");
            } else {
                // logger.warning(logPrefix + "setBranchDelay: Branch at index " + branchIndex + " is NULL.");
            }
        } else {
            // logger.warning(logPrefix + "setBranchDelay: Conditions not met or invalid index. Frame type: " + (frame != null ? frame.type : "NULL") + ", Branch index: " + branchIndex);
        }
    }

    /**
     * Checks if a specific branch in the current PARALLEL frame is delaying AND decrements its delay counter.
     * This should be called once per tick for each active branch.
     * Delegates to ParallelBranchState.tickDelay().
     *
     * @param branchIndex The index of the branch.
     * @return true if the branch is still delaying *after* this tick check, false otherwise.
     */
    public boolean isBranchDelaying(int branchIndex) { // Return value meaning changed
        ControlFlowFrame frame = getCurrentFrame();
        if (frame != null && frame.type == FrameType.PARALLEL && frame.parallelBranches != null && branchIndex >= 0 && branchIndex < frame.parallelBranches.size()) {
            ParallelBranchState branch = frame.parallelBranches.get(branchIndex);
            if (branch != null) {
                boolean stillDelaying = branch.tickDelay(); // Delegate to tickDelay and return its result
                // logger.finest(logPrefix + "isBranchDelaying: Branch " + branchIndex + " tickDelay returned: " + stillDelaying);
                return stillDelaying;
            } else {
                // logger.warning(logPrefix + "isBranchDelaying: Branch at index " + branchIndex + " is NULL.");
                return false; // Null branch is not delaying
            }
        }
        // logger.finest(logPrefix + "isBranchDelaying: Conditions not met or invalid index.");
        return false; // Not a parallel frame or invalid index means not delaying
    }

    /**
     * Sets the global delay for sequential execution blocks.
     * @param durationTicks The delay duration in server ticks. A value <= 0 clears the delay.
     */
    public void setDelay(int durationTicks) { // Parameter type changed to int
        if (durationTicks > 0) {
            this.globalDelayTicksRemaining = durationTicks;
            // logger.info(logPrefix + "setDelay: Global delay set for " + durationTicks + " ticks.");
        } else {
            if (this.globalDelayTicksRemaining > 0) { // Only log clearing if there was a delay
                // logger.info(logPrefix + "setDelay: Global delay cleared (duration <= 0).");
            }
            this.globalDelayTicksRemaining = 0;
        }
    }

    /**
     * Checks if the task is globally delaying (for sequential blocks) AND decrements its delay counter.
     * This should be called once per tick *before* processing sequential actions.
     * @return true if the task is still globally delaying *after* this tick check, false otherwise.
     */
    public boolean isDelaying() { // Return value meaning changed
        if (this.globalDelayTicksRemaining > 0) {
            this.globalDelayTicksRemaining--; // Decrement remaining ticks
            if (this.globalDelayTicksRemaining > 0) {
                // logger.finest(logPrefix + "isDelaying: Still globally delaying, " + this.globalDelayTicksRemaining + " ticks remaining.");
                return true; // Still delaying for the next tick
            } else {
                // logger.info(logPrefix + "isDelaying: Global delay finished this tick.");
                return false; // Delay just finished
            }
        }
        return false; // Was not delaying
    }

    // --- Stack Manipulation Methods (No changes needed in their logic) ---

    /** Starts a new parallel execution block by pushing a PARALLEL frame onto the stack. */
    public void startParallelBlock(List<List<Map<String, Object>>> branchesData) {
        if (branchesData == null || branchesData.isEmpty()) {
            logger.warning(logPrefix + "startParallelBlock called with empty or null branches. Doing nothing.");
            return;
        }
        List<ParallelBranchState> branches = new ArrayList<>();
        // logger.info(logPrefix + "startParallelBlock: Creating " + branchesData.size() + " branches...");
        int branchIdx = 0;
        for (List<Map<String, Object>> branchActions : branchesData) {
            // logger.finest(logPrefix + "startParallelBlock: Creating branch " + branchIdx + "...");
            // Pass the logger to the ParallelBranchState constructor
            branches.add(new ParallelBranchState(branchActions, logger));
            branchIdx++;
        }
        // Pass the logger to the ControlFlowFrame constructor
        executionStack.push(new ControlFlowFrame(branches, logger));
        // logger.info(logPrefix + "startParallelBlock: Pushed PARALLEL frame onto stack. New stack size: " + executionStack.size());
    }

    /** Starts a new conditional execution block by pushing a BLOCK frame onto the stack. */
    public void startConditionalBlock(List<Map<String, Object>> actionsToExecute) {
        if (actionsToExecute != null && !actionsToExecute.isEmpty()) {
            // Pass the logger to the ControlFlowFrame constructor
            executionStack.push(new ControlFlowFrame(List.copyOf(actionsToExecute), FrameType.BLOCK, logger));
            // logger.info(logPrefix + "startConditionalBlock: Pushed conditional BLOCK frame onto stack with " + actionsToExecute.size() + " actions. New stack size: " + executionStack.size());
        } else {
            // logger.info(logPrefix + "startConditionalBlock: Skipped pushing empty conditional block.");
        }
    }

    /** Starts a new numeric loop by pushing a NUMERIC_LOOP frame onto the stack. */
    public void startNumericLoop(String variableName, double start, double end, double step,
                                 List<Map<String, Object>> loopBody, ExecutionContext context) {
        // logger.info(logPrefix + "startNumericLoop: Initializing loop for variable '" + variableName + "' from " + start + " to " + end + " step " + step);
        // Pass the logger to the LoopState constructor
        LoopState loopState = new LoopState(variableName, start, end, step, logger);
        if (!loopState.shouldContinue()) { // Check initial condition
            // logger.info(logPrefix + "startNumericLoop: Condition false initially, skipping loop.");
            return; // Don't push the frame if the loop won't even run once
        }
        // Pass the logger to the ControlFlowFrame constructor
        ControlFlowFrame loopFrame = new ControlFlowFrame(List.copyOf(loopBody), FrameType.NUMERIC_LOOP, logger);
        loopFrame.loopState = loopState;
        executionStack.push(loopFrame);
        Object initialValue = loopState.getInitialValue(); // Get initial value (start)
        if (variableName != null && !variableName.isBlank()) {
            // logger.info(logPrefix + "startNumericLoop: Setting initial loop variable '" + variableName + "' to " + initialValue);
            context.setVariable(variableName, initialValue); // Set variable for the first iteration
        }
        // logger.info(logPrefix + "startNumericLoop: Pushed NUMERIC_LOOP frame. New stack size: " + executionStack.size());
    }

    /** Starts a new list iterator loop by pushing a LIST_ITERATOR_LOOP frame onto the stack. */
    public void startListIteratorLoop(String variableName, List<?> list,
                                      List<Map<String, Object>> loopBody, ExecutionContext context) {
        int listSize = (list != null) ? list.size() : 0;
        // logger.info(logPrefix + "startListIteratorLoop: Initializing loop for variable '" + variableName + "' over list of size " + listSize);
        if (list == null || list.isEmpty()) {
            // logger.info(logPrefix + "startListIteratorLoop: List is empty or null, skipping loop.");
            return; // Don't push frame if list is empty
        }
        // Pass the logger to the LoopState constructor
        LoopState loopState = new LoopState(variableName, List.copyOf(list), logger); // Use immutable list
        // Pass the logger to the ControlFlowFrame constructor
        ControlFlowFrame loopFrame = new ControlFlowFrame(List.copyOf(loopBody), FrameType.LIST_ITERATOR_LOOP, logger); // Use immutable list
        loopFrame.loopState = loopState;
        executionStack.push(loopFrame);
        Object initialValue = loopState.getInitialValue(); // Get first element
        if (variableName != null && !variableName.isBlank()) {
            // logger.info(logPrefix + "startListIteratorLoop: Setting initial loop variable '" + variableName + "' to " + initialValue);
            context.setVariable(variableName, initialValue); // Set variable for the first iteration
        }
        // logger.info(logPrefix + "startListIteratorLoop: Pushed LIST_ITERATOR_LOOP frame. New stack size: " + executionStack.size());
    }

    /**
     * Ends the current control flow block (pops the frame from the stack).
     * Handles loop continuation logic: if the popped frame was a loop and should continue,
     * it resets the frame's action index and pushes it back onto the stack.
     * @param context The execution context, used to update loop variables.
     */
    public void endCurrentBlock(ExecutionContext context) {
        if (executionStack.isEmpty()) {
            logger.warning(logPrefix + "endCurrentBlock called on an empty stack!");
            return;
        }

        ControlFlowFrame finishedFrame = executionStack.pop();
        // logger.info(logPrefix + "endCurrentBlock: Popped frame of type " + finishedFrame.type + ". Stack size is now: " + executionStack.size());

        // Loop continuation logic
        if (finishedFrame.loopState != null) {
            // logger.info(logPrefix + "endCurrentBlock: Handling loop continuation for variable '" + finishedFrame.loopState.variableName + "'...");
            LoopState loopState = finishedFrame.loopState;
            Object nextValue = loopState.advanceAndGetValue(); // Get next value and advance counter/iterator
            if (loopState.shouldContinue()) { // Check if loop should run again
                // logger.info(logPrefix + "endCurrentBlock: Loop continues. Setting variable '" + loopState.variableName + "' to " + nextValue);
                if (loopState.variableName != null && !loopState.variableName.isBlank()) {
                    context.setVariable(loopState.variableName, nextValue); // Update context variable
                }
                finishedFrame.actionIndex = 0; // Reset index to the beginning of the loop body
                // logger.info(logPrefix + "endCurrentBlock: Pushing loop frame back onto stack. Stack size will be: " + (executionStack.size() + 1));
                executionStack.push(finishedFrame); // Push the frame back for the next iteration
            } else {
                // logger.info(logPrefix + "endCurrentBlock: Loop '" + loopState.variableName + "' finished.");
                // Loop finished, frame remains popped
            }
        }
        // If it wasn't a loop, the frame just stays popped.
    }
}

// Enum FrameType, Class ControlFlowFrame, Class LoopState remain unchanged internally
// Ensure ControlFlowFrame and LoopState constructors correctly receive and store the Logger instance.

// FrameType enum remains the same
enum FrameType {
    BLOCK, NUMERIC_LOOP, LIST_ITERATOR_LOOP, PARALLEL
}

// LoopState needs logger in constructor calls if you haven't already updated them
// (Assuming previous version already passed logger to LoopState constructor)
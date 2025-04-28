package com.bformat.skillscript.execution;

import java.util.List;
import java.util.Map;
import java.util.ArrayDeque; // For potential stack implementation
import java.util.Deque;    // For potential stack implementation

/**
 * Manages the execution flow state of a script or a block of actions.
 * Holds the current position within an action list and handles control flow states like delays.
 * Can be extended to support nested control flows (loops, conditionals) using a stack.
 */
public class ExecutionState {

    // Current list of actions being executed
    private List<Map<String, Object>> currentActionList;
    // Index of the *next* action to be executed in the list
    private int currentActionIndex;

    // --- Delay State ---
    // Time (in system milliseconds) when the current delay ends. -1 means no delay.
    private long delayEndTime = -1;

    // --- Loop State (Placeholder - requires Action implementations) ---
    // private int loopCounter = 0;
    // private int loopMaxCount = -1;
    // private long loopStartTime = -1;
    // private long loopMaxDuration = -1; // In ticks or ms
    // private int loopBodyStartIndex = -1;

    // --- Nested State Stack (Placeholder - requires ScriptTask modification) ---
    // private Deque<ExecutionStateSnapshot> stateStack = new ArrayDeque<>();
    // private static class ExecutionStateSnapshot {
    //     List<Map<String, Object>> actionList;
    //     int actionIndex;
    //     // Potentially save loop/other relevant state here
    // }


    public ExecutionState(List<Map<String, Object>> initialActionList) {
        if (initialActionList == null) {
            throw new IllegalArgumentException("Initial action list cannot be null.");
        }
        this.currentActionList = initialActionList;
        this.currentActionIndex = 0; // Start at the beginning
    }

    // --- Getters ---
    // No public getter for the list itself to prevent external modification
    // public List<Map<String, Object>> getCurrentActionList() { return currentActionList; }

    public int getCurrentActionIndex() { return currentActionIndex; }

    /**
     * Gets the map representing the next action to be executed.
     * Does not advance the index.
     * @return The action map, or null if the list is finished.
     */
    public Map<String, Object> getNextActionMap() {
        if (currentActionList != null && currentActionIndex >= 0 && currentActionIndex < currentActionList.size()) {
            return currentActionList.get(currentActionIndex);
        }
        return null; // Reached end of list
    }

    /**
     * Checks if the current list of actions has been completely executed.
     * @return true if finished, false otherwise.
     */
    public boolean isFinished() {
        // Considered finished if the list is null (shouldn't happen with constructor check)
        // or the index is at or beyond the list size.
        return currentActionList == null || currentActionIndex >= currentActionList.size();
    }

    // --- State Modifiers ---

    /**
     * Advances the execution index to the next action.
     * Should be called after an action is successfully processed by ScriptTask.
     */
    public void incrementActionIndex() {
        this.currentActionIndex++;
    }

    /**
     * Sets the execution index to a specific position.
     * Useful for loops or jumps (use with caution).
     * @param index The new index.
     */
    public void setCurrentActionIndex(int index) {
        // Add bounds checking?
        this.currentActionIndex = index;
    }

    // --- Delay Management ---


    /**
     * 지정된 틱만큼 스크립트 실행을 지연시킵니다.
     * @param durationTicks 지연시킬 틱 수 (1틱 = 약 50ms). 0 이하면 지연 없음.
     */
    public void setDelay(long durationTicks) {
        if (durationTicks <= 0) {
            this.delayEndTime = -1; // 지연 없음 또는 기존 지연 취소
            // System.out.println("DEBUG: Delay set to 0 or less. delayEndTime reset."); // 디버깅 로그
        } else {
            this.delayEndTime = System.currentTimeMillis() + (durationTicks * 50); // 종료 시간 계산
            // System.out.println("DEBUG: Delay set for " + durationTicks + " ticks. End time: " + this.delayEndTime); // 디버깅 로그
        }
    }

    /**
     * 현재 스크립트가 지연 상태인지 확인합니다.
     * 지연 시간이 지났다면 내부 상태를 리셋합니다.
     * @return 지연 중이면 true, 아니면 false.
     */
    public boolean isDelaying() {
        if (delayEndTime < 0) {
            return false; // 지연 상태 아님
        }
        boolean stillDelaying = System.currentTimeMillis() < delayEndTime;
        if (!stillDelaying) {
            // System.out.println("DEBUG: Delay finished. Resetting delayEndTime."); // 디버깅 로그
            delayEndTime = -1; // 지연 시간 경과, 상태 리셋
        }
        // else { System.out.println("DEBUG: Still delaying. Current: " + System.currentTimeMillis() + ", End: " + delayEndTime); } // 디버깅 로그
        return stillDelaying;
    }

    // --- Methods for Advanced Control Flow (Requires ScriptTask changes) ---

    /**
     * [Conceptual] Replaces the current action list with a new one.
     * Used by actions like 'If/Then' to execute a nested block.
     * WARNING: This replaces the rest of the current block. Need stack for proper nesting.
     * @param newActionList The list of actions for the nested block.
     */
    public void switchToNestedBlock(List<Map<String, Object>> newActionList) {
        // Logger.getLogger("SkillScript").warning("ExecutionState: switchToNestedBlock is basic and discards subsequent actions in parent block!");
        this.currentActionList = newActionList != null ? newActionList : List.of(); // Use empty list if null
        this.currentActionIndex = 0;
        this.delayEndTime = -1; // Reset delay when switching blocks? Or keep it? Policy decision.
    }

    // --- TODO: Implement stack-based methods for true nesting ---
    // public void pushState() { ... stateStack.push(snapshot()); ... }
    // public void popState() { ... restoreFromSnapshot(stateStack.pop()); ... }
    // public void insertActions(List<Map<String, Object>> actions) { ... Modify currentActionList ... }
}
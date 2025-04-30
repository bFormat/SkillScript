package com.bformat.skillscript.execution;

import com.bformat.skillscript.SkillScript;
import com.bformat.skillscript.actions.ActionRegistry;
import com.bformat.skillscript.lang.Action;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a running instance of a SkillScript.
 * Each ScriptTask is processed by the central ScriptRunner every tick.
 * MODIFIED: Executes multiple sequential actions per tick until delay/error/limit.
 */
public class ScriptTask {

    private final Plugin plugin;
    private final ActionRegistry actionRegistry;
    private final ExecutionContext executionContext;
    private final ExecutionState executionState;
    private final Logger logger;
    private final String pluginPrefix;

    private final UUID scriptTaskId;
    private final UUID casterUUID;
    private boolean cancelled = false;

    // --- 추가: 한 틱당 최대 실행 액션 수 제한 (서버 과부하 방지) ---
    // TODO: 이 값을 config.yml 에서 로드하도록 만들 수 있습니다.
    private static final int MAX_ACTIONS_PER_TICK = 100;

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
        this.logger = plugin.getLogger();
        this.pluginPrefix = "[SkillScript Task " + taskId.toString().substring(0, 4) + "] ";

        this.executionState = new ExecutionState(initialActions, this.logger);
        logger.info(this.pluginPrefix + "Task created for player " + executionContext.getCaster().getName() + ". Initial stack size: " + executionState.getExecutionStackSize());
    }

    /**
     * Executes one tick of logic for this script task.
     * This involves checking for completion, handling delays, and executing the next action(s).
     * MODIFIED: Can execute multiple actions per tick.
     * @return true if the task should continue running, false if it has completed or encountered a fatal error.
     */
    public boolean tick() {
        // --- Pre-checks (동일) ---
        if (cancelled) return false;
        Player caster = plugin.getServer().getPlayer(casterUUID);
        if (caster == null || !caster.isOnline()) {
            logger.warning(pluginPrefix + "Caster " + casterUUID + " is no longer online. Cancelling task.");
            this.cancel();
            return false;
        }

        // --- Execution Stack Processing ---
        try {
            // --- 1. Handle Finished Frames (동일) ---
            while (!executionState.isExecutionFinished() && executionState.isCurrentFrameFinished()) {
                ControlFlowFrame poppedFrame = executionState.getCurrentFrame();
                logger.finest(pluginPrefix + "Popping finished frame type " + (poppedFrame != null ? poppedFrame.type : "NULL") + ". Stack size before pop: " + executionState.getExecutionStackSize());
                executionState.endCurrentBlock(executionContext);
                logger.finest(pluginPrefix + "Frame popped. Stack size after pop: " + executionState.getExecutionStackSize());
            }

            // --- 2. Check Overall Completion (동일) ---
            if (executionState.isExecutionFinished()) {
                logger.info(pluginPrefix + "Execution finished (stack empty). Task removed.");
                return false;
            }

            // --- 3. Check Global Delay (for sequential blocks) (동일) ---
            if (executionState.isDelaying()) {
                logger.finest(pluginPrefix + "Task is globally delaying.");
                return true;
            }

            // --- 4. Process Current Frame (동일) ---
            ControlFlowFrame currentFrame = executionState.getCurrentFrame();
            if (currentFrame == null) {
                logger.severe(pluginPrefix + "CRITICAL - currentFrame is null after completion/delay checks! Cancelling.");
                this.cancel();
                return false;
            }
            logger.finest(pluginPrefix + "Processing frame type: " + currentFrame.type);

            // --- 5. Execute Actions Based on Frame Type (로직 분기) ---
            if (currentFrame.type == FrameType.PARALLEL) {
                // Parallel 프레임 처리 로직 호출 (내부적으로 멀티 액션 처리)
                return processParallelFrameMultiAction(currentFrame);
            } else {
                // Sequential 프레임 처리 로직 호출 (멀티 액션 처리)
                return processSequentialFrameMultiAction(currentFrame);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, pluginPrefix + "Unhandled exception during main tick processing! Cancelling.", e);
            this.cancel();
            return false;
        }
    }

    /**
     * MODIFIED: Processes a sequential (non-parallel) frame, executing multiple actions per tick.
     * @param sequentialFrame The current sequential control flow frame.
     * @return true if the task should continue, false on fatal error or completion.
     */
    private boolean processSequentialFrameMultiAction(ControlFlowFrame sequentialFrame) {
        logger.finest(pluginPrefix + "Processing SEQUENTIAL frame (Multi-Action/Tick).");
        int actionsExecutedThisTick = 0; // 이번 틱에 실행된 액션 수 카운터

        // 현재 프레임에 실행할 액션이 남아있고, 틱당 액션 제한에 도달하지 않은 동안 반복
        while (!sequentialFrame.isNonParallelFinished() && actionsExecutedThisTick < MAX_ACTIONS_PER_TICK) {

            // 현재 프레임에서 실행할 액션 가져오기
            Map<String, Object> actionMap = null;
            if (sequentialFrame.actionList != null && sequentialFrame.actionIndex >= 0 && sequentialFrame.actionIndex < sequentialFrame.actionList.size()) {
                actionMap = sequentialFrame.actionList.get(sequentialFrame.actionIndex);
            }

            if (actionMap == null) {
                // 이 프레임의 액션이 끝났음을 의미 (isNonParallelFinished() 에서도 확인됨)
                logger.finest(pluginPrefix + "Sequential frame has no more actions at index " + sequentialFrame.actionIndex);
                break; // 루프 종료
            }

            String actionName = actionMap.keySet().iterator().next();
            logger.finest(pluginPrefix + "Executing action (Tick " + actionsExecutedThisTick + "): " + actionName + " at index " + sequentialFrame.actionIndex);

            // 액션 실행
            ExecutionStatus status = executeSingleAction(actionMap); // 액션 실행 (새 프레임 push 가능성 있음)
            actionsExecutedThisTick++; // 실행 카운터 증가

            logger.finest(pluginPrefix + "Action status: " + status.getClass().getSimpleName());

            // 액션 결과 처리
            switch (status) {
                case ExecutionStatus.Completed completed -> {
                    // 액션 완료 -> 인덱스 증가 후 루프 계속 (같은 틱에서 다음 액션 시도)
                    sequentialFrame.actionIndex++;
                    logger.finest(pluginPrefix + "Sequential action completed. Incremented index to " + sequentialFrame.actionIndex);
                    // 새 프레임이 push 되었는지 확인 (예: if, for, parallel 실행 시)
                    if (executionState.getCurrentFrame() != sequentialFrame) {
                        logger.finest(pluginPrefix + "New frame pushed onto stack. Stopping sequential execution for this tick.");
                        return true; // 새 프레임 처리는 다음 틱에
                    }
                    // 프레임 변경 없으면 루프 계속
                }
                case ExecutionStatus.Delay delay -> {
                    // 딜레이 요청 -> 글로벌 딜레이 설정, 인덱스 증가, 루프 종료
                    logger.info(pluginPrefix + "Action requested global Delay for " + delay.ticks() + " ticks.");
                    executionState.setDelay((int) delay.ticks());
                    sequentialFrame.actionIndex++; // 딜레이 액션 다음으로 이동
                    logger.finest(pluginPrefix + "Index incremented past Delay action to " + sequentialFrame.actionIndex);
                    return true; // 루프 종료, 다음 틱에 딜레이 처리
                }
                case ExecutionStatus.Error error -> {
                    // 오류 발생 -> 태스크 취소, 루프 종료
                    logger.severe(pluginPrefix + "Action returned ERROR: " + error.message() + ". Cancelling task.");
                    this.cancel();
                    return false; // 루프 종료, 태스크 중단
                }
                // default는 sealed interface로 인해 불필요
            }
        } // End while loop

        // 틱당 액션 제한에 도달했는지 확인
        if (actionsExecutedThisTick >= MAX_ACTIONS_PER_TICK) {
            logger.warning(pluginPrefix + "Reached maximum actions per tick (" + MAX_ACTIONS_PER_TICK + ") for sequential frame. Continuing next tick.");
        }

        // 루프가 정상 종료 (프레임 끝 도달 or 틱 제한) -> 태스크는 다음 틱에 계속
        return true;
    }


    /**
     * MODIFIED: Processes all branches within a PARALLEL frame, executing multiple actions per branch per tick.
     * @param parallelFrame The current parallel control flow frame.
     * @return Always true, as the parallel frame completion is checked at the start of the next tick. Returns false only on critical internal error.
     */
    private boolean processParallelFrameMultiAction(ControlFlowFrame parallelFrame) {
        logger.finest(pluginPrefix + "Processing PARALLEL frame (Multi-Action/Tick).");
        if (parallelFrame.parallelBranches == null) {
            logger.severe(pluginPrefix + "CRITICAL - PARALLEL frame has null branches! Cancelling.");
            this.cancel();
            return false;
        }

        boolean anyBranchActive = false;

        for (int i = 0; i < parallelFrame.parallelBranches.size(); i++) {
            ParallelBranchState branch = parallelFrame.parallelBranches.get(i);
            if (branch == null || branch.isFinished()) {
                continue; // Null 이거나 이미 끝난 브랜치는 건너뜀
            }

            anyBranchActive = true;

            // --- Check Branch Delay (동일) ---
            if (executionState.isBranchDelaying(i)) {
                logger.finest(pluginPrefix + "Branch " + i + " is delaying. Skipping actions.");
                continue;
            }

            // --- Execute Multiple Actions in Branch (이번 틱에) ---
            logger.finest(pluginPrefix + "Processing actions for Branch " + i);
            int actionsExecutedThisBranchTick = 0; // 이번 틱 & 이번 브랜치에서 실행된 액션 수

            // 이 브랜치에 실행할 액션이 남아있고, 틱당 액션 제한에 도달하지 않은 동안 반복
            while (!branch.isFinished() && !branch.isIndexPastEnd() && actionsExecutedThisBranchTick < MAX_ACTIONS_PER_TICK) {

                // 브랜치 딜레이 재확인 (중요: 루프 내에서 delay 발생 시 다음 반복 방지)
                if (executionState.isBranchDelaying(i)) {
                    logger.finest(pluginPrefix + "Branch " + i + " started delaying within multi-action loop. Breaking branch loop.");
                    break; // 이 브랜치의 이번 틱 실행 중단
                }

                Map<String, Object> actionMap = branch.getNextActionMap(); // 현재 인덱스의 액션 가져오기
                if (actionMap == null) {
                    logger.finest(pluginPrefix + "Branch " + i + " has no more actions at index " + branch.actionIndex);
                    branch.finished = true; // 끝났음 표시
                    break; // 이 브랜치의 이번 틱 실행 중단
                }

                String actionName = actionMap.keySet().iterator().next();
                logger.finest(pluginPrefix + "Branch " + i + " executing action (Tick " + actionsExecutedThisBranchTick + "): " + actionName + " at index " + branch.actionIndex);

                // 액션 실행
                ExecutionStatus status = executeSingleAction(actionMap); // 액션 실행
                actionsExecutedThisBranchTick++;

                logger.finest(pluginPrefix + "Branch " + i + " action status: " + status.getClass().getSimpleName());

                // 액션 결과 처리 (브랜치 상태 변경)
                switch (status) {
                    case ExecutionStatus.Completed completed -> {
                        // 액션 완료 -> 브랜치 인덱스 증가 후 루프 계속 (같은 틱, 같은 브랜치 다음 액션 시도)
                        branch.actionIndex++;
                        logger.finest(pluginPrefix + "Branch " + i + " action completed. Incremented index to " + branch.actionIndex);
                        // 브랜치 액션이 새 프레임을 push할 수 있는지 확인 (일반적이지 않지만 가능성은 있음)
                        // 이 경우, parallel 프레임 자체는 계속되지만, 새 프레임 처리는 다음 틱에...
                        // 여기서는 별도 처리 없이 루프 계속
                    }
                    case ExecutionStatus.Delay delay -> {
                        // 딜레이 요청 -> *브랜치* 딜레이 설정, 브랜치 인덱스 증가, 브랜치 루프 종료
                        logger.info(pluginPrefix + "Branch " + i + " action requested Delay for " + delay.ticks() + " ticks.");
                        executionState.setBranchDelay(i, (int) delay.ticks()); // 브랜치 딜레이 설정
                        branch.actionIndex++; // 딜레이 액션 다음으로 이동
                        logger.finest(pluginPrefix + "Branch " + i + " index incremented past Delay action to " + branch.actionIndex);
                        break; // 이 브랜치의 이번 틱 실행 중단 (딜레이 시작)
                    }
                    case ExecutionStatus.Error error -> {
                        // 오류 발생 -> 브랜치 종료 표시, 브랜치 루프 종료
                        logger.severe(pluginPrefix + "Branch " + i + " action returned ERROR: " + error.message() + ". Marking branch finished.");
                        branch.finished = true; // 브랜치 실행 중단
                        break; // 이 브랜치의 이번 틱 실행 중단
                    }
                } // End switch

                // 액션 실행 후 브랜치가 끝났는지 확인
                if (branch.isIndexPastEnd()) {
                    logger.finest(pluginPrefix + "Branch " + i + " finished after completing action (index past end).");
                    branch.finished = true;
                    break; // 이 브랜치의 이번 틱 실행 중단
                }

            } // End while loop for branch actions

            // 틱당 액션 제한 도달 확인
            if (actionsExecutedThisBranchTick >= MAX_ACTIONS_PER_TICK) {
                logger.warning(pluginPrefix + "Reached maximum actions per tick (" + MAX_ACTIONS_PER_TICK + ") for Branch " + i + ". Continuing next tick.");
            }

        } // End for loop iterating through branches

        if (!anyBranchActive && !parallelFrame.isParallelFinished()) {
            logger.warning(pluginPrefix + "Parallel frame has no active branches but isn't marked finished yet. Forcing finish check next tick.");
        }

        // Parallel 프레임 자체는 계속 실행 (종료 여부는 다음 틱 시작 시 isCurrentFrameFinished() 에서 판단)
        return true;
    }


    // --- executeSingleAction, parseParams, cancel, isCancelled, getters (동일) ---
    private ExecutionStatus executeSingleAction(Map<String, Object> actionMap) {
        // ... (이전 코드와 동일) ...
        if (actionMap == null || actionMap.isEmpty()) {
            logger.warning(pluginPrefix + "Attempted to execute null or empty action map.");
            return ExecutionStatus.COMPLETED;
        }
        Map.Entry<String, Object> actionEntry = actionMap.entrySet().iterator().next();
        String actionFullName = actionEntry.getKey();
        Map<String, Object> params = parseParams(actionEntry.getValue());
        Action action = actionRegistry.getAction(actionFullName);
        if (action == null) {
            logger.warning(pluginPrefix + "Action implementation not found for '" + actionFullName + "'. Skipping.");
            return ExecutionStatus.COMPLETED;
        }
        try {
            logger.finest(pluginPrefix + "Executing Action: " + actionFullName);
            return action.execute(executionContext, executionState, params);
        } catch (Exception e) {
            String errorMsg = "Unhandled exception during execution of action '" + actionFullName + "'";
            logger.log(Level.SEVERE, pluginPrefix + errorMsg, e);
            return ExecutionStatus.ERROR(errorMsg + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(Object paramsObject) {
        // ... (이전 코드와 동일) ...
        if (paramsObject instanceof Map) {
            for(Object key : ((Map<?, ?>) paramsObject).keySet()){
                if(!(key instanceof String)){
                    logger.warning(pluginPrefix + "Invalid parameter map key type. Expected String, got " + key.getClass().getName() + ". Map: " + paramsObject);
                    return new HashMap<>();
                }
            }
            try {
                return (Map<String, Object>) paramsObject;
            } catch (ClassCastException e) {
                logger.warning(pluginPrefix + "Parameter map cast failed despite key check. Map: " + paramsObject + ", Error: " + e.getMessage());
                return new HashMap<>();
            }
        }
        if (paramsObject == null) {
            return new HashMap<>();
        }
        logger.warning(pluginPrefix + "Invalid parameter format. Expected Map<String, Object> or null, got: " + paramsObject.getClass().getName() + ". Value: " + paramsObject);
        return new HashMap<>();
    }

    public void cancel() {
        if (!this.cancelled) {
            this.cancelled = true;
        }
    }
    public boolean isCancelled() { return this.cancelled; }
    public UUID getScriptTaskId() { return scriptTaskId; }
    public UUID getCasterUUID() { return casterUUID; }
}
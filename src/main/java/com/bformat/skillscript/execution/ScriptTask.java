package com.bformat.skillscript.execution;

import com.bformat.skillscript.SkillScript;
import com.bformat.skillscript.actions.ActionRegistry;
import com.bformat.skillscript.lang.Action;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

// BukkitRunnable 상속 제거
public class ScriptTask {

    private final SkillScript plugin;
    private final ActionRegistry actionRegistry;
    private final ExecutionContext executionContext; // 데이터 컨텍스트
    private final ExecutionState executionState;     // 실행 흐름 상태
    private final Logger logger;
    private final String pluginPrefix = "[SkillScript Task] "; // 변경된 접두사

    private final UUID scriptTaskId; // 고유 ID
    private final UUID casterUUID;   // 시전자 UUID

    private boolean cancelled = false; // 내부 취소 플래그

    // 생성자에서 BukkitRunnable 관련 로직 제거
    public ScriptTask(SkillScript plugin, ActionRegistry actionRegistry,
                      ExecutionContext executionContext, List<Map<String, Object>> initialActions, UUID taskId) {
        this.plugin = plugin;
        this.actionRegistry = actionRegistry;
        this.executionContext = executionContext;
        this.scriptTaskId = taskId;
        this.casterUUID = executionContext.getCaster().getUniqueId();
        this.logger = plugin.getLogger();
        this.executionState = new ExecutionState(initialActions);
    }

    /**
     * 이 태스크의 한 틱 처리를 수행합니다.
     * 액션 하나를 실행하고 상태를 업데이트합니다.
     * @return 태스크가 계속 실행되어야 하면 true, 완료되거나 취소되었으면 false.
     */
    public boolean tick() {
        // --- 사전 검사 ---
        if (cancelled) {
            logger.fine(pluginPrefix + scriptTaskId + ": Task tick() called but already cancelled.");
            return false; // 이미 취소됨, 실행 중지
        }

        Player caster = plugin.getServer().getPlayer(casterUUID);
        if (caster == null || !caster.isOnline()) {
            logger.warning(pluginPrefix + scriptTaskId + ": Caster " + casterUUID + " is no longer online. Cancelling task.");
            this.cancel(); // 내부 취소 메소드 호출
            return false; // 실행 중지
        }

        if (executionState.isFinished()) {
            logger.fine(pluginPrefix + scriptTaskId + ": Execution finished normally (isFinished check).");
            return false; // 완료됨, 실행 중지
        }

        if (executionState.isDelaying()) {
            // logger.finest(pluginPrefix + scriptTaskId + ": Task delaying..."); // 너무 자주 로깅될 수 있으므로 주석 처리
            return true; // 지연 중이지만 태스크는 계속 활성 상태
        }

        // --- 액션 실행 ---
        Map<String, Object> actionMap = executionState.getNextActionMap();

        if (actionMap == null || actionMap.isEmpty()) {
            logger.warning(pluginPrefix + scriptTaskId + ": No more actions found (actionMap is null/empty). Finishing. Index: " + executionState.getCurrentActionIndex());
            return false; // 완료됨
        }

        String actionFullName = null;
        Map<String, Object> params = null;
        Action action = null;

        try {
            Map.Entry<String, Object> actionEntry = actionMap.entrySet().iterator().next();
            actionFullName = actionEntry.getKey();
            params = parseParams(actionEntry.getValue());
            action = actionRegistry.getAction(actionFullName);
        } catch (Exception e) {
            logger.log(Level.SEVERE, pluginPrefix + scriptTaskId + ": Error parsing action map at index " + executionState.getCurrentActionIndex() + ": " + actionMap, e);
            this.cancel(); // 오류 발생 시 취소
            return false;
        }

        if (action == null) {
            logger.warning(pluginPrefix + scriptTaskId + ": Action not found in registry: '" + actionFullName + "' at index " + executionState.getCurrentActionIndex() + ". Skipping.");
        } else {
            try {
                logger.fine(pluginPrefix + scriptTaskId + ": Executing action: '" + actionFullName + "' at index " + executionState.getCurrentActionIndex());
                action.execute(executionContext, executionState, params); // 액션 실행
            } catch (Exception e) {
                logger.log(Level.SEVERE, pluginPrefix + scriptTaskId + ": Error executing action '" + actionFullName + "' at index " + executionState.getCurrentActionIndex(), e);
                this.cancel(); // 오류 발생 시 취소
                return false;
            }
        }

        // --- 사후 처리 ---
        executionState.incrementActionIndex(); // 다음 액션 인덱스로

        // 다음 틱에도 계속 실행되어야 함
        return true;
    }

    /**
     * 이 태스크를 취소 상태로 설정합니다.
     */
    public void cancel() {
        if (!this.cancelled) {
            logger.info(pluginPrefix + scriptTaskId + ": Marking task as cancelled.");
            this.cancelled = true;
            // 취소 시 즉시 Runner에게 알릴 수도 있음 (선택적, tick()에서 false 반환으로 처리해도 됨)
            // plugin.getScriptRunner().taskFinished(this.scriptTaskId);
        }
    }

    /**
     * 태스크가 취소되었는지 확인합니다.
     * @return 취소되었으면 true.
     */
    public boolean isCancelled() {
        return this.cancelled;
    }

    // --- Getters ---
    public UUID getScriptTaskId() {
        return scriptTaskId;
    }

    public UUID getCasterUUID() {
        return casterUUID;
    }

    // parseParams 메소드는 유지
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(Object paramsObject) {
        if (paramsObject instanceof Map) {
            try {
                Map<?, ?> rawMap = (Map<?, ?>) paramsObject;
                Map<String, Object> stringKeyMap = new HashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    if (entry.getKey() instanceof String) {
                        stringKeyMap.put((String) entry.getKey(), entry.getValue());
                    } else {
                        logger.warning(pluginPrefix + scriptTaskId + ": Parameter map contains non-String key: " + entry.getKey() + " (type: " + entry.getKey().getClass().getName() + ")");
                    }
                }
                return stringKeyMap;
            } catch (ClassCastException e) {
                logger.warning(pluginPrefix + scriptTaskId + ": Invalid parameter format casting to Map: " + paramsObject);
            }
        } else if (paramsObject == null) {
            return new HashMap<>();
        }
        logger.warning(pluginPrefix + scriptTaskId + ": Invalid parameter format. Expected a Map or null, got: " + paramsObject.getClass().getName());
        return new HashMap<>();
    }
}
package com.bformat.skillscript.script;

import com.bformat.skillscript.SkillScript;
import com.bformat.skillscript.actions.ActionRegistry;
import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ScriptTask;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable; // BukkitRunnable 임포트
import org.bukkit.scheduler.BukkitTask;    // BukkitTask 임포트

import java.util.Iterator; // Iterator 임포트
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScriptRunner {

    private final SkillScript plugin;
    private final ActionRegistry actionRegistry;
    private final Logger logger;
    private final String pluginPrefix = "[SkillScript Runner] ";

    // 실행 중인 스크립트 태스크 관리 (ScriptTask는 더 이상 Runnable이 아님)
    private final Map<UUID, ScriptTask> runningTasks = new ConcurrentHashMap<>();

    // 중앙 실행 타이머 태스크
    private BukkitTask centralTask = null;

    public ScriptRunner(SkillScript plugin, ActionRegistry actionRegistry) {
        this.plugin = plugin;
        this.actionRegistry = actionRegistry;
        this.logger = plugin.getLogger();
    }

    /**
     * 중앙 태스크 실행기를 시작합니다. (플러그인 활성화 시 호출)
     */
    public void startRunner() {
        if (centralTask != null && !centralTask.isCancelled()) {
            logger.warning(pluginPrefix + "Runner is already running.");
            return;
        }
        logger.info(pluginPrefix + "Starting central task runner...");
        // 매 틱 실행되는 BukkitRunnable 생성 및 등록
        centralTask = new ScriptProcessorTask().runTaskTimer(plugin, 0L, 1L); // 0틱 딜레이, 1틱 간격
    }

    /**
     * 중앙 태스크 실행기를 중지합니다. (플러그인 비활성화 시 호출)
     */
    public void stopRunner() {
        if (centralTask != null && !centralTask.isCancelled()) {
            logger.info(pluginPrefix + "Stopping central task runner...");
            centralTask.cancel();
            centralTask = null;
        } else {
            logger.warning(pluginPrefix + "Runner is not running or already stopped.");
        }
        // 모든 실행 중인 태스크 강제 종료 (선택적)
        // shutdown(); // 필요 시 호출
    }


    /**
     * 지정된 플레이어를 시전자로 하여 스크립트 실행을 시작합니다.
     * 이제 태스크를 직접 스케줄링하지 않고, 관리 목록에만 추가합니다.
     *
     * @param caster 스킬 시전자
     * @param actions 실행할 스크립트의 초기 액션 목록
     * @return 생성된 스크립트 태스크의 고유 ID, 생성 실패 시 null
     */
    public UUID runScript(Player caster, List<Map<String, Object>> actions) {
        if (caster == null) {
            logger.severe(pluginPrefix + "Attempted to run script with a null caster!");
            return null;
        }
        if (actions == null || actions.isEmpty()) {
            logger.warning(pluginPrefix + "Attempted to run an empty or null script action list for player: " + caster.getName());
            return null;
        }

        ExecutionContext context = new ExecutionContext(caster);
        UUID taskId = UUID.randomUUID();
        ScriptTask task = new ScriptTask(plugin, actionRegistry, context, actions, taskId);

        // 스케줄링 대신 맵에 추가만 함
        runningTasks.put(taskId, task);
        logger.info(pluginPrefix + "Added script task " + taskId + " for player " + caster.getName() + " to runner.");
        return taskId;
    }

    /**
     * 지정된 ID의 스크립트 태스크 실행을 중지 (취소 상태로 만듦).
     * 실제 제거는 중앙 태스크의 다음 틱에서 이루어짐.
     *
     * @param taskId 중지할 태스크의 고유 ID
     * @return 태스크가 존재하면 true, 아니면 false.
     */
    public boolean stopScript(UUID taskId) {
        if (taskId == null) return false;

        ScriptTask task = runningTasks.get(taskId);
        if (task != null) {
            if (!task.isCancelled()) {
                task.cancel(); // 내부 플래그만 설정
                logger.info(pluginPrefix + "Marked script task " + taskId + " for cancellation.");
                return true;
            } else {
                logger.fine(pluginPrefix + "Task " + taskId + " requested to stop, but was already cancelled.");
                return false; // 이미 취소 요청됨
            }
        } else {
            logger.warning(pluginPrefix + "Could not stop script task " + taskId + ". Task not found in runner.");
            return false;
        }
    }

    /**
     * 특정 플레이어가 실행 중인 모든 스크립트 태스크를 중지 요청합니다.
     *
     * @param player 대상 플레이어
     * @return 중지 요청된 태스크 수
     */
    public int stopPlayerScripts(Player player) {
        if (player == null) return 0;
        UUID playerUUID = player.getUniqueId();
        int stoppedCount = 0;

        // ConcurrentHashMap을 순회하며 작업
        for (ScriptTask task : runningTasks.values()) {
            if (task.getCasterUUID().equals(playerUUID) && !task.isCancelled()) {
                task.cancel(); // 취소 요청
                stoppedCount++;
            }
        }

        if (stoppedCount > 0) {
            logger.info(pluginPrefix + "Requested cancellation for " + stoppedCount + " tasks for player: " + player.getName());
        }
        return stoppedCount;
    }


    /**
     * 플러그인 종료 시 모든 태스크를 정리합니다.
     */
    public void shutdown() {
        logger.info(pluginPrefix + "Shutting down runner and cancelling all tasks (" + runningTasks.size() + ")...");
        stopRunner(); // 중앙 태스크 중지
        // 모든 태스크에 대해 취소 호출 (선택적이지만 명시적)
        runningTasks.values().forEach(ScriptTask::cancel);
        runningTasks.clear(); // 맵 클리어
        logger.info(pluginPrefix + "Runner shutdown complete.");
    }

    /**
     * 태스크 완료/취소 시 ScriptTask 내부 또는 중앙 태스크가 호출하여 추적 맵에서 제거합니다.
     * @param taskId 완료된 태스크의 ID
     */
    public void taskFinished(UUID taskId) {
        if (taskId != null) {
            ScriptTask removedTask = runningTasks.remove(taskId);
            if (removedTask != null) {
                logger.fine(pluginPrefix + "Task " + taskId + " removed from runner.");
            }
        }
    }

    /**
     * Checks if a task with the given UUID is currently being tracked by the runner.
     * @param taskId The UUID to check.
     * @return true if the task is in the runningTasks map, false otherwise.
     */
    public boolean isTaskRunning(UUID taskId) {
        return taskId != null && runningTasks.containsKey(taskId);
    }


    // --- 중앙 태스크 실행 로직 ---
    private class ScriptProcessorTask extends BukkitRunnable {
        @Override
        public void run() {
            if (runningTasks.isEmpty()) {
                return; // 실행할 태스크 없음
            }

            // ConcurrentHashMap을 안전하게 순회하기 위해 Iterator 사용
            Iterator<Map.Entry<UUID, ScriptTask>> iterator = runningTasks.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<UUID, ScriptTask> entry = iterator.next();
                UUID taskId = entry.getKey();
                ScriptTask task = entry.getValue();

                try {
                    if (task.isCancelled()) {
                        logger.fine(pluginPrefix + "Removing cancelled task " + taskId + " during tick.");
                        iterator.remove(); // 취소된 태스크 제거
                        continue; // 다음 태스크로
                    }

                    // 태스크의 틱 로직 실행
                    boolean shouldContinue = task.tick();

                    if (!shouldContinue) {
                        // 태스크가 완료되었거나 내부적으로 취소됨
                        logger.fine(pluginPrefix + "Task " + taskId + " reported finished or cancelled, removing.");
                        iterator.remove(); // 완료/취소된 태스크 제거
                    }
                } catch (Exception e) {
                    // 개별 태스크의 tick() 실행 중 오류 발생 시 로깅하고 해당 태스크만 제거
                    logger.log(Level.SEVERE, pluginPrefix + "Error ticking task " + taskId + ". Removing task.", e);
                    task.cancel(); // 만약을 위해 취소 상태로 만듦
                    iterator.remove(); // 오류 발생 태스크 제거
                }
            }
        }
    }
}
package com.bformat.skillscript.script;

import com.bformat.skillscript.SkillScript;
import com.bformat.skillscript.actions.ActionRegistry;
import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ScriptTask;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
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

    private final Map<UUID, ScriptTask> runningTasks = new ConcurrentHashMap<>();
    private BukkitTask centralTask = null;

    public ScriptRunner(SkillScript plugin, ActionRegistry actionRegistry) {
        this.plugin = plugin;
        this.actionRegistry = actionRegistry;
        this.logger = plugin.getLogger(); // Use plugin's logger
    }

    public void startRunner() {
        if (centralTask != null && !centralTask.isCancelled()) {
            logger.warning(pluginPrefix + "Runner is already running.");
            return;
        }
        logger.info(pluginPrefix + "Starting central task runner...");
        centralTask = new ScriptProcessorTask().runTaskTimer(plugin, 0L, 1L);
    }

    public void stopRunner() {
        if (centralTask != null && !centralTask.isCancelled()) {
            logger.info(pluginPrefix + "Stopping central task runner...");
            centralTask.cancel();
            centralTask = null;
        } else {
            logger.info(pluginPrefix + "Runner not running or already stopped."); // Changed from warning
        }
    }

    public UUID runScript(Player caster, List<Map<String, Object>> actions) {
        if (caster == null) {
            logger.severe(pluginPrefix + "Attempted to run script with a null caster!");
            return null;
        }
        if (actions == null || actions.isEmpty()) {
            logger.warning(pluginPrefix + "Attempted to run an empty or null script action list for player: " + caster.getName());
            return null;
        }

        logger.info(pluginPrefix + "runScript called for player " + caster.getName());
        ExecutionContext context = new ExecutionContext(caster);
        UUID taskId = UUID.randomUUID();
        logger.info(pluginPrefix + "Creating ScriptTask with ID: " + taskId);
        // ScriptTask constructor now logs internally
        ScriptTask task = new ScriptTask(plugin, actionRegistry, context, actions, taskId);

        runningTasks.put(taskId, task);
        logger.info(pluginPrefix + "Added script task " + taskId + " for player " + caster.getName() + " to runner. Current task count: " + runningTasks.size());
        return taskId;
    }

    public boolean stopScript(UUID taskId) {
        if (taskId == null) {
            logger.warning(pluginPrefix + "stopScript called with null taskId.");
            return false;
        }
        logger.info(pluginPrefix + "stopScript called for task ID: " + taskId);
        ScriptTask task = runningTasks.get(taskId);
        if (task != null) {
            if (!task.isCancelled()) {
                logger.info(pluginPrefix + "Task " + taskId + " found. Requesting cancellation.");
                task.cancel(); // cancel() logs internally
                return true;
            } else {
                logger.info(pluginPrefix + "Task " + taskId + " was already cancelled.");
                return false; // Already cancelled
            }
        } else {
            logger.warning(pluginPrefix + "Could not stop task " + taskId + ". Task not found in runner map.");
            return false;
        }
    }

    public int stopPlayerScripts(Player player) {
        if (player == null) {
            logger.warning(pluginPrefix + "stopPlayerScripts called with null player.");
            return 0;
        }
        UUID playerUUID = player.getUniqueId();
        logger.info(pluginPrefix + "stopPlayerScripts called for player: " + player.getName() + " (UUID: " + playerUUID + ")");
        int stoppedCount = 0;

        // Iterate safely
        for (Map.Entry<UUID, ScriptTask> entry : runningTasks.entrySet()) {
            UUID taskId = entry.getKey();
            ScriptTask task = entry.getValue();
            if (task.getCasterUUID().equals(playerUUID)) {
                logger.info(pluginPrefix + "Found task " + taskId + " for player " + player.getName() + ". Checking cancellation status.");
                if (!task.isCancelled()) {
                    logger.info(pluginPrefix + "Requesting cancellation for task " + taskId);
                    task.cancel(); // cancel() logs internally
                    stoppedCount++;
                } else {
                    logger.info(pluginPrefix + "Task " + taskId + " for player " + player.getName() + " was already cancelled.");
                }
            }
        }

        logger.info(pluginPrefix + "Finished stopPlayerScripts for " + player.getName() + ". Requested cancellation for " + stoppedCount + " tasks.");
        return stoppedCount;
    }


    public void shutdown() {
        logger.info(pluginPrefix + "Shutdown requested. Stopping runner and cancelling all " + runningTasks.size() + " tasks...");
        stopRunner(); // Logs internally
        runningTasks.values().forEach(task -> {
            if (!task.isCancelled()){
                task.cancel(); // cancel() logs internally
            }
        });
        int remaining = runningTasks.size();
        runningTasks.clear();
        logger.info(pluginPrefix + "Runner shutdown complete. Cleared " + remaining + " tasks from map.");
    }

    // This might not be needed if removal happens only in the runner task
    /*
    public void taskFinished(UUID taskId) {
        if (taskId != null) {
            logger.info(pluginPrefix + "taskFinished called for task ID: " + taskId);
            ScriptTask removedTask = runningTasks.remove(taskId);
            if (removedTask != null) {
                logger.info(pluginPrefix + "Task " + taskId + " removed from runner map via taskFinished. Remaining tasks: " + runningTasks.size());
            } else {
                logger.warning(pluginPrefix + "taskFinished called for " + taskId + " but task was not found in map (might have been removed already).");
            }
        } else {
            logger.warning(pluginPrefix + "taskFinished called with null taskId.");
        }
    }
    */

    public boolean isTaskRunning(UUID taskId) {
        boolean isRunning = taskId != null && runningTasks.containsKey(taskId);
        // logger.finest(pluginPrefix + "isTaskRunning check for " + taskId + ": " + isRunning); // Maybe too verbose
        return isRunning;
    }


    // --- 중앙 태스크 실행 로직 ---
    private class ScriptProcessorTask extends BukkitRunnable {
        @Override
        public void run() {
            // logger.finest(pluginPrefix + "[Runner Tick] Processor task running..."); // Can be very spammy
            if (runningTasks.isEmpty()) {
                // logger.finest(pluginPrefix + "[Runner Tick] No tasks to process.");
                return; // 실행할 태스크 없음
            }

            // logger.info(pluginPrefix + "[Runner Tick] Processing " + runningTasks.size() + " tasks..."); // Log task count before processing
            Iterator<Map.Entry<UUID, ScriptTask>> iterator = runningTasks.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<UUID, ScriptTask> entry = iterator.next();
                UUID taskId = entry.getKey();
                ScriptTask task = entry.getValue();

                // logger.finest(pluginPrefix + "[Runner Tick] --- Processing task: " + taskId + " ---");

                try {
                    // 1. Check cancellation flag FIRST
                    if (task.isCancelled()) {
                        logger.info(pluginPrefix + "[Runner Tick] Task " + taskId + " is marked cancelled. Removing.");
                        iterator.remove();
                        logger.info(pluginPrefix + "[Runner Tick] Task " + taskId + " removed (was cancelled). Remaining: " + runningTasks.size());
                        continue; // Process next task
                    }

                    // 2. Execute the task's logic for this tick
                    // logger.finest(pluginPrefix + "[Runner Tick] Calling tick() for task " + taskId);
                    boolean shouldContinue = task.tick(); // tick() now logs extensively
                    // logger.finest(pluginPrefix + "[Runner Tick] Task " + taskId + " tick() returned: " + shouldContinue);

                    // 3. Remove if tick() indicated completion/error
                    if (!shouldContinue) {
                        logger.info(pluginPrefix + "[Runner Tick] Task " + taskId + " tick() returned false (finished or error). Removing.");
                        iterator.remove();
                        logger.info(pluginPrefix + "[Runner Tick] Task " + taskId + " removed (tick returned false). Remaining: " + runningTasks.size());
                    } else {
                        // logger.finest(pluginPrefix + "[Runner Tick] Task " + taskId + " tick() returned true. Task continues.");
                    }
                    // logger.finest(pluginPrefix + "[Runner Tick] --- Finished processing task: " + taskId + " ---");

                } catch (Exception e) {
                    logger.log(Level.SEVERE, pluginPrefix + "[Runner Tick] !! Unhandled exception while processing task " + taskId + " in runner !! Removing task.", e);
                    if (task != null) {
                        task.cancel(); // Ensure it's marked cancelled if an error occurs during tick() call itself
                    }
                    try {
                        iterator.remove(); // Safely remove the task that caused the error
                        logger.info(pluginPrefix + "[Runner Tick] Task " + taskId + " removed due to exception. Remaining: " + runningTasks.size());
                    } catch (IllegalStateException ise) {
                        logger.log(Level.SEVERE, pluginPrefix + "[Runner Tick] Error removing task " + taskId + " after exception (already removed?).", ise);
                    }
                }
            }
            // logger.finest(pluginPrefix + "[Runner Tick] Finished processing all tasks for this tick.");
        }
    }
}
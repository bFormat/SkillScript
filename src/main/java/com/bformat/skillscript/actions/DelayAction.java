package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState;
import com.bformat.skillscript.execution.ExecutionStatus;
import com.bformat.skillscript.lang.Action;

import java.util.Map;
import java.util.logging.Logger;

public class DelayAction implements Action {

    @Override
    public ExecutionStatus execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action Delay] ";

        // <<< 수정: getIntParameter 호출 시 context 전달 >>>
        int durationTicks = getIntParameter(params, "duration", -1, context);

        if (durationTicks < 0) {
            // 경고 메시지 유지 (duration이 아예 없거나 음수거나 해석 불가 시)
            logger.warning(pluginPrefix + "Missing or invalid 'duration' parameter (must be a non-negative integer/variable resolving to ticks). Defaulting to 0 ticks (no delay).");
            durationTicks = 0;
        }

        if (durationTicks > 0) {
            logger.fine(pluginPrefix + "Requesting delay for " + durationTicks + " ticks."); // 로그 레벨 fine으로 변경
            return ExecutionStatus.DELAY(durationTicks);
        } else {
            logger.finest(pluginPrefix + "Duration is 0, executing immediately (COMPLETED).");
            return ExecutionStatus.COMPLETED;
        }
    }
}
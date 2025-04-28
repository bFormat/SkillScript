package com.bformat.skillscript.actions; // actions 패키지 아래에 생성

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState;
import com.bformat.skillscript.lang.Action;

import java.util.Map;
import java.util.logging.Logger; // Logger 임포트

/**
 * ControlFlow.Delay 액션 구현체.
 * 스크립트 실행을 지정된 틱(tick) 동안 일시 중지합니다.
 */
public class DelayAction implements Action {

    @Override
    public void execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger(); // 로거 가져오기
        final String pluginPrefix = "[SkillScript Action] ";

        // "duration" 파라미터 파싱 (틱 단위)
        // 파라미터가 없거나 유효하지 않으면 기본값 1틱 사용 및 경고 로깅
        int durationTicks = getIntParameter(params, "duration", -1); // 기본값을 -1로 하여 누락 감지

        if (durationTicks < 0) {
            logger.warning(pluginPrefix + "DelayAction: Missing or invalid 'duration' parameter (must be a non-negative integer). Defaulting to 1 tick.");
            durationTicks = 1; // 잘못된 값이면 1틱으로 설정
        } else if (durationTicks == 0) {
            logger.fine(pluginPrefix + "DelayAction: Duration is 0, no delay will occur.");
            // duration이 0이면 setDelay(-1)을 호출하게 되므로 사실상 지연 없음
        }


        logger.fine(pluginPrefix + "DelayAction: Setting delay for " + durationTicks + " ticks.");

        // ExecutionState에 지연 설정 요청
        state.setDelay(durationTicks);

        // DelayAction 자체는 즉시 완료됩니다. 실제 대기는 ScriptTask의 tick() 메소드에서 처리합니다.
    }

    // getIntParameter는 Action 인터페이스에서 상속받아 사용
}
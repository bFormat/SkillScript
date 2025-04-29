package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState;
import com.bformat.skillscript.execution.ExecutionStatus;
import com.bformat.skillscript.lang.Action;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class ParallelAction implements Action {

    @Override
    @SuppressWarnings("unchecked") // For casting list elements
    public ExecutionStatus execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action] ";

        // --- 파라미터 파싱: "Branches" 키에 List<List<Map<String, Object>>> 형태 기대 ---
        Object branchesObject = params.get("Branches");
        if (!(branchesObject instanceof List)) {
            logger.warning(pluginPrefix + "ParallelAction: Missing or invalid 'Branches' parameter. Expected a List.");
            return ExecutionStatus.ERROR("Missing or invalid 'Branches' parameter.");
        }

        List<?> potentialBranches = (List<?>) branchesObject;
        List<List<Map<String, Object>>> validBranches = new ArrayList<>();

        for (Object branchObj : potentialBranches) {
            if (branchObj instanceof List) {
                List<?> potentialActionList = (List<?>) branchObj;
                // 간단한 타입 체크 (첫 요소가 Map인지)
                if (!potentialActionList.isEmpty() && !(potentialActionList.get(0) instanceof Map)) {
                    logger.warning(pluginPrefix + "ParallelAction: A branch contains non-Map elements. Skipping branch.");
                    continue; // 이 브랜치는 건너뜀
                }
                // 모든 요소가 Map인지 더 확실히 검사할 수 있음 (선택적)
                try {
                    // 여기서 실제 타입 캐스팅 시도
                    validBranches.add((List<Map<String, Object>>) potentialActionList);
                } catch (ClassCastException e) {
                    logger.warning(pluginPrefix + "ParallelAction: A branch has invalid action format. Skipping branch.");
                    // 캐스팅 실패 시 이 브랜치 건너뜀
                }
            } else {
                logger.warning(pluginPrefix + "ParallelAction: Element inside 'Branches' is not a List. Skipping element.");
            }
        }

        if (validBranches.isEmpty()) {
            logger.warning(pluginPrefix + "ParallelAction: No valid branches found in 'Branches' parameter.");
            return ExecutionStatus.ERROR("No valid branches found.");
        }

        // --- ExecutionState에 병렬 블록 시작 요청 ---
        state.startParallelBlock(validBranches);

        return ExecutionStatus.COMPLETED; // 상태 설정 후 즉시 완료
    }
}
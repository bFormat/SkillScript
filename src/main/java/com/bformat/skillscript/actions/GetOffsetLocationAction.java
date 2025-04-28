package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState;
import com.bformat.skillscript.lang.Action;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class GetOffsetLocationAction implements Action {
    @Override
    public void execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action] ";

        // 파라미터 가져오기 (Action 인터페이스 헬퍼 사용)
        Optional<Location> baseLocationOpt = getLocationParameter(params, "baseLocation", context);
        Optional<Vector> offsetVectorOpt = getVectorParameter(params, "offset", context);
        Optional<String> variableNameOpt = getStringParameter(params, "variable");

        if (baseLocationOpt.isEmpty() || offsetVectorOpt.isEmpty() || variableNameOpt.isEmpty()) {
            logger.warning(pluginPrefix + "GetOffsetLocationAction: Missing 'baseLocation', 'offset', or 'variable' parameter.");
            return;
        }

        Location baseLocation = baseLocationOpt.get(); // Optional에서 값 추출
        Vector offsetVector = offsetVectorOpt.get();
        String variableName = variableNameOpt.get();

        // 새 위치 계산 (baseLocation은 clone되어 넘어옴)
        Location offsetLocation = baseLocation.add(offsetVector);

        // 결과 저장
        context.setVariable(variableName, offsetLocation); // 계산된 Location 저장 (이미 복제됨)
        logger.fine(pluginPrefix + "GetOffsetLocationAction: Calculated offset location and stored in variable '" + variableName + "'.");
    }
}
package com.bformat.skillscript.actions;

import com.bformat.skillscript.SkillScript; // 메인 플러그인 참조
import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState; // ExecutionState 임포트 추가
import com.bformat.skillscript.lang.Action;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ActionRegistry {

    private final SkillScript plugin;
    private final Map<String, Action> actions = new HashMap<>();

    public ActionRegistry(SkillScript plugin) {
        this.plugin = plugin;
    }

    /**
     * 레지스트리에 액션을 등록합니다. 액션 이름은 소문자로 변환되어 저장됩니다.
     * @param name 액션 이름 (예: "targetbehaviour.sendmessage")
     * @param action 액션 구현 객체
     */
    public void register(String name, Action action) {
        String lowerCaseName = name.toLowerCase();
        if (actions.containsKey(lowerCaseName)) {
            plugin.getLogger().warning("Action already registered, overwriting: " + lowerCaseName);
        }
        actions.put(lowerCaseName, action);
        plugin.getLogger().fine("Registered action: " + lowerCaseName);
    }

    /**
     * 등록된 Action 구현체를 반환합니다.
     * @param name 액션 이름 (소문자 변환됨)
     * @return Action 인스턴스 또는 null
     */
    public Action getAction(String name) {
        return actions.get(name.toLowerCase());
    }

    /**
     * 등록된 액션을 실행합니다.
     * @param name 실행할 액션 이름
     * @param context 현재 실행 컨텍스트
     * @param state 현재 실행 상태
     * @param params 액션에 전달될 파라미터
     * @deprecated This method is no longer the primary way to execute actions. Use ScriptTask which calls Action.execute directly.
     */
    @Deprecated
    public void execute(String name, ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        String lowerCaseName = name.toLowerCase();
        Action action = actions.get(lowerCaseName);

        if (action != null) {
            try {
                action.execute(context, state, params != null ? params : new HashMap<>());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error executing action (via deprecated path): " + name, e);
            }
        } else {
            plugin.getLogger().warning("Attempted to execute unregistered action (via deprecated path): " + name);
        }
    }


    // --- 액션 등록 메소드 ---
    public void registerCoreActions() {
        plugin.getLogger().info("Registering core actions...");
        // 대상 행동
        register("targetbehaviour.sendmessage", new SendMessageAction());
        register("targetbehaviour.playeffect", new PlayEffectAction());
        register("targetbehaviour.damage", new DamageAction());
        // ... 다른 TargetBehaviour 액션들 ...

        // 타겟팅
        register("target.setself", new SetSelfAction());
        // register("target.setsingle", new SetSingleAction());
        // ... 다른 Target 액션들 ...

        // 오브젝트
        register("object.createobject", new CreateObjectAction());
        // ... 다른 Object 액션들 ...

        // 제어 흐름
        register("controlflow.delay", new DelayAction()); // DelayAction 구현 필요 (ExecutionState 사용)
        register("controlflow.ifcondition", new IfConditionAction()); // IfConditionAction
        register("controlflow.forloop", new ForLoopAction()); // ForLoopAction 등록
        register("controlflow.parallel", new ParallelAction()); // ParallelAction 등록
        // ... 다른 ControlFlow 액션들 ...

        // 변수/유틸리티
        register("setvariable", new SetVariableAction());
        register("variable.getlocation", new GetLocationAction());     // 새 액션 등록
        register("variable.getdirection", new GetDirectionAction());   // 새 액션 등록
        register("variable.getoffsetlocation", new GetOffsetLocationAction()); // 새 액션 등록
        register("variable.calculate", new CalculateVariableAction());
        register("variable.vectormath", new VectorMathAction());

        plugin.getLogger().info("Core actions registered.");
    }
}
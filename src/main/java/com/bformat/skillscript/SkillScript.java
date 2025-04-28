package com.bformat.skillscript;

import com.bformat.skillscript.actions.ActionRegistry; // ActionRegistry 임포트
import com.bformat.skillscript.commands.CastCommand;
import com.bformat.skillscript.commands.SkillScriptCommand;
import com.bformat.skillscript.script.ScriptManager;
import com.bformat.skillscript.script.ScriptRunner; // ScriptRunner 임포트
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class SkillScript extends JavaPlugin {

    private ScriptManager scriptManager;
    private ActionRegistry actionRegistry; // ActionRegistry 멤버 변수 추가
    private ScriptRunner scriptRunner; // ScriptRunner 멤버 변수 추가

    @Override
    public void onEnable() {
        getLogger().info("Enabling SkillScript v" + getDescription().getVersion());

        saveDefaultConfig();
        File scriptsFolder = new File(getDataFolder(), "scripts");
        if (!scriptsFolder.exists()) {
            if (scriptsFolder.mkdirs()) {
                getLogger().info("Created scripts folder: " + scriptsFolder.getPath());
            } else {
                getLogger().severe("Could not create scripts folder!");
            }
        }

        // --- 초기화 순서 유지 ---
        // 1. ActionRegistry 초기화 및 액션 등록
        this.actionRegistry = new ActionRegistry(this);
        this.actionRegistry.registerCoreActions(); // 핵심 액션들을 등록

        // 2. ScriptManager 초기화
        this.scriptManager = new ScriptManager(this);
        scriptManager.loadScripts();

        // 3. ScriptRunner 초기화 (ActionRegistry 필요)
        this.scriptRunner = new ScriptRunner(this, actionRegistry); // ScriptRunner 생성 및 의존성 주입

        // 명령어 등록 (ScriptRunner 전달)
        getCommand("cast").setExecutor(new CastCommand(scriptManager, scriptRunner)); // CastCommand에 Runner 전달
        getCommand("skillscript").setExecutor(new SkillScriptCommand(this));

        // === 추가: 중앙 Runner 시작 ===
        this.scriptRunner.startRunner();
        // ===========================

        getLogger().info("SkillScript enabled successfully.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling SkillScript.");
        if (scriptRunner != null) {
            scriptRunner.shutdown(); // Runner 종료 및 모든 태스크 정리
        }
        // 추가 정리 작업 필요 시 여기에 구현
    }

    // --- Getters ---
    public ScriptManager getScriptManager() { return scriptManager; }
    public ActionRegistry getActionRegistry() { return actionRegistry; }
    public ScriptRunner getScriptRunner() { return scriptRunner; }
}
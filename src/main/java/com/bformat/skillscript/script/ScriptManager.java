package com.bformat.skillscript.script;

import com.bformat.skillscript.SkillScript; // 메인 클래스 참조 변경
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class ScriptManager {

    private final SkillScript plugin; // 메인 클래스 타입 변경
    private final Map<String, Map<String, Object>> loadedScripts = new HashMap<>();
    private final File scriptsFolder;

    // 생성자에서 받는 타입 변경
    public ScriptManager(SkillScript plugin) {
        this.plugin = plugin;
        // 폴더 경로 생성 시에도 변경된 plugin 인스턴스 사용
        this.scriptsFolder = new File(plugin.getDataFolder(), "scripts");
    }

    public void loadScripts() {
        loadedScripts.clear();
        if (!scriptsFolder.exists() || !scriptsFolder.isDirectory()) {
            plugin.getLogger().warning("Scripts folder not found or is not a directory.");
            return;
        }

        File[] scriptFiles = scriptsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yaml") || name.toLowerCase().endsWith(".yml"));

        if (scriptFiles == null || scriptFiles.length == 0) {
            plugin.getLogger().info("No scripts found in the scripts folder.");
            return;
        }

        Yaml yamlParser = new Yaml();

        for (File scriptFile : scriptFiles) {
            String scriptName = scriptFile.getName().substring(0, scriptFile.getName().lastIndexOf('.'));
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(scriptFile), StandardCharsets.UTF_8)) {
                Map<String, Object> scriptData = yamlParser.load(reader);

                if (scriptData != null && !scriptData.isEmpty()) {
                    loadedScripts.put(scriptName.toLowerCase(), scriptData);
                    plugin.getLogger().info("Loaded script: " + scriptFile.getName());
                } else {
                    plugin.getLogger().warning("Skipping empty or invalid script file: " + scriptFile.getName());
                }

            } catch (FileNotFoundException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not find script file: " + scriptFile.getName(), e);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error loading script: " + scriptFile.getName(), e);
            }
        }
        plugin.getLogger().info("Successfully loaded " + loadedScripts.size() + " scripts.");
    }

    public Map<String, Object> getScriptData(String scriptName) {
        return loadedScripts.get(scriptName.toLowerCase());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTriggerActions(Map<String, Object> scriptData, String triggerName) {
        if (scriptData == null || !scriptData.containsKey(triggerName)) {
            return null;
        }
        Object triggerBlock = scriptData.get(triggerName);
        if (triggerBlock instanceof List) {
            try {
                // 각 액션이 Map 형태인지 더 검사할 수 있습니다.
                // 예를 들어, 리스트의 첫 번째 요소만 검사하거나 모든 요소를 검사
                List<?> potentialList = (List<?>) triggerBlock;
                if (!potentialList.isEmpty() && !(potentialList.get(0) instanceof Map)) {
                    plugin.getLogger().warning("Invalid action format in trigger '" + triggerName + "'. List elements are not Maps.");
                    return null;
                }
                return (List<Map<String, Object>>) triggerBlock;
            } catch (ClassCastException e) {
                plugin.getLogger().warning("Invalid action format in trigger '" + triggerName + "'. Expected a List of Maps.");
                return null;
            }
        } else {
            plugin.getLogger().warning("Invalid trigger format for '" + triggerName + "'. Expected a List.");
            return null;
        }
    }
}
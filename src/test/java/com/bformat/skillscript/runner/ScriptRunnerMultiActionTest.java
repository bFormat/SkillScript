package com.bformat.skillscript.runner; // Example package

import com.bformat.skillscript.SkillScript;
import com.bformat.skillscript.actions.*; // Import necessary actions
import com.bformat.skillscript.script.ScriptRunner;
import com.bformat.skillscript.actions.ActionRegistry;

import org.bukkit.ChatColor;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the ScriptRunner's multi-action-per-tick execution model.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // Use PER_CLASS for faster setup
public class ScriptRunnerMultiActionTest {

    private ServerMock server;
    private SkillScript plugin;
    private ActionRegistry actionRegistry;
    private ScriptRunner scriptRunner;

    // PlayerMock is per-test-method
    private PlayerMock caster;

    // Constants for test scripts
    private static final int LOOP_COUNT = 10;
    private static final int ACTIONS_PER_LOOP = 5; // Number of 'setvariable' actions per loop
    private static final int DELAY_TICKS = 1;

    @BeforeAll
    void setUpAll() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SkillScript.class);
        actionRegistry = plugin.getActionRegistry();
        scriptRunner = plugin.getScriptRunner();

        // Register actions needed for the tests
        // Ensure core actions are registered OR register them manually
        // actionRegistry.registerCoreActions(); // Assuming this registers needed actions
        actionRegistry.register("setvariable", new SetVariableAction());
        actionRegistry.register("controlflow.delay", new DelayAction());
        actionRegistry.register("controlflow.forloop", new ForLoopAction());
        actionRegistry.register("targetbehaviour.sendmessage", new SendMessageAction()); // For signalling completion
    }

    @AfterAll
    void tearDownAll() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() {
        // Create a fresh player for each test
        caster = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        // Clean up player and messages
        if (caster != null && caster.isOnline()) {
            scriptRunner.stopPlayerScripts(caster); // Stop any running scripts for this player
            caster.assertNoMoreSaid(); // Consume any remaining messages implicitly
        }
    }

    // --- Helper Methods ---
    private String stripColor(String msg) {
        return msg == null ? null : ChatColor.stripColor(msg);
    }

    private void assertNextMessage(String expectedMessage) {
        caster.assertSaid(stripColor(expectedMessage));
    }

    private void assertNoMoreMessages() {
        caster.assertNoMoreSaid();
    }

    // --- Test Cases ---

    @Test
    @DisplayName("Test Loop Body WITHOUT Delay - Should Finish Quickly") // 이름 수정
    void testLoopWithoutDelay() {
        // --- Arrange ---

        // --- loopBody 구성: setvariable, playeffect, sendmessage 모두 포함 ---
        List<Map<String, Object>> loopBody = new ArrayList<>(); // 변경 가능하도록 ArrayList 사용

        // 1. loopBody에 5개의 'setvariable' 액션 추가
        for (int i = 0; i < ACTIONS_PER_LOOP; i++) {
            loopBody.add(Map.of("setvariable", Map.of("name", "var_in_loop_" + i, "value", "{var:i}")));
        }

        // 2. loopBody에 10개의 'playeffect' 액션 추가
        int particleActionCount = 10;
        for (int p = 0; p < particleActionCount; p++) {
            // 파티클 이름 수정: VILLAGER_HAPPY -> HAPPY_VILLAGER (일반적인 이름) 또는 버전에 맞는 이름 사용
            loopBody.add(Map.of("targetbehaviour.playeffect", Map.of(
                    "location", "@CasterLocation",
                    "particle", "VILLAGER_HAPPY", // 유효한 파티클 이름으로 수정
                    "particleData", Map.of("count", 1)
            )));
        }

        // 3. loopBody에 10개의 'sendmessage' 액션 추가
        int messageActionCount = 10;
        for (int m = 0; m < messageActionCount; m++) {
            // 메시지 내용 변경: 루프 내에서 실행됨을 명시 (선택 사항)
            loopBody.add(Map.of("targetbehaviour.sendmessage", Map.of(
                    "message", "Loop Body Action Cycle " + (m+1) // 또는 그냥 동일 메시지 반복
            )));
        }
        // --- 이제 loopBody는 총 25개의 액션을 포함 ---


        // --- 메인 스크립트 구성 ---
        List<Map<String, Object>> testScript = List.of( // 외부 스크립트는 간단하게 유지
                Map.of("controlflow.forloop", Map.of(
                        "variable", "i",
                        "from", 1,
                        "to", LOOP_COUNT, // 10번 반복
                        "Do", loopBody    // 각 반복마다 25개 액션 실행
                )),
                // --- 루프 *이후* 완료 신호 ---
                Map.of("targetbehaviour.sendmessage", Map.of("message", "Entire Loop Finished"))
        );

        // Expected Ticks:
        // - Each loop iteration (25 actions, no delay) should complete in ~1 tick.
        // - Total loop time ≈ LOOP_COUNT (10) ticks + overhead.
        // - The final 'sendmessage' runs immediately after.
        // Total expected time should still be very low.
        int expectedMaxTicks = LOOP_COUNT + 15; // 충분한 버퍼

        // --- Act ---
        UUID taskId = scriptRunner.runScript(caster, testScript);
        assertNotNull(taskId, "Script task ID should not be null.");
        assertTrue(scriptRunner.isTaskRunning(taskId), "Task should be running initially.");

        System.out.println("[Test No Delay In Loop] Simulating " + expectedMaxTicks + " ticks...");
        server.getScheduler().performTicks(expectedMaxTicks);
        System.out.println("[Test No Delay In Loop] Simulation finished.");


        // --- Assert ---
        assertFalse(scriptRunner.isTaskRunning(taskId),
                "Task should have finished within " + expectedMaxTicks + " ticks (Loop Body No Delay).");

        // --- 수정: 루프 본문 메시지 100개 소비 ---
        int loopMessages = LOOP_COUNT * messageActionCount; // 10 * 10 = 100
        System.out.println("[Test No Delay In Loop] Consuming " + loopMessages + " loop body messages...");
        for (int i = 0; i < loopMessages; i++) {
            // 메시지 내용을 정확히 검증할 수도 있지만, 여기서는 소비만 합니다.
            // String consumedMessage = caster.nextMessage();
            // assertNotNull(consumedMessage, "Expected a loop body message, but queue was empty at index " + i);
            // assertTrue(consumedMessage.contains("Loop Body Action Cycle"), "Message content mismatch at index " + i);

            // 더 간단하게는 assertSaid를 사용하여 큐에서 하나씩 꺼내기만 합니다.
            // 메시지 내용이 약간 다를 수 있으므로 startsWith 등으로 유연하게 검증하거나,
            // 단순히 메시지를 꺼내기만 할 수도 있습니다. 여기서는 내용 검증을 포함합니다.
            int cycleNum = (i % messageActionCount) + 1; // 메시지 번호 계산 (1-10 반복)
            assertNextMessage("Loop Body Action Cycle " + cycleNum);
            if ((i + 1) % 10 == 0 || i == loopMessages - 1) { // 10개마다 또는 마지막에 로그 출력
                System.out.println("[Test No Delay In Loop] Consumed message " + (i + 1) + "/" + loopMessages);
            }
        }
        System.out.println("[Test No Delay In Loop] Finished consuming loop body messages.");
        // ----------------------------------------

        // --- 이제 최종 완료 메시지 확인 ---
        assertNextMessage("Entire Loop Finished");
        System.out.println("[Test No Delay In Loop] Asserted final message.");
        // ----------------------------------

        // 최종 메시지 확인 후 더 이상 메시지가 없는지 확인
        assertNoMoreMessages();
        System.out.println("[Test No Delay In Loop] Assertions passed.");
    }

    @Test
    @DisplayName("Test Loop WITH Delay - Should Finish Slower")
    void testLoopWithDelay() {
        // --- Arrange ---
        List<Map<String, Object>> loopBody = new ArrayList<>();
        for (int i = 0; i < ACTIONS_PER_LOOP; i++) {
            // Add 'fast' actions
            loopBody.add(Map.of("setvariable", Map.of("name", "var_with_delay_" + i, "value", "{var:i}")));
        }
        // Add the delay action at the end of the loop body
        loopBody.add(Map.of("controlflow.delay", Map.of("duration", DELAY_TICKS)));

        List<Map<String, Object>> testScript = List.of(
                Map.of("controlflow.forloop", Map.of(
                        "variable", "i",
                        "from", 1,
                        "to", LOOP_COUNT,
                        "Do", loopBody
                )),
                // Signal completion after the loop
                Map.of("targetbehaviour.sendmessage", Map.of("message", "Loop With Delay Finished"))
        );

        // Expected Ticks:
        // Each loop iteration:
        // - Runs ACTIONS_PER_LOOP setvariable actions (ideally same tick).
        // - Hits the 'delay' action, waits DELAY_TICKS.
        // - Loop control takes ~1 tick.
        // Total per iteration ≈ 1 (actions) + DELAY_TICKS.
        // Total expected time ≈ LOOP_COUNT * (1 + DELAY_TICKS).
        int expectedMinTicks = LOOP_COUNT * DELAY_TICKS; // Absolute minimum wait time
        int expectedCompletionTicks = LOOP_COUNT * (1 + DELAY_TICKS) + 5; // Expected + Buffer

        // --- Act ---
        UUID taskId = scriptRunner.runScript(caster, testScript);
        assertNotNull(taskId, "Script task ID should not be null.");
        assertTrue(scriptRunner.isTaskRunning(taskId), "Task should be running initially.");

        // 1. Simulate fewer ticks than required and assert it's STILL running
        int intermediateTicks = LOOP_COUNT; // Less than expectedMinTicks
        System.out.println("[Test With Delay] Simulating intermediate " + intermediateTicks + " ticks...");
        if (intermediateTicks > 0) {
            server.getScheduler().performTicks(intermediateTicks);
        }
        System.out.println("[Test With Delay] Intermediate simulation finished.");
        assertTrue(scriptRunner.isTaskRunning(taskId),
                "Task should STILL be running after " + intermediateTicks + " ticks (With Delay).");

        // 2. Simulate remaining ticks to reach completion
        int remainingTicks = expectedCompletionTicks - intermediateTicks;
        System.out.println("[Test With Delay] Simulating remaining " + remainingTicks + " ticks...");
        if (remainingTicks > 0) {
            server.getScheduler().performTicks(remainingTicks);
        }
        System.out.println("[Test With Delay] Final simulation finished.");

        // --- Assert ---
        assertFalse(scriptRunner.isTaskRunning(taskId),
                "Task should have finished within " + expectedCompletionTicks + " total ticks (With Delay).");
        assertNextMessage("Loop With Delay Finished"); // Final message should be sent now
        assertNoMoreMessages();
        System.out.println("[Test With Delay] Assertions passed.");
    }
}
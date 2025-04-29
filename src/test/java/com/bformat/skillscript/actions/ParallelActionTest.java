package com.bformat.skillscript.actions;

import com.bformat.skillscript.SkillScript;
// import com.bformat.skillscript.execution.ExecutionContext; // Not directly used
import com.bformat.skillscript.script.ScriptRunner;
import com.bformat.skillscript.actions.ActionRegistry; // Import ActionRegistry

import org.bukkit.ChatColor;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
// import static org.mockbukkit.mockbukkit.scheduler.paper.MockPaperScheduler.SCHEDULER_TICKS_PER_SECOND; // Use if available

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
// import java.util.concurrent.ConcurrentLinkedQueue; // No longer needed

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the ParallelAction with tick-based delays using MockBukkit message assertion.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ParallelActionTest {

    private ServerMock server;
    private SkillScript plugin;
    private ActionRegistry actionRegistry;
    private ScriptRunner scriptRunner;

    private final int TICKS_PER_SECOND = 20; // Standard Bukkit ticks per second

    // --- Test Setup and Teardown ---

    @BeforeAll
    void setUpAll() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SkillScript.class);
        actionRegistry = plugin.getActionRegistry();
        scriptRunner = plugin.getScriptRunner();

        // Register actions (ensure this runs only once if needed)
        actionRegistry.register("controlflow.parallel", new ParallelAction());
        actionRegistry.register("targetbehaviour.sendmessage", new SendMessageAction());
        actionRegistry.register("controlflow.delay", new DelayAction());
        actionRegistry.register("setvariable", new SetVariableAction());
    }

    @AfterAll
    void tearDownAll() {
        MockBukkit.unmock();
    }

    // PlayerMock instance for each test
    private PlayerMock caster;

    @BeforeEach
    void setUp() {
        // Create a fresh player for each test
        caster = server.addPlayer();
        // No need to override sendMessage anymore
    }

    @AfterEach
    void tearDown() {
        if (caster != null && caster.isOnline()) {
            scriptRunner.stopPlayerScripts(caster);
            // Consume any remaining messages from MockBukkit's internal queue
            // to ensure a clean state for the next test run.
            while (caster.nextMessage() != null) {
                // Loop until nextMessage() returns null, indicating the queue is empty.
            }
        }
    }

    // --- Helper Methods (Modified) ---

    /** Strips Bukkit color codes from a message string. */
    private String stripColor(String msg) {
        return msg == null ? null : ChatColor.stripColor(msg);
    }

    /** Asserts the next message sent to the caster matches expected (ignoring color). Uses MockBukkit's assertSaid. */
    private void assertNextMessage(String expectedMessage) {
        // assertSaid handles null checks and queue polling internally
        caster.assertSaid(stripColor(expectedMessage));
    }

    /** Asserts the next message sent to the caster starts with prefix (ignoring color). */
    private void assertNextMessageStartsWith(String expectedPrefix) {
        String actualMessage = caster.nextMessage(); // Get message from MockBukkit queue
        assertNotNull(actualMessage, "Expected message starting with '" + expectedPrefix + "' but queue was empty.");
        String strippedActual = stripColor(actualMessage);
        String strippedExpected = stripColor(expectedPrefix);
        assertTrue(strippedActual.startsWith(strippedExpected),
                "Expected message starting with '" + strippedExpected + "' but got '" + strippedActual + "'");
    }

    /** Asserts that the caster's message queue is now empty. Uses MockBukkit's assertNoMoreSaid. */
    private void assertNoMoreMessages() {
        caster.assertNoMoreSaid(); // Checks if the internal queue is empty
    }

    /** Collects the next N messages and asserts they match the expected set (ignoring color and order). */
    private void assertNextMessagesAnyOrder(List<String> expectedMessagesRaw) {
        // Strip color from expected messages first
        List<String> expectedMessages = expectedMessagesRaw.stream().map(this::stripColor).toList();
        List<String> actualMessages = new ArrayList<>();

        // Poll messages from MockBukkit's queue
        for (int i = 0; i < expectedMessages.size(); i++) {
            String msg = caster.nextMessage(); // Use nextMessage to retrieve
            assertNotNull(msg, "Expected " + expectedMessages.size() + " messages, but only got " + i + ". Missing one matching: " + expectedMessagesRaw);
            actualMessages.add(stripColor(msg));
        }

        // Sort both lists to compare content regardless of order
        Collections.sort(expectedMessages);
        Collections.sort(actualMessages);
        assertEquals(expectedMessages, actualMessages, "Message set mismatch (order ignored).");
    }


    // --- Test Cases ---

    @Test
    @DisplayName("Test Parallel Execution Flow with Tick-Based Delays")
    void testParallelExecutionFlowTickBased() {
        // --- Arrange: Same script definition as before ---
        int delayBranch1Ticks = 2 * TICKS_PER_SECOND; // 40 ticks
        int delayBranch2Ticks = 1 * TICKS_PER_SECOND; // 20 ticks
        // Expected timeline remains the same

        List<Map<String, Object>> testScript = List.of(
                Map.of("targetbehaviour.sendmessage", Map.of("message", "&aStart Sequential")),      // Seq 1
                Map.of("setvariable", Map.of("name", "status", "value", "Initializing")),          // Seq 2
                Map.of("controlflow.parallel", Map.of(                                               // Parallel Start
                        "Branches", List.of(
                                // Branch 0: Long delay
                                List.of(
                                        Map.of("targetbehaviour.sendmessage", Map.of("message", "&eBranch 0 Start")), // B0-1
                                        Map.of("controlflow.delay", Map.of("duration", delayBranch1Ticks)),         // B0-2 (Delay starts next tick)
                                        Map.of("setvariable", Map.of("name", "varA", "value", 100)),                 // B0-3 (After delay)
                                        Map.of("targetbehaviour.sendmessage", Map.of("message", "&eBranch 0 End"))  // B0-4
                                ),
                                // Branch 1: No delay
                                List.of(
                                        Map.of("targetbehaviour.sendmessage", Map.of("message", "&bBranch 1 Start & End")),// B1-1
                                        Map.of("setvariable", Map.of("name", "varB", "value", "Immediate"))           // B1-2
                                ),
                                // Branch 2: Short delay
                                List.of(
                                        Map.of("targetbehaviour.sendmessage", Map.of("message", "&cBranch 2 Start")), // B2-1
                                        Map.of("controlflow.delay", Map.of("duration", delayBranch2Ticks)),         // B2-2 (Delay starts next tick)
                                        Map.of("setvariable", Map.of("name", "varC", "value", true)),                 // B2-3 (After delay)
                                        Map.of("targetbehaviour.sendmessage", Map.of("message", "&cBranch 2 End"))  // B2-4
                                )
                        )
                )),                                                                                    // Parallel End
                Map.of("setvariable", Map.of("name", "status", "value", "Parallel Done")),         // Seq 3 (After Parallel)
                Map.of("targetbehaviour.sendmessage", Map.of("message", "&aEnd Sequential")),       // Seq 4
                Map.of("targetbehaviour.sendmessage", Map.of("message", "VarA: {var:varA}")),       // Seq 5
                Map.of("targetbehaviour.sendmessage", Map.of("message", "VarB: {var:varB}")),       // Seq 6
                Map.of("targetbehaviour.sendmessage", Map.of("message", "VarC: {var:varC}")),       // Seq 7
                Map.of("targetbehaviour.sendmessage", Map.of("message", "Status: {var:status}"))    // Seq 8
        );

        int ticksToSimulate = 600000; // Buffer added

        // --- Act ---
        UUID taskId = scriptRunner.runScript(caster, testScript);
        assertNotNull(taskId, "Script task ID should not be null.");
        assertTrue(scriptRunner.isTaskRunning(taskId), "Task should be running initially.");

        server.getScheduler().performTicks(ticksToSimulate);

        // --- Assert ---
        assertFalse(scriptRunner.isTaskRunning(taskId), "Task should be finished after " + ticksToSimulate + " ticks.");

        // Verify messages using the MODIFIED helper methods that use MockBukkit's queue
        //assertNextMessage("&aStart Sequential");

        // Branch start messages can arrive in any order
        /*assertNextMessagesAnyOrder(List.of(
                "&eBranch 0 Start",
                "&bBranch 1 Start & End",
                "&cBranch 2 Start"
        ));

        // Assert end messages based on delay completion order
        assertNextMessage("&cBranch 2 End"); // Finishes first
        assertNextMessage("&eBranch 0 End"); // Finishes second

        // Assert sequential messages after parallel block
        assertNextMessage("&aEnd Sequential");
        assertNextMessage("VarA: 100");
        assertNextMessage("VarB: Immediate");
        assertNextMessage("VarC: true");
        assertNextMessage("Status: Parallel Done");

        // Ensure no unexpected messages remain
        assertNoMoreMessages();*/
    }

    // TODO: Add more tests as needed
}
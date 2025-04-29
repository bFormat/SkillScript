package com.bformat.skillscript.lang; // Action 인터페이스와 같은 패키지 또는 하위

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionStatus;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class PlaceholderProcessingTest {

    private ServerMock server;
    private PlayerMock caster;
    private ExecutionContext context;
    private Logger logger; // 로깅 확인용 (선택적)

    // 테스트를 위해 Action 인터페이스를 구현하는 간단한 객체
    // default 메소드를 호출하기 위해 인스턴스가 필요합니다.
    private final Action testActionInstance = (ctx, state, params) -> {
        // 이 테스트에서는 Action의 실제 로직이 중요하지 않으므로,
        // 항상 완료 상태를 반환하도록 합니다.
        return ExecutionStatus.COMPLETED; // <--- 수정된 부분
    };// 람다로 간단히 구현

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        // 플러그인을 로드할 필요는 없습니다. ExecutionContext 만 있으면 됩니다.
        caster = server.addPlayer("TestCaster");
        World world = caster.getWorld();
        caster.setLocation(new Location(world, 10.5, 64.0, -20.2));
        caster.setHealth(18.5); // .5 추가하여 포맷팅 확인

        context = new ExecutionContext(caster);
        logger = Logger.getLogger("PlaceholderTestLogger"); // 테스트용 로거

        // 테스트용 변수 설정
        context.setVariable("playerName", "Steve");
        context.setVariable("score", 1250);
        context.setVariable("accuracy", 0.758);
        context.setVariable("targetPos", new Location(world, 50.1, 70.0, 100.9));
        context.setVariable("offsetVec", new Vector(1.2, -0.5, 3.0));
        context.setVariable("isEnabled", true);
        // null 값 변수 테스트
        context.setVariable("nullVar", null);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    // --- processPlaceholders(input, context, logger, prefix) 테스트 ---

    @Test
    @DisplayName("기본 변수 치환 테스트")
    void testVariableReplacement() {
        String input = "Player: {var:playerName}, Score: {var:score}, Enabled: {var:isEnabled}";
        String expected = "Player: Steve, Score: 1250, Enabled: true";
        String result = testActionInstance.processPlaceholders(input, context, logger, "[Test] ");
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("실수 및 정수 포맷팅 테스트")
    void testNumberFormatting() {
        // accuracy 는 .758 -> .76 으로, score는 1250 (정수) 로
        String input = "Accuracy: {var:accuracy}, Score: {var:score}";
        // String.format("%.2f", 0.758) -> 0.76
        String expected = "Accuracy: 0.76, Score: 1250";
        String result = testActionInstance.processPlaceholders(input, context, logger, "[Test] ");
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("기본 셀렉터 치환 및 포맷팅 테스트")
    void testSelectorReplacement() {
        // @Caster.Health 는 18.5 -> 18.50 으로, @CasterLocation.X 는 10.5 -> 10.50 으로
        String input = "Caster HP: {sel:@Caster.Health}, Pos X: {sel:@CasterLocation.X}";
        String expected = "Caster HP: 18.50, Pos X: 10.50"; // 기본 %.2f 포맷팅
        String result = testActionInstance.processPlaceholders(input, context, logger, "[Test] ");
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Location 및 Vector 변수/셀렉터 치환 테스트")
    void testLocationVectorReplacement() {
        String input = "Target at {var:targetPos}, Offset: {var:offsetVec}, Caster world: {sel:@CasterLocation.World}"; // .World 는 현재 미지원
        // Location 포맷: world, x, y, z (소수점 1자리)
        // Vector 포맷: x, y, z (소수점 2자리)
        String worldName = caster.getWorld().getName();
        String expected = String.format("Target at %s, 50.1, 70.0, 100.9, Offset: 1.20, -0.50, 3.00, Caster world: {sel:@CasterLocation.World}", worldName); // .World 는 처리 안됨
        String result = testActionInstance.processPlaceholders(input, context, logger, "[Test] ");
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("존재하지 않는 변수/셀렉터 처리 테스트")
    void testNonExistentPlaceholders() {
        String input = "Missing var: {var:nonExistentVar}, Missing sel: {sel:@Invalid.Selector}";
        // 기본적으로 플레이스홀더 유지
        String expected = "Missing var: {var:nonExistentVar}, Missing sel: {sel:@Invalid.Selector}";
        String result = testActionInstance.processPlaceholders(input, context, logger, "[Test] ");
        assertEquals(expected, result);
        // TODO: 로거 출력을 확인하여 경고 메시지가 제대로 로깅되었는지 검증할 수 있음 (더 고급 테스트)
    }

    @Test
    @DisplayName("Null 값 변수 처리 테스트")
    void testNullVariable() {
        String input = "Null value: {var:nullVar}";
        // null 값 변수는 기본적으로 플레이스홀더 유지 + 경고 로깅
        String expected = "Null value: {var:nullVar}";
        String result = testActionInstance.processPlaceholders(input, context, logger, "[Test] ");
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("플레이스홀더가 없는 문자열 테스트")
    void testStringWithoutPlaceholders() {
        String input = "This is a normal string without placeholders.";
        String expected = "This is a normal string without placeholders.";
        String result = testActionInstance.processPlaceholders(input, context, logger, "[Test] ");
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("비어있는 문자열 및 Null 입력 테스트")
    void testEmptyAndNullInput() {
        assertEquals("", testActionInstance.processPlaceholders("", context, logger, "[Test] "), "비어있는 문자열");
        assertNull(testActionInstance.processPlaceholders(null, context, logger, "[Test] "), "Null 입력");
    }

    @Test
    @DisplayName("중첩되지 않은 여러 플레이스홀더 테스트")
    void testMultiplePlaceholders() {
        String input = "{var:playerName} - {var:score} points. Location: {sel:@CasterLocation.Y}";
        String expected = "Steve - 1250 points. Location: 64"; // Y좌표는 정수
        String result = testActionInstance.processPlaceholders(input, context, logger, "[Test] ");
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("플레이스홀더 내부에 특수문자가 있는 식별자 (현재 미지원 시나리오)")
    void testIdentifiersWithSpecialChars() {
        // 예: {var:my-variable} 또는 {sel:@Target[type=Player].Name} 등은 현재 패턴/로직 미지원
        context.setVariable("my-variable", "hyphenated");
        String input = "Test: {var:my-variable}";
        // 현재 패턴 \{(var|sel):([^\s}]+)\} 은 '-' 를 허용하지 않을 수 있음 -> 확인 필요
        // 현재 패턴은 [^\s}]+ 이므로 하이픈(-) 포함 가능!
        String expected = "Test: hyphenated"; // 하이픈 허용 시
        String result = testActionInstance.processPlaceholders(input, context, logger, "[Test] ");
        assertEquals(expected, result);

        // 복잡한 셀렉터는 현재 resolveNumericValue 등에서 지원해야 함
        String inputComplex = "Complex: {sel:@Target[type=Player].Name}";
        String expectedComplex = "Complex: {sel:@Target[type=Player].Name}"; // 미지원 시 유지
        String resultComplex = testActionInstance.processPlaceholders(inputComplex, context, logger, "[Test] ");
        assertEquals(expectedComplex, resultComplex);
    }

    // --- processPlaceholders(input, context) 오버로드 테스트 ---
    @Test
    @DisplayName("로거 없는 오버로드 메소드 테스트")
    void testOverloadWithoutLogger() {
        String input = "Player: {var:playerName}";
        String expected = "Player: Steve";
        // 로거 없이 호출
        String result = testActionInstance.processPlaceholders(input, context);
        assertEquals(expected, result);
    }
}
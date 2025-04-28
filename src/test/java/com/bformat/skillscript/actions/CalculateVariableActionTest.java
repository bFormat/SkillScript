package com.bformat.skillscript.actions;

import com.bformat.skillscript.SkillScript; // 메인 플러그인 클래스
import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class CalculateVariableActionTest {

    private ServerMock server;
    private SkillScript plugin;
    private PlayerMock caster;
    private ExecutionContext context;
    private ExecutionState state;
    private CalculateVariableAction action; // 테스트 대상 액션

    @BeforeEach
    public void setUp() {
        // MockBukkit 서버 및 플러그인 초기화
        server = MockBukkit.mock();
        // MockBukkit은 실제 플러그인 jar 없이 클래스만으로 로드 가능
        plugin = MockBukkit.load(SkillScript.class);

        // 테스트용 플레이어 생성 및 설정
        caster = server.addPlayer("TestCaster");
        World world = caster.getWorld(); // MockBukkit이 기본 월드 제공
        caster.setLocation(new Location(world, 10.0, 64.0, 20.0)); // 시나리오 2 용 위치
        caster.setHealth(18.0); // 시나리오 2 용 체력

        // 실행 컨텍스트 및 상태 생성
        context = new ExecutionContext(caster);
        // CalculateVariableAction은 ExecutionState를 크게 수정하지 않으므로 빈 리스트로 초기화
        state = new ExecutionState(Collections.emptyList());

        // 테스트 대상 액션 인스턴스 생성
        action = new CalculateVariableAction();
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("테스트 1: 기본 변수 및 간단한 산술 연산")
    void testSimpleArithmeticWithVariable() {
        // Arrange: 초기 변수 설정 (SetVariableAction을 모방)
        context.setVariable("initialvalue", 15.0); // 변수명은 소문자로 저장됨
        Map<String, Object> params = Map.of(
                "variable", "calcResult1",
                "expression", "initialValue * 3 - 5" // 표현식 내 변수명은 대소문자 구분 안 함(mXparser 기본)
        );
        double expectedResult = 40.0;

        // Act: 액션 실행
        action.execute(context, state, params);

        // Assert: 결과 변수 확인
        Object result = context.getVariable("calcresult1"); // 결과 변수명도 소문자로 조회
        assertNotNull(result, "결과 변수가 설정되지 않았습니다.");
        assertTrue(result instanceof Double, "결과 변수 타입이 Double이 아닙니다.");
        assertEquals(expectedResult, (Double) result, 0.0001, "계산 결과가 예상과 다릅니다.");
    }

    @Test
    @DisplayName("테스트 2: 셀렉터, 함수, 상수 사용")
    void testSelectorsFunctionsAndConstants() {
        // Arrange: (플레이어 위치/체력은 @BeforeEach에서 설정됨)
        Map<String, Object> params = Map.of(
                "variable", "calcResult2",
                // round 함수에 두 번째 인수로 0 추가!
                "expression", "round(sin(@CasterLocation.X * pi / 180) * 100, 0) + @Caster.Health"
        );
        // 예상 결과: Math.round(Math.sin(10.0 * Math.PI / 180.0) * 100.0) + 18.0
        // = Math.round(Math.sin(0.1745329...) * 100.0) + 18.0
        // = Math.round(0.173648... * 100.0) + 18.0
        // = Math.round(17.3648...) + 18.0 = 17.0 + 18.0 = 35.0
        double expectedResult = 35.0;

        // Act: 액션 실행
        action.execute(context, state, params);

        // Assert: 결과 변수 확인
        Object result = context.getVariable("calcresult2");
        assertNotNull(result);
        assertTrue(result instanceof Double);
        assertEquals(expectedResult, (Double) result, 0.0001);
    }

    @Test
    @DisplayName("테스트 3: 다른 변수 참조 및 함수 사용")
    void testVariableReferenceAndFunction() {
        // Arrange: 이전 단계의 변수 설정
        context.setVariable("initialvalue", 15.0);
        context.setVariable("calcresult1", 40.0); // 테스트 1의 결과
        Map<String, Object> params = Map.of(
                "variable", "calcResult3",
                "expression", "sqrt(calcResult1) + initialValue / 2"
        );
        // 예상 결과: Math.sqrt(40.0) + 15.0 / 2.0
        // = 6.324555... + 7.5 = 13.824555...
        double expectedResult = Math.sqrt(40.0) + 15.0 / 2.0;

        // Act: 액션 실행
        action.execute(context, state, params);

        // Assert: 결과 변수 확인
        Object result = context.getVariable("calcresult3");
        assertNotNull(result);
        assertTrue(result instanceof Double);
        assertEquals(expectedResult, (Double) result, 0.0001);
    }

    @Test
    @DisplayName("테스트 4: 구문 오류(Syntax Error)가 있는 표현식")
    void testExpressionWithSyntaxError() {
        // Arrange:
        Map<String, Object> params = Map.of(
                "variable", "calcError1",
                "expression", "10 + / 5" // 잘못된 구문
        );

        // Act: 액션 실행 (오류가 로깅되지만, 예외는 던지지 않아야 함)
        assertDoesNotThrow(() -> action.execute(context, state, params));

        // Assert: 결과 변수가 설정되지 않았는지 확인 (구문 오류 시 변수 설정을 안 함)
        assertNull(context.getVariable("calcerror1"), "구문 오류 발생 시 변수가 설정되지 않아야 합니다.");
        // 추가적으로 로그 메시지를 확인하는 방법도 있지만, 테스트 복잡도가 증가함
        // 예: MockBukkit 로거 사용 또는 custom LogHandler 사용
    }

    @Test
    @DisplayName("테스트 5: 계산 결과가 NaN인 표현식")
    void testExpressionResultingInNaN() {
        // Arrange:
        Map<String, Object> params = Map.of(
                "variable", "calcError2",
                "expression", "log(10, -1)" // log 밑이 10, 진수가 음수 -> NaN
        );

        // Act: 액션 실행
        assertDoesNotThrow(() -> action.execute(context, state, params));

        // Assert: NaN 결과 처리 확인
        // assertNull(context.getVariable("calcerror2"), "NaN 결과 발생 시 변수가 설정되지 않아야 합니다 (현재 구현 기준).");

        // 만약 NaN 값을 저장하도록 로직을 변경한다면 아래 Assert 사용
        Object result = context.getVariable("calcerror2");
        assertNotNull(result, "NaN 결과 발생 시 변수가 설정되어야 합니다.");
        assertTrue(result instanceof Double, "NaN 결과 변수 타입은 Double이어야 합니다.");
        assertTrue(Double.isNaN((Double) result), "계산 결과가 NaN이어야 합니다.");
    }

    @Test
    @DisplayName("테스트 6: 존재하지 않는 변수 사용")
    void testUsingNonExistentVariable() {
        // Arrange: someValue 변수는 설정하지 않음
        Map<String, Object> params = Map.of(
                "variable", "calcError3",
                "expression", "someValue * 5"
        );

        // Act: 액션 실행 (mXparser는 존재하지 않는 인수를 0 또는 NaN으로 처리하거나 에러 발생시킬 수 있음)
        assertDoesNotThrow(() -> action.execute(context, state, params));

        // Assert: mXparser가 어떻게 처리하는지에 따라 달라짐.
        // 가능성 1: Syntax Error로 처리될 경우 (가장 가능성 높음)
        assertNull(context.getVariable("calcerror3"), "존재하지 않는 변수 사용 시 구문 오류로 처리되어 변수가 설정되지 않아야 합니다.");
        // 가능성 2: NaN으로 계산될 경우 (덜 일반적)
        // assertTrue(Double.isNaN((Double) context.getVariable("calcerror3")));
        // 가능성 3: 0으로 간주하고 계산될 경우 (매우 드묾)
        // assertEquals(0.0, context.getVariable("calcerror3"));
    }
}
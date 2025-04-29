package com.bformat.skillscript.actions;

import com.bformat.skillscript.execution.ExecutionContext;
import com.bformat.skillscript.execution.ExecutionState;
import com.bformat.skillscript.lang.Action;
import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;
import org.mariuszgromada.math.mxparser.mXparser;
import com.bformat.skillscript.execution.ExecutionStatus;

import java.util.*; // Import Collections, List, Map, ArrayList, HashMap, Comparator
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors; // Import Collectors

public class CalculateVariableAction implements Action {

    // SkillScript 변수/셀렉터를 찾는 정규식 (이전과 동일)
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("(@[a-zA-Z_]\\w*(?:\\.\\w+)*)|([a-zA-Z_]\\w*(?:\\.\\w+)*)");
    // mXparser 인수로 변환 시 사용할 접두사 (충돌 방지)
    private static final String MXPARSER_ARG_PREFIX = "ss_";

    @Override
    public ExecutionStatus execute(ExecutionContext context, ExecutionState state, Map<String, Object> params) {
        final Logger logger = context.getCaster().getServer().getLogger();
        final String pluginPrefix = "[SkillScript Action] ";

        // --- 필수 파라미터 파싱 ---
        Optional<String> variableNameOpt = getStringParameter(params, "variable");
        Optional<String> expressionStringOpt = getStringParameter(params, "expression");

        if (variableNameOpt.isEmpty()) {
            logger.warning(pluginPrefix + "CalculateVariableAction: Missing 'variable' parameter.");
            return ExecutionStatus.ERROR("CalculateVariableAction: Missing 'variable' parameter.");
        }
        if (expressionStringOpt.isEmpty()) {
            logger.warning(pluginPrefix + "CalculateVariableAction: Missing 'expression' parameter for variable '" + variableNameOpt.get() + "'.");
            return ExecutionStatus.ERROR("CalculateVariableAction: Missing 'expression' parameter for variable '" + variableNameOpt.get() + "'.");
        }

        String variableName = variableNameOpt.get();
        String originalExpression = expressionStringOpt.get();
        logger.fine(pluginPrefix + "CalculateVariableAction: Evaluating expression for variable '" + variableName + "': " + originalExpression);

        // mXparser 설정 (선택적)
        mXparser.disableImpliedMultiplicationMode();
        mXparser.disableAlmostIntRounding();
        mXparser.disableCanonicalRounding();

        // --- 표현식 내 식별자 처리 및 Argument 생성 ---
        List<Argument> arguments = new ArrayList<>();
        // 원본 식별자 -> mXparser 안전 이름 매핑 (문자열 치환용)
        Map<String, String> replacements = new HashMap<>();
        Matcher matcher = IDENTIFIER_PATTERN.matcher(originalExpression);

        try {
            while (matcher.find()) {
                String identifier = matcher.group(0); // 원본 식별자

                logger.info("Found identifier: " + identifier); // <--- 디버깅 로그 추가

                if (replacements.containsKey(identifier)) {
                    logger.info("Identifier already processed: " + identifier); // <--- 디버깅 로그 추가
                    continue;
                }

                Optional<Double> valueOpt = context.resolveNumericValue(identifier);
                logger.info("Resolved value for '" + identifier + "': " + (valueOpt.isPresent() ? valueOpt.get() : "EMPTY")); // <--- 디버깅 로그 추가

                if (valueOpt.isPresent()) {
                    // 1. mXparser 안전 이름 생성
                    String safeMxParserName = MXPARSER_ARG_PREFIX + identifier.replaceAll("[^a-zA-Z0-9_]", "_");
                    if (!safeMxParserName.isEmpty() && Character.isDigit(safeMxParserName.charAt(0))) {
                        safeMxParserName = "_" + safeMxParserName;
                    }
                    if (safeMxParserName.equals(MXPARSER_ARG_PREFIX) || safeMxParserName.isEmpty()) {
                        logger.warning(pluginPrefix + "CalculateVariableAction: Could not generate a valid mXparser argument name for identifier: " + identifier);
                        continue;
                    }

                    // 2. 람다 문제 해결용 final 변수
                    final String finalSafeMxParserName = safeMxParserName;

                    // 3. Argument 추가 (중복 방지)
                    boolean exists = arguments.stream().anyMatch(arg -> arg.getArgumentName().equals(finalSafeMxParserName));
                    if (!exists) {
                        double value = valueOpt.get();
                        Argument arg = new Argument(finalSafeMxParserName + " = " + value);
                        arguments.add(arg);
                        logger.finer(pluginPrefix + "CalculateVariableAction: Added argument: " + arg.getArgumentName() + " = " + value + " (from: " + identifier + ")");
                        // 4. 문자열 치환 매핑 저장
                        logger.info("Adding to replacements: " + identifier + " -> " + finalSafeMxParserName); // <--- 디버깅 로그 추가
                        replacements.put(identifier, finalSafeMxParserName);
                    } else {
                        logger.finer(pluginPrefix + "CalculateVariableAction: Argument already exists: " + finalSafeMxParserName);
                        // 이미 존재해도 매핑은 저장해야 함 (표현식에 여러 번 나올 수 있으므로)
                        if (!replacements.containsKey(identifier)) {
                            logger.info("Adding existing to replacements: " + identifier + " -> " + finalSafeMxParserName); // <--- 디버깅 로그 추가
                            replacements.put(identifier, finalSafeMxParserName);
                        }
                    }

                } else {
                    logger.info("Could not resolve value for: " + identifier); // <--- 디버깅 로그 추가
                    logger.finer(pluginPrefix + "CalculateVariableAction: Could not resolve numeric value for identifier '" + identifier + "'. Assuming it's a function/constant or will cause error.");
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, pluginPrefix + "CalculateVariableAction: Error during identifier processing for expression: " + originalExpression, e);
            return ExecutionStatus.ERROR("CalculateVariableAction: Error during identifier processing for expression: " + originalExpression);
        }

        // --- 표현식 문자열 치환 (가장 긴 식별자부터) ---
        // ** 이 로직이 다시 필요합니다! **
        String processedExpression = originalExpression;
        // replacements 맵의 키(원본 식별자)를 길이 내림차순으로 정렬
        List<String> sortedIdentifiers = replacements.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .collect(Collectors.toList());

        for (String originalId : sortedIdentifiers) {
            String safeName = replacements.get(originalId);
            // 정규식 대신 간단한 문자열 치환 사용 (더 안전할 수 있음)
            // 주의: 이 방식도 완벽하지는 않음. 예를 들어 'var'가 'myvar' 안에 포함될 때 문제될 수 있음.
            // 하지만 정규식 \b 보다 나을 수 있음.
            // processedExpression = processedExpression.replace(originalId, safeName);

            // 정규식을 사용하되, 단어 경계(\b) 대신 lookaround 사용 시도 (더 복잡)
            // 예: (?<![\w@.])pattern(?![\w@.]) -> 패턴 앞뒤에 단어 문자,@,. 이 없는 경우 매칭
            // 구현 복잡도가 높아지므로, 일단 replaceAll 방식 유지 (위험성 인지)
            // 더 안전한 방법: 토크나이저를 사용해서 파싱 후 재구성

            // 가장 안전한 방법 중 하나: 식별자만 정확히 찾아 바꾸기 (정규식 활용 개선)
            // `originalId` 가 정규식 특수문자를 포함할 수 있으므로 Pattern.quote 사용은 필수
            // Pattern.quote 된 패턴 앞뒤로 단어 문자가 아닌 것(^|\W)과 ($|\W)을 확인
            // (하지만 이 방법도 완벽하지 않음. 연산자 등 고려)
            try {
                // 정규식 특수문자 이스케이프
                String quotedOriginalId = Pattern.quote(originalId);
                // 단어 경계를 좀 더 명확하게 정의 (앞/뒤가 단어 문자가 아니거나, 문자열 시작/끝)
                String regex = "(?<![a-zA-Z0-9_])" + quotedOriginalId + "(?![a-zA-Z0-9_])";
                processedExpression = processedExpression.replaceAll(regex, safeName);

            } catch (Exception e) {
                logger.log(Level.WARNING, pluginPrefix + "CalculateVariableAction: Error during regex replacement for identifier '" + originalId + "'. Falling back to simple replace.", e);
                processedExpression = processedExpression.replace(originalId, safeName); // fallback
            }
        }
        logger.fine(pluginPrefix + "CalculateVariableAction: Processed expression: " + processedExpression);


        // --- mXparser Expression 생성 및 계산 ---
        // Expression 생성 시 **치환된** 표현식 문자열과 Argument 리스트 전달
        logger.info("Final processed expression before passing to mXparser: " + processedExpression); // <--- 로그 추가
        Expression expression = new Expression(processedExpression, arguments.toArray(new Argument[0]));

        // 구문 검사
        if (!expression.checkSyntax()) {
            String errorMessage = expression.getErrorMessage();
            // 오류 메시지에 처리된 표현식을 보여주는 것이 디버깅에 더 유용
            logger.warning(pluginPrefix + "CalculateVariableAction: Syntax error in expression for variable '" + variableName + "': " + errorMessage + " (Processed Expression: " + processedExpression + ")");
            return ExecutionStatus.ERROR("CalculateVariableAction: Syntax error in expression for variable '" + variableName + "': " + errorMessage + " (Processed Expression: " + processedExpression + ")");
        }

        // 계산 실행
        double result = expression.calculate();

        // --- 결과 확인 및 저장 ---
        if (Double.isNaN(result)) {
            // (NaN 처리 로직은 이전과 동일)
            logger.warning(pluginPrefix + "CalculateVariableAction: Calculation result is NaN for variable '" + variableName + "'. (Processed Expression: " + processedExpression + ")");
            String errorMessage = expression.getErrorMessage();
            if(errorMessage != null && !errorMessage.trim().isEmpty() && !errorMessage.contains("OK")) {
                logger.warning(pluginPrefix + "Calculation Error Message: " + errorMessage);
            }
            context.setVariable(variableName, Double.NaN);
            // return; // 필요 시 NaN 저장 안함
        } else {
            logger.fine(pluginPrefix + "CalculateVariableAction: Calculated result for '" + variableName + "' is: " + result);
            context.setVariable(variableName, result); // 계산된 결과(double)를 변수에 저장
        }

        return ExecutionStatus.COMPLETED;
    }
}
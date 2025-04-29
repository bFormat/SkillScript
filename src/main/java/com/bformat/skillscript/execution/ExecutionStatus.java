package com.bformat.skillscript.execution; // execution 패키지가 적합

import java.util.Objects;

public sealed interface ExecutionStatus {
    /** Action completed normally in this tick. */
    record Completed() implements ExecutionStatus {}

    /** Action requested a delay. Execution for this branch/task will pause. */
    record Delay(long ticks) implements ExecutionStatus {
        public Delay {
            if (ticks <= 0) {
                throw new IllegalArgumentException("Delay ticks must be positive.");
            }
        }
    }

    /** Action encountered an error and cannot continue. */
    record Error(String message) implements ExecutionStatus {
        public Error {
            Objects.requireNonNull(message, "Error message cannot be null");
        }
    }

    // Convenience static instances/factories
    ExecutionStatus COMPLETED = new Completed();
    static ExecutionStatus DELAY(long ticks) {
        // 0틱 이하면 즉시 완료로 처리 (DelayAction 에서도 이 로직 사용 가능)
        return (ticks > 0) ? new Delay(ticks) : COMPLETED;
    }
    static ExecutionStatus ERROR(String message) { return new Error(message); }
}
package com.imd.cobolexplorer;

import java.util.Objects;

public record Diagnostic(String frontend, Phase phase, Code code, String file, int line, int column,
                         String message, String offendingToken, String exceptionClass) {
    public enum Phase { PREPROCESSOR, LEXER, PARSER, IO, OTHER }

    public enum Code {
        GENERAL, UNRESOLVED_COPY, NOMINAL_COPYBOOK;
        public boolean incompleteCopy() { return this == UNRESOLVED_COPY || this == NOMINAL_COPYBOOK; }
    }

    public static boolean incompleteCopyCode(String code) {
        return Code.UNRESOLVED_COPY.name().equals(code) || Code.NOMINAL_COPYBOOK.name().equals(code);
    }

    public Diagnostic {
        code = Objects.requireNonNull(code, "code");
        if (code.incompleteCopy() && phase != Phase.PREPROCESSOR)
            throw new IllegalArgumentException(
                    "incomplete COPY diagnostics must belong to the preprocessor");
    }

    public Diagnostic(String frontend, Phase phase, String file, int line, int column,
                      String message, String offendingToken, String exceptionClass) {
        this(frontend, phase, Code.GENERAL, file, line, column,
                message, offendingToken, exceptionClass);
    }
}

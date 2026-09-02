package com.cleanroommc.modularui.api.markup;

/** Parse or validation failure while loading declarative UI markup. */
public final class MuiMarkupException extends RuntimeException {

    private final int line;
    private final int column;

    public MuiMarkupException(String message) {
        this(message, -1, -1, null);
    }

    public MuiMarkupException(String message, Throwable cause) {
        this(message, -1, -1, cause);
    }

    public MuiMarkupException(String message, int line, int column, Throwable cause) {
        super(format(message, line, column), cause);
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return this.line;
    }

    public int getColumn() {
        return this.column;
    }

    private static String format(String message, int line, int column) {
        if (line < 0 || column < 0) return message;
        return message + " (line " + line + ", column " + column + ")";
    }
}

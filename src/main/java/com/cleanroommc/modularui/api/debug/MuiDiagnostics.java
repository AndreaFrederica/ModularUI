package com.cleanroommc.modularui.api.debug;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Small client-local diagnostic buffer for the optional MUI inspector log pane. */
public final class MuiDiagnostics {
    private static final int MAX_ENTRIES = 512;
    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();

    private MuiDiagnostics() {}

    public static void info(String source, String message) { append(Level.INFO, source, message, null); }
    public static void warn(String source, String message, @Nullable Throwable error) { append(Level.WARN, source, message, error); }
    public static void error(String source, String message, @Nullable Throwable error) { append(Level.ERROR, source, message, error); }

    public static synchronized List<Entry> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(ENTRIES));
    }

    public static synchronized void clear() { ENTRIES.clear(); }

    private static synchronized void append(Level level, String source, String message, @Nullable Throwable error) {
        while (ENTRIES.size() >= MAX_ENTRIES) ENTRIES.removeFirst();
        ENTRIES.addLast(new Entry(System.currentTimeMillis(), level, source, message,
                error == null ? null : error.getClass().getName(), error == null ? null : error.getMessage()));
    }

    public enum Level { INFO, WARN, ERROR }

    public static final class Entry {
        private final long timestamp;
        private final Level level;
        private final String source;
        private final String message;
        @Nullable private final String errorType;
        @Nullable private final String errorMessage;

        private Entry(long timestamp, Level level, String source, String message,
                      @Nullable String errorType, @Nullable String errorMessage) {
            this.timestamp = timestamp;
            this.level = level;
            this.source = source;
            this.message = message;
            this.errorType = errorType;
            this.errorMessage = errorMessage;
        }

        public long getTimestamp() { return timestamp; }
        public Level getLevel() { return level; }
        public String getSource() { return source; }
        public String getMessage() { return message; }
        public @Nullable String getErrorType() { return errorType; }
        public @Nullable String getErrorMessage() { return errorMessage; }
    }
}

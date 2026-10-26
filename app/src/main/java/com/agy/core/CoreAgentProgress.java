package com.agy.core;

/**
 * Public notification state derived only from Core protocol enums and step counts.
 * No prompts, command lines, paths, tool arguments or file contents enter here.
 * One instance per conversation survives subscription reconnects.
 */
public final class CoreAgentProgress {
    public static final String GENERIC = "Агент выполняет задачу";
    public static final int COMPLETED = 0, ERROR = 1, CANCELLED = 2;

    public static final class Update {
        public final String trajectory;
        public final int status, totalSteps, stepType;
        public final Boolean fullyIdle;
        public final boolean error, killed;
        public Update(String trajectory, int status, Boolean idle, int totalSteps,
                      int stepType, boolean error, boolean killed) {
            this.trajectory = trajectory == null ? "" : trajectory;
            this.status = status; this.fullyIdle = idle; this.totalSteps = totalSteps;
            this.stepType = stepType; this.error = error; this.killed = killed;
        }
    }
    public static final class Snapshot {
        public final int status, outcome;
        public final boolean fullyIdle;
        public final String stage;
        Snapshot(int status, boolean idle, String stage, int outcome) {
            this.status = status; this.fullyIdle = idle;
            this.stage = stage; this.outcome = outcome;
        }
    }

    private String trajectory = "";
    private final java.util.Set<String> retired = new java.util.LinkedHashSet<>();
    private int status, highWater = -1, lastType;
    private boolean idle, terminal, error, cancelling, killed;
    private String stage = GENERIC;

    public synchronized Snapshot accept(Update u) {
        if (retired.contains(u.trajectory)) return null;
        if (!u.trajectory.isEmpty() && !u.trajectory.equals(trajectory)) {
            if (!trajectory.isEmpty()) retired.add(trajectory);
            if (retired.size() > 16) retired.remove(retired.iterator().next());
            trajectory = u.trajectory;
            highWater = -1; lastType = 0; status = 0; idle = false;
            stage = GENERIC; error = false; killed = false;
            terminal = false; cancelling = false;
        }
        if (u.totalSteps >= 0 && u.totalSteps < highWater) return null;
        // Same/older rehydration cannot resurrect a finished run. A new user
        // turn appends steps; until then retain the terminal watermark.
        if (terminal && (u.status == 2 || u.status == 3 || u.status == 4)
                && u.totalSteps <= highWater) return null;
        boolean advanced = u.totalSteps > highWater;
        if (advanced) {
            if (terminal) { status = 0; idle = false; }
            highWater = u.totalSteps; lastType = 0;
            stage = GENERIC; error = false; killed = false;
            terminal = false; cancelling = false;
        }
        // Equal-index conflicts are ambiguous: retain the first confirmed type.
        // Status-only deltas retain the last confirmed stage.
        if (u.stepType > 0 && (advanced || lastType == 0)) {
            lastType = u.stepType;
            stage = label(u.stepType);
        }
        if (u.status >= 1 && u.status <= 4) status = u.status;
        if (u.fullyIdle != null) idle = u.fullyIdle;
        error |= u.error;
        killed |= u.killed;
        cancelling |= status == 3;
        if (status == 1 && idle) terminal = true;
        int outcome = killed || cancelling ? CANCELLED : error ? ERROR : COMPLETED;
        return new Snapshot(status, idle, status == 3 ? "Отменяет задачу" : stage, outcome);
    }

    public static int status(Object raw) {
        if (raw instanceof Number) {
            int n = ((Number) raw).intValue();
            return n >= 1 && n <= 4 ? n : 0;
        }
        if (!(raw instanceof String)) return 0;
        switch ((String) raw) {
            case "1": case "CASCADE_RUN_STATUS_IDLE": return 1;
            case "2": case "CASCADE_RUN_STATUS_RUNNING": return 2;
            case "3": case "CASCADE_RUN_STATUS_CANCELING": return 3;
            case "4": case "CASCADE_RUN_STATUS_BUSY": return 4;
            default: return 0;
        }
    }

    /** Exact enum names from the bundled protobuf descriptor, never free text. */
    public static int stepType(Object raw) {
        if (raw instanceof Number) return Math.max(0, ((Number)raw).intValue());
        if (!(raw instanceof String)) return 0;
        String v = (String) raw;
        if (v.matches("[0-9]{1,4}")) return Integer.parseInt(v);
        switch (v) {
            case "CORTEX_STEP_TYPE_VIEW_FILE": return 8;
            case "CORTEX_STEP_TYPE_VIEW_CODE_ITEM": return 13;
            case "CORTEX_STEP_TYPE_VIEW_FILE_OUTLINE": return 47;
            case "CORTEX_STEP_TYPE_LIST_DIRECTORY": return 9;
            case "CORTEX_STEP_TYPE_GREP_SEARCH": return 7;
            case "CORTEX_STEP_TYPE_CODE_SEARCH": return 73;
            case "CORTEX_STEP_TYPE_FIND": return 25;
            case "CORTEX_STEP_TYPE_CODE_ACTION": return 5;
            case "CORTEX_STEP_TYPE_FILE_CHANGE": return 86;
            case "CORTEX_STEP_TYPE_EDIT_NOTEBOOK": return 126;
            case "CORTEX_STEP_TYPE_RUN_COMMAND": return 21;
            case "CORTEX_STEP_TYPE_SHELL_EXEC": return 112;
            case "CORTEX_STEP_TYPE_COMMAND_STATUS": return 28;
            case "CORTEX_STEP_TYPE_READ_TERMINAL": return 65;
            case "CORTEX_STEP_TYPE_BLAZE_TEST_TARGETS": return 107;
            case "CORTEX_STEP_TYPE_PLANNER_RESPONSE": return 15;
            case "CORTEX_STEP_TYPE_CRITIQUE": return 118;
            default: return 0;
        }
    }

    /** Generic Core steps expose a typed tool name, never a public description. */
    public static int toolType(String name) {
        if (name == null) return 0;
        switch (name) {
            case "view_file": return 8;
            case "view_code_item": return 13;
            case "view_file_outline": return 47;
            case "list_dir": return 9;
            case "grep_search": return 7;
            case "find_by_name": return 25;
            case "code_search": return 73;
            case "replace_file_content": case "multi_replace_file_content":
            case "write_to_file": return 5;
            case "run_command": return 21;
            default: return 0;
        }
    }

    public static String label(int type) {
        switch (type) {
            case 8: case 13: case 47: return "Читает исходники";
            case 9: return "Просматривает каталог";
            case 7: case 73: case 25: return "Ищет в исходниках";
            case 5: case 86: case 126: return "Изменяет файлы";
            // A run_command event does NOT prove it is running tests.
            case 21: case 112: return "Выполняет команду";
            case 28: case 65: return "Читает вывод команды";
            case 107: return "Запускает тесты";
            case 15: return "Готовит ответ";
            case 118: return "Анализирует результаты";
            default: return GENERIC;
        }
    }

    /** Notification strings are allowlisted, not merely truncated/sanitized. */
    public static String publicStage(String value) {
        if ("Отменяет задачу".equals(value)) return value;
        for (int type : new int[]{8,9,7,5,21,28,107,15,118})
            if (label(type).equals(value)) return value;
        return GENERIC;
    }
}

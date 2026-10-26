package com.agy.core;

import java.io.File;
import java.util.Map;

/** PTY environment policy; Android-free for filesystem regression tests. */
public final class CoreShellEnvironment {
    private CoreShellEnvironment() {}

    public static void apply(Map<String, String> env, File prefix, File bin, File systemSh) {
        File[] candidates = {new File(prefix, "bin/bash"), new File(bin, "bash"), systemSh};
        File selected = null;
        for (File candidate : candidates) {
            if (candidate.isFile() && candidate.canExecute()) {
                selected = candidate;
                break;
            }
        }
        if (selected == null) throw new IllegalStateException("No executable PTY shell available");
        env.put("SHELL", selected.getAbsolutePath());
        env.put("TERM", "xterm-256color");
        // Never retain an inherited path belonging to another application.
        env.remove("TERMINFO");
        File terminfo = new File(prefix, "share/terminfo");
        if (terminfo.isDirectory() && terminfo.canRead()) {
            env.put("TERMINFO", terminfo.getAbsolutePath());
        }
    }
}

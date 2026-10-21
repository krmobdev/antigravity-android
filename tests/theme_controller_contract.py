#!/usr/bin/env python3
"""Compile the real ThemeController with tiny fake Android dependencies.

No emulator or mocking of the production method body: exercises the actual
controller's save/paint/launcher behavior on the JVM.
"""
import pathlib
import subprocess
import sys
import tempfile

PROJECT = pathlib.Path(__file__).resolve().parents[1]
SOURCES = (pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else PROJECT / "app/src/main/java/com/agy/theme")
STUBS = {
    "android/content/Context.java": """
        package android.content;
        public class Context {
            public Context getApplicationContext() { return this; }
        }
    """,
    "android/app/Activity.java": """
        package android.app;
        public class Activity extends android.content.Context {}
    """,
    "com/agy/util/MainThreadPost.java": """
        package com.agy.util;
        public class MainThreadPost {
            public static int posts;
            public static void post(Runnable job) { posts++; job.run(); }
        }
    """,
    "com/agy/theme/ThemePrefs.java": """
        package com.agy.theme;
        public class ThemePrefs {
            public static int saved, writes;
            public static int load(android.content.Context ctx) { return saved; }
            public static void save(android.content.Context ctx,int mode) {
                saved=mode; writes++;
            }
        }
    """,
    "com/agy/theme/LauncherIconSwitcher.java": """
        package com.agy.theme;
        public class LauncherIconSwitcher {
            public static int calls;
            public static void scheduleSwitch(boolean dark) { calls++; }
            public static void apply(android.content.Context context, boolean dark) {}
        }
    """,
    "com/agy/theme/ActivityPainter.java": """
        package com.agy.theme;
        public class ActivityPainter {
            public static int calls;
            public static void apply(android.app.Activity ctx, boolean dark) { calls++; }
        }
    """,
    "com/agy/theme/ThemeResolver.java": """
        package com.agy.theme;
        public class ThemeResolver {
            public static boolean isDark(int mode, android.content.Context ctx) {
                return mode==ThemeColors.MODE_DARK;
            }
        }
    """,
    "com/agy/theme/ThemeControllerContractTest.java": """
        package com.agy.theme;
        public final class ThemeControllerContractTest {
            static void check(boolean ok, String label) {
                if (!ok) throw new AssertionError(label);
            }
            static void counters(int saved, int painted, int icons) {
                check(ThemePrefs.writes==saved, "prefs writes: " + ThemePrefs.writes);
                check(ActivityPainter.calls==painted, "paint calls: " + ActivityPainter.calls);
                check(LauncherIconSwitcher.calls==icons, "launcher calls: " + LauncherIconSwitcher.calls);
            }
            public static void main(String[] args) {
                android.app.Activity activity = new android.app.Activity();
                ThemeController c = new ThemeController(activity);
                check(c.mode()==ThemeColors.MODE_SYSTEM,"initial system mode");
                check(c.setThemeByUser("light"),"set light");
                check(c.mode()==ThemeColors.MODE_LIGHT,"light persisted");
                counters(1,1,1);
                check(c.setThemeByUser("light"),"repeat light");
                counters(1,1,1); // idempotent repaint and storage on duplicate events
                check(c.setThemeByUser("dark"),"dark after light");
                check(c.mode()==ThemeColors.MODE_DARK,"dark saved");
                counters(2,2,2);
                c.onVisualThemeChanged("light"); // visual-only MUST not persist user mode
                check(c.mode()==ThemeColors.MODE_DARK,"visual must not modify preference");
                counters(2,3,3);
                c.onVisualThemeChanged("invalid");
                counters(2,3,3);
                check(!c.setThemeByUser("purple"),"invalid theme rejected");
                counters(2,3,3);
                check(c.setTheme("system"),"explicit system");
                check(c.mode()==ThemeColors.MODE_SYSTEM,"system mode restored");
                counters(3,4,4);
                check(c.setTheme("system"),"duplicate system");
                counters(3,4,4);
                System.out.println("PASS ThemeController: one persist/repaint/icon update per change; visual callback does not save");
            }
        }
    """
}

def main():
    with tempfile.TemporaryDirectory(prefix="agy-theme-contract-") as tmp:
        base = pathlib.Path(tmp)
        files = []
        for name, src in STUBS.items():
            p = base / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(src)
            files.append(str(p))
        classes = base / "classes"
        classes.mkdir()
        prod = [str(SOURCES / "ThemeController.java"), str(SOURCES / "ThemeColors.java")]
        subprocess.run(["javac", "-d", str(classes), *files, *prod], check=True)
        subprocess.run(["java", "-cp", str(classes),
                        "com.agy.theme.ThemeControllerContractTest"], check=True)

if __name__ == "__main__":
    main()

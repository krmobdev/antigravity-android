import com.agy.core.CoreShellEnvironment;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class CoreShellEnvironmentTest {
    private static int checks;
    private static void check(boolean value) {
        if (!value) throw new AssertionError("shell policy check " + checks);
        checks++;
    }
    private static File shell(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[]{35, 33, 10});
        check(path.toFile().setExecutable(true, true));
        return path.toFile();
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("agy-shell-test-");
        try {
            File prefix = root.resolve("usr").toFile();
            File bin = root.resolve("runtime/bin").toFile();
            File system = shell(root.resolve("system/sh"));
            Map<String,String> env = new HashMap<>();
            env.put("TERMINFO", "/unrelated/private/prefix");
            CoreShellEnvironment.apply(env,prefix,bin,system);
            check(env.get("SHELL").equals(system.getAbsolutePath()));
            check(env.get("TERM").equals("xterm-256color"));
            check(!env.containsKey("TERMINFO"));
            File runtime = shell(bin.toPath().resolve("bash"));
            CoreShellEnvironment.apply(env,prefix,bin,system);
            check(env.get("SHELL").equals(runtime.getAbsolutePath()));
            File bash = shell(prefix.toPath().resolve("bin/bash"));
            Files.createDirectories(prefix.toPath().resolve("share/terminfo"));
            CoreShellEnvironment.apply(env,prefix,bin,system);
            check(env.get("SHELL").equals(bash.getAbsolutePath()));
            check(env.get("TERMINFO").equals(new File(prefix,"share/terminfo").getAbsolutePath()));
            check(bash.setExecutable(false,false));
            CoreShellEnvironment.apply(env,prefix,bin,system);
            check(env.get("SHELL").equals(runtime.getAbsolutePath()));
            Files.delete(runtime.toPath());
            Files.delete(system.toPath());
            boolean refused = false;
            try { CoreShellEnvironment.apply(env,prefix,bin,system); }
            catch (IllegalStateException expected) { refused = true; }
            check(refused);
            // A directory named bash must never be selected as an executable.
            Files.delete(bash.toPath());
            Files.createDirectory(bash.toPath());
            refused = false;
            try { CoreShellEnvironment.apply(env,prefix,bin,system); }
            catch (IllegalStateException expected) { refused = true; }
            check(refused);
            System.out.println("PASS CoreShellEnvironment: " + checks + " assertions");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (Exception e) { throw new RuntimeException(e); }
                });
            }
        }
    }
}

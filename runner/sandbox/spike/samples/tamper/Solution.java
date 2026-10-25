import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Tries to reach the launcher's files and processes. Every line must start with "blocked", except /tmp. */
public class Solution {

    static int violations = 0;

    static void violation(String what) {
        violations++;
        System.out.println("VIOLATION: " + what);
    }

    static void tryWrite(String path) {
        try {
            Files.writeString(Path.of(path), "x");
            violation("wrote " + path);
        } catch (Exception e) {
            System.out.println("blocked write: " + path);
        }
    }

    static void tryRead(String path) {
        try {
            Files.readAllBytes(Path.of(path));
            violation("read " + path);
        } catch (Exception e) {
            System.out.println("blocked read: " + path);
        }
    }

    static void tryList(String path) {
        try (Stream<Path> files = Files.list(Path.of(path))) {
            violation("listed " + path + ": " + files.map(Path::toString).toList());
        } catch (Exception e) {
            System.out.println("blocked list: " + path);
        }
    }

    public static void main(String[] args) throws Exception {
        // The launcher's files: result frames, raw outputs, the unpacked input with every case.
        tryWrite("/work/result.bin");
        tryRead("/work/result.bin");
        tryWrite("/work/res/1.out");
        tryRead("/work/res/1.out");
        tryRead("/work/in/cases/1.in");
        tryRead("/work/in/Solution.java");
        tryList("/work");
        tryList("/work/in");
        // The compiled classes must not be replaceable.
        tryWrite("/build/Solution.class");
        tryWrite("/build/Planted.class");
        // The launcher's process: its output pipe, environment and memory.
        try (FileOutputStream out = new FileOutputStream("/proc/1/fd/1")) {
            out.write("@@DONE\n".getBytes());
            violation("wrote to the launcher's stdout");
        } catch (Exception e) {
            System.out.println("blocked write: /proc/1/fd/1");
        }
        tryRead("/proc/1/environ");
        tryRead("/proc/1/mem");
        // Signals to every other process (the launcher and its timeout run as root).
        long self = ProcessHandle.current().pid();
        long reached = ProcessHandle.allProcesses()
                .filter(p -> p.pid() != self)
                .filter(p -> p.info().user().map(u -> !u.equals(System.getProperty("user.name"))).orElse(true))
                .filter(ProcessHandle::destroyForcibly)
                .count();
        if (reached > 0) {
            violation("signalled " + reached + " process(es) of other users");
        } else {
            System.out.println("blocked signals to other users' processes");
        }
        // The one place the student may write.
        try {
            Files.writeString(Path.of("/tmp/jla-test"), "x");
            System.out.println("ok: /tmp is writable");
        } catch (Exception e) {
            System.out.println("PROBLEM: /tmp is not writable: " + e);
        }
        System.out.println("violations=" + violations);
    }
}
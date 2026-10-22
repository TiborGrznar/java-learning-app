import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class Solution {
    public static void main(String[] args) throws Exception {
        System.out.println("user.name=" + System.getProperty("user.name"));
        System.out.println("env=" + System.getenv().keySet());
        System.out.println("cpus=" + Runtime.getRuntime().availableProcessors()
                + " maxHeapMB=" + Runtime.getRuntime().maxMemory() / 1024 / 1024);
        try (Stream<Path> files = Files.list(Path.of("/work/src"))) {
            files.forEach(p -> System.out.println("visible: " + p));
        }
        System.out.println(Files.readString(Path.of("/proc/self/status")).lines()
                .filter(l -> l.startsWith("Cap") || l.startsWith("NoNewPrivs") || l.startsWith("Seccomp"))
                .toList());
    }
}

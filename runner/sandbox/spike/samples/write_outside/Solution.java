import java.nio.file.Files;
import java.nio.file.Path;

public class Solution {
    public static void main(String[] args) {
        for (String target : new String[] {"/etc/jla-test", "/opt/jla-test", "/work/jla-test", "/tmp/jla-test"}) {
            try {
                Files.writeString(Path.of(target), "x");
                System.out.println("WRITABLE: " + target);
            } catch (Exception e) {
                System.out.println("blocked: " + target + " (" + e.getClass().getSimpleName() + ")");
            }
        }
    }
}

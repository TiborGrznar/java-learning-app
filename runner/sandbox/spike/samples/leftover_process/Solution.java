import java.util.Scanner;

/** Case 1 leaves a background process behind; case 2 checks that the launcher killed it. */
public class Solution {

    public static void main(String[] args) throws Exception {
        String mode = new Scanner(System.in).nextLine().trim();
        if (mode.equals("spawn")) {
            new ProcessBuilder("/bin/sh", "-c", "sleep 300 &").start().waitFor();
            System.out.println("spawned a background sleep");
        } else {
            long self = ProcessHandle.current().pid();
            boolean survived = ProcessHandle.allProcesses()
                    .filter(p -> p.pid() != self)
                    .anyMatch(p -> p.info().command().orElse("").endsWith("sleep"));
            System.out.println(survived
                    ? "VIOLATION: a background process survived the previous case"
                    : "ok: no leftover process");
        }
    }
}
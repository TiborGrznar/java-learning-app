public class Solution {
    public static void main(String[] args) throws Exception {
        new ProcessBuilder("/bin/sh", "-c", "while true; do sleep 100 & done").inheritIO().start().waitFor();
    }
}

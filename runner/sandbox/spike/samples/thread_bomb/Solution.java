public class Solution {
    public static void main(String[] args) {
        while (true) {
            new Thread(() -> {
                try { Thread.sleep(60_000); } catch (InterruptedException e) { }
            }).start();
        }
    }
}

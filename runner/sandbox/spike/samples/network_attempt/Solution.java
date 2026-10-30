import java.net.InetSocketAddress;
import java.net.Socket;

public class Solution {
    public static void main(String[] args) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("1.1.1.1", 80), 2000);
            System.out.println("CONNECTED (sandbox has network access!)");
        } catch (Exception e) {
            System.out.println("blocked: " + e);
        }
    }
}

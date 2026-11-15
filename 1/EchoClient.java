import java.io.*;
import java.net.*;

public class EchoClient {
    public static void main(String[] args) throws IOException {
        Socket s = new Socket("localhost", 5000);              // connect to server
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        PrintWriter out = new PrintWriter(s.getOutputStream(), true);
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Connected. Type messages, 'exit' to quit.");
        String msg;
        while ((msg = keyboard.readLine()) != null) {
            out.println(msg);                                  // send to server
            System.out.println("Server: " + in.readLine());    // read echoed reply
            if (msg.equalsIgnoreCase("exit")) break;
        }
        s.close();
    }
}

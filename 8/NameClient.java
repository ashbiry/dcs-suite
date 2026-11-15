import java.io.*;
import java.net.*;

// Same as Practical 1's EchoClient: send a line, print the reply.
public class NameClient {
    public static void main(String[] args) throws IOException {
        Socket s = new Socket("localhost", 5300);
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        PrintWriter out = new PrintWriter(s.getOutputStream(), true);
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Connected to name server.");
        System.out.println("Commands: LOOKUP name | REGISTER name address | UNREGISTER name | LIST | EXIT");
        String msg;
        while ((msg = keyboard.readLine()) != null) {
            out.println(msg);
            String reply = in.readLine();
            System.out.println("Server: " + reply);
            if (reply == null || reply.equals("BYE")) break;
        }
        s.close();
    }
}

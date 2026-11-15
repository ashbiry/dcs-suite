import java.io.*;
import java.net.*;

// 1. Publisher: reads messages from the keyboard and calls the web service (HTTP POST).
//    It does not know any subscriber or topic: the ESB decides where messages go.
public class Publisher {
    public static void main(String[] args) throws Exception {
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Type messages (include URGENT for alerts), 'exit' to quit:");
        String msg;
        while ((msg = keyboard.readLine()) != null && !msg.equals("exit")) {
            HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:8080/publish").openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            PrintWriter out = new PrintWriter(con.getOutputStream(), true);
            out.println(msg);                                             // send message as request body
            out.close();
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            System.out.println("Service: " + in.readLine());              // e.g. "Published to NEWS"
            con.disconnect();
        }
    }
}

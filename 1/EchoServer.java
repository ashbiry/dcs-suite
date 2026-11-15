import java.io.*;
import java.net.*;

// Concurrent echo server: main thread only accepts, one Thread per client does the echoing.
public class EchoServer {
    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(5000);          // 1. listen on port 5000
        System.out.println("Echo server started on port 5000");
        while (true) {
            Socket s = server.accept();                        // 2. blocks until a client connects
            System.out.println("Client connected: " + s.getPort());
            new ClientHandler(s).start();                      // 3. hand over to a new thread
        }
    }
}

class ClientHandler extends Thread {
    Socket s;
    ClientHandler(Socket s) { this.s = s; }

    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);   // true = auto flush
            String msg;
            while ((msg = in.readLine()) != null) {            // null => client closed
                System.out.println("[" + s.getPort() + "] received: " + msg);
                out.println("Echo: " + msg);                   // send same message back
                if (msg.equalsIgnoreCase("exit")) break;
            }
            s.close();
            System.out.println("Client " + s.getPort() + " disconnected");
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}

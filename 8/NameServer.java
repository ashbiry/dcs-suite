import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

// Name server = Practical 1's concurrent server + a table (name -> address).
// Commands (one line each): LOOKUP name | REGISTER name address | UNREGISTER name | LIST | EXIT
public class NameServer {
    // shared by all client threads -> ConcurrentHashMap (thread-safe HashMap, same methods)
    static ConcurrentHashMap<String, String> table = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        table.put("google.com", "142.250.195.14");
        table.put("aissmsioit.org", "103.21.58.10");
        table.put("printer", "192.168.1.50:9100");

        ServerSocket server = new ServerSocket(5300);
        System.out.println("Name server started on port 5300");
        while (true) {
            Socket s = server.accept();
            System.out.println("Client connected: " + s.getPort());
            new ClientHandler(s).start();
        }
    }
}

class ClientHandler extends Thread {
    Socket s;
    ClientHandler(Socket s) { this.s = s; }

    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[" + s.getPort() + "] request: " + line);
                String[] p = line.trim().split(" ");
                String cmd = p[0].toUpperCase();

                if (cmd.equals("LOOKUP") && p.length == 2) {
                    String addr = NameServer.table.get(p[1]);
                    if (addr != null) out.println("FOUND " + p[1] + " -> " + addr);
                    else out.println("NOT FOUND " + p[1]);
                } else if (cmd.equals("REGISTER") && p.length == 3) {
                    NameServer.table.put(p[1], p[2]);
                    out.println("OK registered " + p[1] + " -> " + p[2]);
                } else if (cmd.equals("UNREGISTER") && p.length == 2) {
                    if (NameServer.table.remove(p[1]) != null) out.println("OK removed " + p[1]);
                    else out.println("NOT FOUND " + p[1]);
                } else if (cmd.equals("LIST")) {
                    out.println(NameServer.table.toString());
                } else if (cmd.equals("EXIT")) {
                    out.println("BYE");
                    break;
                } else {
                    out.println("ERROR use: LOOKUP name | REGISTER name address | UNREGISTER name | LIST | EXIT");
                }
            }
            s.close();
            System.out.println("Client " + s.getPort() + " disconnected");
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}

import java.io.*;
import java.net.*;

// A HAND-WRITTEN STUB. Lives on the CLIENT. Pretends to be a Calculator, but has no maths in it.
// It only packs the call into a message, sends it, waits, and unpacks the answer.
// RMI generates a class that does this job for you automatically.
public class CalculatorStub implements Calculator {
    String host; int port;
    CalculatorStub(String host, int port) { this.host = host; this.port = port; }

    private int call(String method, int a, int b) {
        try (Socket s = new Socket(host, port)) {
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String msg = method + " " + a + " " + b;               // MARSHALLING: call -> text
            System.out.println("  [stub] sending   \"" + msg + "\"");
            out.println(msg);
            String reply = in.readLine();                          // wait for the answer
            System.out.println("  [stub] received  \"" + reply + "\"");
            return Integer.parseInt(reply);                        // UNMARSHALLING: text -> int
        } catch (IOException e) {
            throw new RuntimeException("Server unreachable (RMI would throw RemoteException)", e);
        }
    }
    public int add(int a, int b)      { return call("add", a, b); }
    public int subtract(int a, int b) { return call("subtract", a, b); }
    public int multiply(int a, int b) { return call("multiply", a, b); }
    public int divide(int a, int b)   { return call("divide", a, b); }
}

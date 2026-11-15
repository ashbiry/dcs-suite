import java.io.*;
import java.net.*;

// A HAND-WRITTEN SKELETON. Lives on the SERVER. Receives the message, unpacks it,
// calls the REAL object, packs the result and sends it back. (= Practical 1's ClientHandler,
// except it calls a method instead of echoing.) RMI's runtime does this job for you.
public class CalculatorSkeleton {
    public static void main(String[] args) throws Exception {
        Calculator real = new CalculatorImpl();                    // the REAL object, only here
        ServerSocket server = new ServerSocket(6000);
        System.out.println("Skeleton listening on 6000");
        while (true) {
            Socket s = server.accept();
            new Thread(() -> {                                     // one thread per client
                try {
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                    PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                    String[] p = in.readLine().split(" ");         // UNMARSHALLING: "add 30 25"
                    int a = Integer.parseInt(p[1]), b = Integer.parseInt(p[2]);
                    System.out.println("  [skeleton] got " + p[0] + "(" + a + "," + b + ")");
                    int r;
                    switch (p[0]) {                                 // DISPATCH to the real method
                        case "add":      r = real.add(a, b); break;
                        case "subtract": r = real.subtract(a, b); break;
                        case "multiply": r = real.multiply(a, b); break;
                        default:         r = real.divide(a, b);
                    }
                    out.println(r);                                 // MARSHALLING the result
                    s.close();
                } catch (Exception e) { System.out.println("Error: " + e); }
            }).start();
        }
    }
}

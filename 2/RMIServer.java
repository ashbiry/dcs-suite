import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

// 3. Server: create the object and register it under a name.
public class RMIServer {
    public static void main(String[] args) throws Exception {
        Registry registry = LocateRegistry.createRegistry(1099);   // start registry inside this JVM
        registry.rebind("Calculator", new CalculatorImpl());       // name -> object
        System.out.println("Calculator service ready on port 1099");
    }
}

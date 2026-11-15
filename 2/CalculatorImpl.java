import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

// 2. Remote object: the real implementation, lives on the server.
public class CalculatorImpl extends UnicastRemoteObject implements Calculator {
    public CalculatorImpl() throws RemoteException { super(); }   // exports the object

    private void log(String op) {   // shows which RMI thread serves each call
        System.out.println(op + " called on thread " + Thread.currentThread().getName());
    }
    public int add(int a, int b)      throws RemoteException { log("add");      return a + b; }
    public int subtract(int a, int b) throws RemoteException { log("subtract"); return a - b; }
    public int multiply(int a, int b) throws RemoteException { log("multiply"); return a * b; }
    public int divide(int a, int b)   throws RemoteException {
        log("divide");
        if (b == 0) throw new ArithmeticException("Division by zero");
        return a / b;
    }
}

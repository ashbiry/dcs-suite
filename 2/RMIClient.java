import java.rmi.Naming;
import java.util.Scanner;

// 4. Client: look up the name, get a stub, call methods as if local.
public class RMIClient {
    public static void main(String[] args) throws Exception {
        Calculator c = (Calculator) Naming.lookup("rmi://localhost:1099/Calculator");
        Scanner sc = new Scanner(System.in);
        System.out.print("Enter two numbers: ");
        int a = sc.nextInt(), b = sc.nextInt();
        System.out.println("Addition       = " + c.add(a, b));
        System.out.println("Subtraction    = " + c.subtract(a, b));
        System.out.println("Multiplication = " + c.multiply(a, b));
        System.out.println("Division       = " + c.divide(a, b));
    }
}

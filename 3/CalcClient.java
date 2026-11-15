import CalcApp.*;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.*;
import java.util.Scanner;

public class CalcClient {
    public static void main(String[] args) throws Exception {
        ORB orb = ORB.init(args, null);                                    // 1. start the ORB

        NamingContextExt ncRef = NamingContextExtHelper.narrow(
                orb.resolve_initial_references("NameService"));            // 2. find the naming service
        Calc calc = CalcHelper.narrow(ncRef.resolve_str("Calc"));          // 3. lookup "Calc" -> stub

        Scanner sc = new Scanner(System.in);
        System.out.print("Enter two numbers: ");
        int a = sc.nextInt(), b = sc.nextInt();
        System.out.println("Addition       = " + calc.add(a, b));           // 4. call like a local object
        System.out.println("Subtraction    = " + calc.subtract(a, b));
        System.out.println("Multiplication = " + calc.multiply(a, b));
        System.out.println("Division       = " + calc.divide(a, b));
    }
}

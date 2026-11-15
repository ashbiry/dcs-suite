// The client code looks EXACTLY like the RMI client: it just calls c.add(...).
// The only difference: here we build the stub ourselves; in RMI, Naming.lookup() hands us one.
public class ManualClient {
    public static void main(String[] args) throws Exception {
        Calculator c = new CalculatorStub("localhost", 6000);
        System.out.println("Addition = " + c.add(30, 25));
        System.out.println("Division = " + c.divide(30, 25));
    }
}

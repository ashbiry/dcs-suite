import java.rmi.Remote;
import java.rmi.RemoteException;

// 1. Remote interface: the "contract" both client and server know.
public interface Calculator extends Remote {
    int add(int a, int b) throws RemoteException;
    int subtract(int a, int b) throws RemoteException;
    int multiply(int a, int b) throws RemoteException;
    int divide(int a, int b) throws RemoteException;
}

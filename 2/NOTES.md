# Practical 2 – Remote Method Invocation (RMI)

**Aim:** multi-threaded client/server communication using Java RMI. Client calls methods of an object that lives in another JVM.

## Run
```
javac *.java
java RMIServer          # terminal 1 (starts registry + registers object)
java RMIClient          # terminal 2, enter e.g. 30 25
java RMIClient          # terminal 3 at the same time -> shows multithreading on server log
```
No `rmic`, no separate `rmiregistry` needed (Java 5+ makes stubs automatically; `createRegistry` starts the registry
inside the server). If the examiner wants the manual way: run `rmiregistry 1099` in another terminal (from the folder
with the .class files) and use `Naming.rebind("rmi://localhost/Calculator", obj)` in the server instead of createRegistry.

## The 4 files (write in this order)
1. `Calculator` – interface `extends Remote`, every method `throws RemoteException`.
2. `CalculatorImpl` – `extends UnicastRemoteObject implements Calculator`; constructor `throws RemoteException`.
3. `RMIServer` – `LocateRegistry.createRegistry(1099)` then `registry.rebind("Calculator", new CalculatorImpl())`.
4. `RMIClient` – `(Calculator) Naming.lookup("rmi://localhost:1099/Calculator")`, then call methods normally.

## The idea
Practical 1: you wrote sockets, streams, message formats yourself.
RMI: you just write `c.add(5, 3)` and RMI does all the socket work underneath.

```
CLIENT JVM                                   SERVER JVM
RMIClient                                    CalculatorImpl (real object)
   | c.add(5,3)                                    ^ add(5,3) -> 8
   v                                               |
 Stub  --marshal "add",5,3--> network --> Skeleton/RMI runtime (unmarshal, call)
 Stub  <--unmarshal 8------- network <--- marshal result 8
        (lookup "Calculator")
              \--------> RMI Registry :1099 (name -> stub)  <-- rebind by server
```

**Steps at runtime**
1. Server creates `CalculatorImpl`. Because it extends `UnicastRemoteObject`, it is *exported*: RMI opens a port and
   waits for calls to it.
2. Server binds it in the **registry** under the name "Calculator" (`rebind`).
3. Client asks the registry: `lookup("Calculator")`. It gets back a **stub**: a proxy object that implements `Calculator`.
4. Client calls `c.add(30,25)` on the stub. The stub **marshals** (packs) method name + arguments, sends them over TCP.
5. Server side (skeleton / RMI runtime) **unmarshals**, calls the real `add`, marshals the result 55 back.
6. Stub unmarshals 55 and returns it. To the client it looked like a normal method call.

**Where is "multi-threaded"?** The RMI runtime handles each incoming client connection on its own thread
(log shows `RMI TCP Connection(3)` and `(4)`). We didn't write any Thread code; RMI did it, unlike Practical 1.
Consequence: if the remote object had shared state, methods would need `synchronized`.

## Viva answers (manual FAQ)
1. **Heterogeneity:** a distributed system's parts differ in hardware, OS, network, language. Middleware hides this.
   Java RMI handles OS/hardware via the JVM, but is Java-only (CORBA solves the language part → Practical 3).
2. **Marshalling / unmarshalling:** marshalling = converting method name + arguments into a byte stream to send
   (serialization). Unmarshalling = rebuilding them on the other side. Example: `add(30,25)` → bytes → server rebuilds 30, 25.
   Result 55 travels back the same way.
3. **RMI with diagram:** draw the diagram above (client, stub, registry, skeleton, remote object).
4. **Binding:** associating a name with a remote object reference in the registry (`bind` / `rebind`).
5. **RMI registry:** a naming service (phone book) that maps names → remote object stubs, default port 1099.
   Started first because the server must register the object before any client can look it up.
6. **UnicastRemoteObject:** base class that exports the object so it can receive remote calls (single server,
   point-to-point). **lookup()**: client fetches a stub by name. **rebind()**: server binds name → object, replacing
   any old binding (`bind` would throw `AlreadyBoundException`).
7. **Stub and skeleton:** stub = client-side proxy (marshals call, sends, waits, unmarshals result).
   Skeleton = server-side counterpart (unmarshals, calls real method, marshals result). Since Java 2 the skeleton is
   built into the RMI runtime; since Java 5 stubs are generated dynamically, so `rmic` is no longer needed.
8. **Exception vs RemoteException:** `RemoteException` (checked, subclass of IOException) means a *communication*
   failure: server down, network broken, unmarshalling error. Normal exceptions are errors in the method's own logic
   (e.g. ArithmeticException in divide) and are sent back to the client and re-thrown there.

**Other likely questions**
- *RMI vs sockets?* Sockets = send raw bytes/text, you design the protocol. RMI = call methods on objects, protocol hidden.
- *RMI vs RPC?* RPC calls procedures (C-style, language-neutral). RMI calls methods on objects, Java-only, can pass objects.
- *Why must the interface extend Remote?* Marker interface: tells RMI these methods can be called remotely.
- *Why every method throws RemoteException?* Any remote call can fail because of the network; the caller must handle it.
- *What is passed by value vs by reference?* Normal objects are serialized (copied, must implement Serializable);
  remote objects are passed as stubs (reference).
- *Default port?* 1099.
- *Errors:* `NotBoundException` = wrong name / server not bound yet. `ConnectException` = server/registry not running.
  `ExportException: Port already in use: 1099` = old server still running.

---
# Stub & skeleton from scratch: one call `c.add(30, 25)` traced through the code

## Step 0 – The problem
Server and client are two separate programs = two separate JVMs = two separate memories.
`new CalculatorImpl()` on the server creates the object in the **server's RAM**.
A Java reference is basically "where in MY memory is this object". The client JVM cannot point into
the server's RAM. So **the client can never hold the real object.** The only way to reach it is a
network message (Practical 1).

## Step 1 – Solve it by hand with Practical 1
Goal: client code should look like `c.add(30, 25)`.

**Client side: a fake Calculator (= STUB)**
```java
class CalculatorStub implements Calculator {       // has the same methods...
    public int add(int a, int b) {                  // ...but no maths inside
        Socket s = new Socket("localhost", 6000);
        out.println("add " + a + " " + b);          // pack the call      = MARSHALLING
        String reply = in.readLine();               // wait for the answer
        return Integer.parseInt(reply);             // unpack the result  = UNMARSHALLING
    }
}
```
**Server side: receive, call the real object, reply (= SKELETON)**
```java
String[] p = in.readLine().split(" ");             // "add 30 25"         = UNMARSHALLING
if (p[0].equals("add")) r = real.add(30, 25);      // call the REAL object (dispatch)
out.println(r);                                    // send 55 back        = MARSHALLING
```
Stub = EchoClient that implements Calculator. Skeleton = ClientHandler that calls a method instead of echoing.
Client now writes `Calculator c = new CalculatorStub(...); c.add(30,25);` and it *looks* local.

Output of the demo:
```
[stub] sending "add 30 25"   ->   [skeleton] got add(30,25) -> real add -> 55
[stub] received "55"         <-
Addition = 55
```
Writing this for every interface and method is boring and error-prone.
**RMI = it writes the stub and the skeleton for you, from the interface.** That's the whole idea.

## Step 2 – What each line of the real RMI code does
| Code | What really happens |
|---|---|
| `interface Calculator extends Remote` | The list of methods the stub will copy. Both sides need it. |
| `throws RemoteException` | Every stub call goes over the network and can fail → caller must handle it. |
| `extends UnicastRemoteObject` + `super()` | **Exports** the object: RMI opens a hidden ServerSocket on a random port, starts listener threads, gives the object an ID, and creates its stub. |
| `LocateRegistry.createRegistry(1099)` | Starts the phone book (another ServerSocket on 1099). |
| `registry.rebind("Calculator", impl)` | Stores "Calculator" → **stub** (RMI swaps the real object for its stub; the real one never leaves the server). |
| `Naming.lookup("rmi://localhost:1099/Calculator")` | Client asks the phone book, receives the **stub** (serialized, rebuilt in client RAM). |
| `(Calculator)` cast | Works because the stub implements Calculator. |
| `c.add(a, b)` | Runs the **stub's** add, not the real one. |

## Step 3 – Timeline with input 30 25
**Phase A – server starts (`java RMIServer`)**
1. `new CalculatorImpl()` → `UnicastRemoteObject` constructor exports it: hidden socket on e.g. port 45231,
   object ID = #1. RMI prepares a stub containing {host=localhost, port=45231, id=#1}.
2. `createRegistry(1099)` → phone book listening on 1099.
3. `rebind("Calculator", impl)` → phone book now holds `"Calculator" → stub{localhost:45231, #1}`.
4. `main` ends, but the JVM keeps running because RMI's listener threads are alive.

**Phase B – client finds the object (`java RMIClient`)**
5. `Naming.lookup(...)` → connects to localhost:1099, asks for "Calculator".
6. Registry sends the stub as bytes; client JVM rebuilds it. `c` now points to the stub in **client RAM**.

**Phase C – the call `c.add(30, 25)`**
7. Stub's add runs. It connects to localhost:45231 and **marshals** `{id #1, method add(int,int), 30, 25}` into bytes.
8. Server: an RMI thread (`RMI TCP Connection(3)`, the same role as ClientHandler) receives the bytes.
   **Skeleton role:** unmarshal → find object #1 → call the **real** `CalculatorImpl.add(30, 25)` → your log prints → returns 55.
9. Skeleton role: marshal 55 → send back.
10. Stub unmarshals 55 and returns it → client prints `Addition = 55`.
Steps 7–10 repeat for subtract, multiply, divide.

**Failure cases**
- `divide(5, 0)`: ArithmeticException thrown on the server → marshalled back → stub re-throws it on the client.
- Server not running: the stub can't connect → `RemoteException` (ConnectException).

## Picture
```
          CLIENT JVM                                         SERVER JVM
 RMIClient: c.add(30,25)                                CalculatorImpl.add(30,25) -> 55
        |            ^                                         ^          |
        v            | 55                          call real   |          | 55
  +-----------+      |                                   +-------------+  |
  |   STUB    |------+                                   |  SKELETON   |<-+
  | (fake     | --- "id#1 add 30 25" (marshalled) ---->  | (RMI thread)|
  | Calculator)| <-------------- "55" (marshalled) ----- | unmarshal,  |
  +-----------+                                          | dispatch    |
        ^ got stub via lookup("Calculator")              +-------------+
        +--------------  RMI REGISTRY :1099  <---- rebind("Calculator") by server
```

## Summary table
| Thing | Lives in | Job | Practical 1 equivalent | Who writes it |
|---|---|---|---|---|
| Remote interface | both | the contract (method list) | the message format you agreed on | you |
| Remote object | server | does the real work | the echo logic | you |
| Stub | client | fake object: marshal, send, wait, unmarshal | EchoClient | RMI (auto) |
| Skeleton | server | unmarshal, call real method, marshal result | ClientHandler | RMI runtime (auto) |
| Registry | server, :1099 | name → stub phone book | (none: you hard-coded the port) | RMI (`createRegistry`) |

## 3-line viva answer
"A stub is a client-side proxy that implements the remote interface. When the client calls a method,
the stub marshals the method name and arguments, sends them to the server, waits, and unmarshals the result.
The skeleton is its server-side partner: it unmarshals the request, invokes the method on the actual remote
object, and marshals the result back. In modern Java the stub is generated dynamically and the skeleton's
work is done by the RMI runtime, so `rmic` is no longer needed."

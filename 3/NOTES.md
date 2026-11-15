# Practical 3 – CORBA with Java IDL (calculator)

**Aim:** a distributed application using CORBA and Java IDL.
Same calculator as RMI (Practical 2), so you only learn what is NEW. Tested on JDK 8.

## ⚠ Needs JDK 8
`idlj` (IDL compiler), `orbd` (naming service) and the `org.omg.*` classes were **removed in JDK 11**.
In WSL Ubuntu:
```
sudo apt update && sudo apt install -y openjdk-8-jdk
export PATH=/usr/lib/jvm/java-8-openjdk-amd64/bin:$PATH
java -version        # must say 1.8
which idlj orbd      # both must exist
```
Windows without WSL: download Temurin JDK 8 (adoptium.net), then in PowerShell
`$env:Path = "C:\path\to\jdk8\bin;$env:Path"`.

## Run (3 terminals, all with JDK 8 on PATH, all in this folder)
```
idlj -fall Calc.idl                    # generates folder CalcApp/ (stub, skeleton, helper...)
javac *.java CalcApp/*.java

orbd -ORBInitialPort 1050 &            # terminal 1: naming service (& = background; or own terminal)
java CalcServer -ORBInitialPort 1050 -ORBInitialHost localhost     # terminal 2
java CalcClient -ORBInitialPort 1050 -ORBInitialHost localhost     # terminal 3, enter 30 25
```
Expected client: Addition = 55, Subtraction = 5, Multiplication = 750, Division = 1.
Server prints each call. Order matters: **orbd → server → client**.
Stop orbd afterwards: `pkill orbd` (Windows: close its window). If port 1050 is busy, use 1051 everywhere.
`tnameserv -ORBInitialPort 1050` is an older alternative to orbd (non-persistent naming service).

## The idea (you already know it from RMI)
CORBA = RMI's idea (call a method on a remote object through a stub) but **language-neutral**:
client and server may be written in **different languages** (Java client, C++ server...) and run on different OSes.
To make that possible, the interface is written in a neutral language, **IDL**, and each side compiles it
into its own language.

| RMI (Practical 2) | CORBA (Practical 3) |
|---|---|
| `interface Calculator extends Remote` (Java) | `Calc.idl` (IDL, any language) |
| stubs generated automatically at runtime | `idlj -fall Calc.idl` **generates** stub + skeleton files |
| `CalculatorImpl extends UnicastRemoteObject` | `CalcImpl extends CalcPOA` (generated skeleton) |
| RMI registry, port 1099 | Naming Service (`orbd`), port 1050 |
| `registry.rebind("Calculator", obj)` | `ncRef.rebind(ncRef.to_name("Calc"), ref)` |
| `Naming.lookup(...)` + cast | `CalcHelper.narrow(ncRef.resolve_str("Calc"))` |
| JRMP protocol, Java only | **IIOP** protocol, any language |
| — | **ORB** = the middleware doing all the routing/marshalling |

### The IDL file
```
module CalcApp {                         // module -> Java package CalcApp
    interface Calc {                     // interface -> Java interface Calc
        long add(in long a, in long b);  // IDL long = Java int; "in" = input parameter
    };
};
```
(IDL `long` is 32-bit = Java `int`. `string` → Java `String`. Parameters: `in`, `out`, `inout`.)

### What `idlj -fall Calc.idl` generates (in folder CalcApp/)
| File | Role |
|---|---|
| `CalcOperations.java` | the Java interface with your methods |
| `Calc.java` | the full CORBA interface (CalcOperations + CORBA Object) |
| `_CalcStub.java` | **client stub**: marshals the call, sends it via ORB |
| `CalcPOA.java` | **server skeleton**: receives, unmarshals, calls your servant |
| `CalcHelper.java` | `narrow()`: converts a generic CORBA object reference into a `Calc` (= the cast) |
| `CalcHolder.java` | used for `out`/`inout` parameters |
`-fall` = generate files for **all** sides (client + server). `-fclient` / `-fserver` generate one side.

### Server, step by step
1. `ORB.init(args, null)` – start the ORB (reads `-ORBInitialPort 1050` from args).
2. Get the **RootPOA** and `activate()` its manager. POA = Portable Object Adapter: connects incoming requests
   to the right servant object.
3. `new CalcImpl()` – the **servant** (real object). `servant_to_reference` → its object reference.
4. `resolve_initial_references("NameService")` → the naming service (orbd); `narrow` to NamingContextExt.
5. `rebind(to_name("Calc"), ref)` – register the name.
6. `orb.run()` – wait for requests forever.

### Client, step by step
1. `ORB.init`.
2. Get the naming service.
3. `resolve_str("Calc")` returns a generic reference; `CalcHelper.narrow(...)` turns it into a `Calc` **stub**.
4. `calc.add(30, 25)` → stub → ORB → IIOP → server ORB → POA → skeleton `CalcPOA` → `CalcImpl.add` → 55 back.

### Writing from scratch: memorise these 6 server lines and 3 client lines
```java
// server
ORB orb = ORB.init(args, null);
POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
rootpoa.the_POAManager().activate();
org.omg.CORBA.Object ref = rootpoa.servant_to_reference(new CalcImpl());
NamingContextExt ncRef = NamingContextExtHelper.narrow(orb.resolve_initial_references("NameService"));
ncRef.rebind(ncRef.to_name("Calc"), ref);
orb.run();
// client
ORB orb = ORB.init(args, null);
NamingContextExt ncRef = NamingContextExtHelper.narrow(orb.resolve_initial_references("NameService"));
Calc calc = CalcHelper.narrow(ncRef.resolve_str("Calc"));
```
Pattern: **everything you get from the ORB is generic → `XxxHelper.narrow(...)` it to the real type.**
Imports: `CalcApp.*`, `org.omg.CORBA.ORB`, `org.omg.CosNaming.*`, `org.omg.PortableServer.*` (server only).

## Common errors
- `idlj: command not found` / `package org.omg.CORBA does not exist` → not on JDK 8.
- `CalcApp does not exist` → you forgot `idlj -fall Calc.idl` or compiled from another folder.
- `org.omg.CORBA.COMM_FAILURE` / `connect refused` → orbd not running, or port mismatch (1050 everywhere).
- `NotFound` exception in client → server not started yet (name "Calc" not bound).

## Viva answers (manual FAQ)
1. **What is CORBA?** Common Object Request Broker Architecture: a standard by the **OMG** (Object Management Group)
   that lets objects written in different languages, on different machines/OSes, call each other's methods.
2. **How does CORBA work?** Define the interface in IDL → compile to stubs (client) and skeletons (server) →
   server creates the object, registers its reference in the naming service → client looks up the reference,
   calls a method on the stub → client ORB marshals and sends it via IIOP → server ORB + POA deliver it to the
   skeleton → servant executes → result returns the same way.
3. **Synchronous or asynchronous?** Normal CORBA calls are **synchronous**: the client blocks until the reply comes
   (like our calculator). CORBA also supports **oneway** operations (fire and forget, no reply) and
   asynchronous messaging (AMI), so it can do both; the default is synchronous.
4. **ORB (Object Request Broker):** the middleware "post office": locates the remote object, marshals/unmarshals
   requests, sends them over the network (IIOP), delivers replies. Hides location, language and OS.
5. **IDL interface:** a language-neutral definition of the operations, parameters and types of a remote object.
   It is the contract; `idlj` (or a C++/Python IDL compiler) maps it to a real language.
6. **ORBD (Object Request Broker Daemon):** a background process shipped with the JDK that provides the
   **Naming Service** (name → object reference) and supports persistent servers. Here: `orbd -ORBInitialPort 1050`.
7. **Middleware:** software layer between the OS/network and the applications, which hides the complexity of
   distribution (communication, heterogeneity, location) and gives applications a simple interface.
8. **Examples of middleware:** CORBA, Java RMI, RPC / gRPC, message brokers (ActiveMQ, RabbitMQ, Kafka, JMS –
   Practical 9), ESBs, web servers/app servers, database middleware (JDBC/ODBC).
9. **Uses of middleware:** communication between distributed components, hiding heterogeneity (language/OS/hardware),
   naming and location transparency, transactions, security, load balancing, messaging/integration of legacy systems.
10. **Applications of CORBA:** telecom systems (network management), banking and finance, defence/aerospace,
    healthcare systems, integrating legacy enterprise systems written in different languages.

**Other likely questions**
- *POA?* Portable Object Adapter: server-side component that maps an incoming request (object reference) to the
  servant that implements it; portable across ORB vendors.
- *Servant?* The server-side object (CalcImpl) that actually implements the IDL operations.
- *IIOP?* Internet Inter-ORB Protocol: the TCP/IP protocol ORBs use to talk to each other (even from different vendors).
- *narrow()?* CORBA's safe cast from a generic `org.omg.CORBA.Object` to the specific interface type.
- *CORBA vs RMI?* RMI: Java-only, simpler, can pass Java objects by value. CORBA: any language, needs IDL,
  heavier, standard protocol IIOP.

---

## Detailed walkthrough (for beginners, in execution order)

This practical does **exactly the same job as Practical 2** (a calculator on a server, called from a client) but with **CORBA** instead of RMI. This time there are **three** programs running at once, so the side-by-side columns below are **orbd**, **server**, and **client**.

### Part A: The basics you need first

#### 1. Quick recap: calling a method on another computer

A **class** is a blueprint, an **object** is a real thing built from it (`new CalcImpl()`), and a **method** is something the object can do (`add(30, 25)`). An **interface** is a list of methods with no code inside, a contract that says "anything of this type can do these things."

In Practical 2 (RMI), the client wrote `c.add(30, 25)` and a hidden **stub** on the client side packed that call into bytes, sent it to the server, waited, and handed back `55`. The client never wrote any socket code. A **registry** (phone book) let the client find the server's object by name.

CORBA is the **same idea**, with one big difference.

#### 2. What CORBA adds: any language can talk to any language

RMI only works **Java ↔ Java**. Real companies have old systems written in C++, COBOL, Python, and so on, which still need to call each other.

**CORBA** (Common Object Request Broker Architecture) is a standard, set by a group called the **OMG**, that lets a client in one language call methods on an object written in **another language**, possibly on another operating system.

To make that work, the contract can't be written in Java, because a C++ program couldn't read it. So it's written in a **neutral language** called **IDL** (Interface Definition Language), and each side translates it into its own language.

**Analogy:** RMI is two Indians talking in Hindi. CORBA is a UN meeting: everyone agrees on the meeting agenda in a neutral format (IDL), and each delegate has an interpreter (the ORB) who translates into their own language.

#### 3. The key players

| Player | What it is | RMI equivalent (Practical 2) |
|---|---|---|
| **IDL file** (`Calc.idl`) | The contract, in neutral language | `Calculator.java` interface |
| **`idlj`** | A tool that **translates IDL into Java files** (stub, skeleton, helper) | None. RMI made stubs automatically. |
| **ORB** (Object Request Broker) | The **interpreter / post office** in each program. It finds remote objects, packs and unpacks calls, and sends them over the network. | The hidden RMI runtime |
| **orbd** (ORB daemon) | A separate background program that runs the **Naming Service**, a phone book that maps "Calc" to the object's address | RMI registry |
| **Servant** (`CalcImpl`) | The **real object** on the server that actually does `a + b` | `CalculatorImpl` |
| **POA** (Portable Object Adapter) | Server-side **receptionist**: when a request arrives, it hands it to the right servant | Done invisibly by RMI |
| **Stub** | Client-side stand-in that looks like a `Calc` but forwards each call over the network | Stub (same idea) |
| **Skeleton** (`CalcPOA`) | Server-side counterpart of the stub: unpacks the request and calls the servant | Hidden in RMI |
| **IIOP** | The network protocol ORBs use to talk to each other (over TCP/IP) | JRMP |

**A daemon** is a program that runs in the background with no screen of its own, waiting to serve others.

#### 4. The IDL file

```
module CalcApp {                         // becomes a Java "package" (folder) called CalcApp
    interface Calc {                     // becomes a Java interface called Calc
        long add(in long a, in long b);  // IDL "long" = Java "int" (a whole number)
        ...
    };
};
```

- `module` is a **group name**. In Java it becomes a **package**, which is just a folder of related classes.
- `in` means the parameter is an **input** (the client sends it to the server). IDL also has `out` (the server sends back) and `inout` (both ways).
- IDL `long` is 32 bits, the same size as Java's `int`. (Confusingly, Java also has its own `long`, which is bigger. Ignore that here.)

#### 5. The new Java pieces you'll see

| Code | Meaning |
|---|---|
| `import CalcApp.*;` | "I want to use the classes in the CalcApp folder" (the ones `idlj` generated). `*` means all of them. |
| `String[] args` in `main(String[] args)` | The **extra words typed after the program name** on the command line. For `java CalcServer -ORBInitialPort 1050 -ORBInitialHost localhost`, `args` holds those four words. |
| `XxxHelper.narrow(something)` | CORBA's **safe cast**. The ORB always hands back a generic "some CORBA object." `narrow` checks it really is an `Xxx` and converts it, like `(Calculator)` in Practical 2. |
| `org.omg.CORBA.Object` | The full name of CORBA's generic object type. The full name is needed because Java has its own unrelated class called `Object`. |
| `cond ? A : B` | A one-line if/else: "if cond, give A, otherwise give B." |

---

### Part B: The execution, step by step

**Example:** the client enters `30 25`.

#### Step 0: Before running, generate the CORBA files from the IDL

```
idlj -fall Calc.idl
javac *.java CalcApp/*.java
```

`idlj` reads `Calc.idl` and **writes Java code for you** into a new folder `CalcApp/`. `-fall` means "generate files for **all** sides," both client and server.

| Generated file | Role | Used by |
|---|---|---|
| `CalcOperations.java` | The Java interface with `add`, `subtract`, `multiply`, `divide` | Both |
| `Calc.java` | The full CORBA interface type (`CalcOperations` + CORBA object abilities) | Both |
| `_CalcStub.java` | The **stub**: packs a call and sends it through the ORB | Client |
| `CalcPOA.java` | The **skeleton**: unpacks a request and calls your servant | Server |
| `CalcHelper.java` | Contains `narrow()` (the safe cast) | Both |
| `CalcHolder.java` | Used for `out`/`inout` parameters (not needed here) | Both |

Then `javac` compiles everything (your three files plus the generated ones).

**Why this step exists:** in RMI, Java made the stub automatically because both sides were Java. Here, the C++ side would run a *C++* IDL compiler on the *same* `Calc.idl`, and the two sides would still match.

#### Step 1: Start the naming service (orbd)

| orbd (terminal 1) | Server (terminal 2) | Client (terminal 3) |
|---|---|---|
| `orbd -ORBInitialPort 1050` | | |
| Starts the **Naming Service** on port 1050. Prints nothing and keeps running. | | |

The phone book is now open, but empty.

#### Step 2: The server starts its ORB

| orbd | Server | Client |
|---|---|---|
| ⏸ waiting | `java CalcServer -ORBInitialPort 1050 -ORBInitialHost localhost` | |
| | `ORB orb = ORB.init(args, null)` starts this program's **ORB** (its interpreter). It reads `args` and learns: "the naming service is at `localhost`, port `1050`." | |

That's why the same `-ORBInitialPort 1050` must be typed for orbd, the server, and the client: it's how they all find the same phone book.

#### Step 3: The server prepares the receptionist (POA)

| Server code | Meaning |
|---|---|
| `orb.resolve_initial_references("RootPOA")` | Asks the ORB: "give me the main receptionist." It comes back as a generic CORBA object. |
| `POAHelper.narrow(...)` | Safely converts it into a `POA`. |
| `rootpoa.the_POAManager().activate()` | **Opens the reception desk**. Until this runs, incoming requests would be held and not delivered. |

#### Step 4: The server creates the real calculator (the servant)

| Server code | Meaning |
|---|---|
| `CalcImpl calc = new CalcImpl()` | Builds the real calculator object. |
| `class CalcImpl extends CalcPOA` | `CalcImpl` **is a kind of** `CalcPOA` (the generated skeleton), so it inherits all the "receive a request and unpack it" code. `CalcImpl` only has to write the actual maths. |
| `rootpoa.servant_to_reference(calc)` | Registers the servant with the receptionist and gets back its **object reference**: a network address that says "this object, on this machine, at this port." |

**Servant vs reference:** the servant is the actual object in the server's memory. The reference is like its **postal address**, which can be handed to other programs anywhere.

#### Step 5: The server registers the name in the phone book

| orbd | Server | Client |
|---|---|---|
| | `orb.resolve_initial_references("NameService")` asks the ORB to find the naming service. The ORB contacts orbd at localhost:1050. | |
| | `NamingContextExtHelper.narrow(...)` converts it to a usable phone book object, `ncRef`. | |
| Stores **"Calc" → the calculator's reference** | `ncRef.rebind(ncRef.to_name("Calc"), ref)` | |
| | Prints **`CalcServer ready, waiting for clients...`** | |
| | `orb.run()` ⏸ **waits forever** for requests | |

- `to_name("Calc")` converts the text `"Calc"` into CORBA's name format.
- `rebind` (not `bind`) replaces any old entry with the same name, so restarting the server is safe.
- `orb.run()` is like Practical 1's `while(true) accept()`: it keeps the server alive and lets the ORB handle requests.

The state now:
```
orbd (port 1050)     { "Calc" → reference to CalcImpl in the server }
Server               POA active, CalcImpl ready, orb.run() waiting
```

#### Step 6: The client starts and looks up "Calc"

| orbd | Server | Client |
|---|---|---|
| | ⏸ in `orb.run()` | `java CalcClient -ORBInitialPort 1050 -ORBInitialHost localhost` |
| | | `ORB.init(args, null)` starts the client's own ORB, which learns where orbd is. |
| | | `resolve_initial_references("NameService")` + `narrow` gives the phone book object `ncRef`. |
| Looks up "Calc" and sends back the **reference** | | `ncRef.resolve_str("Calc")` asks: "who is Calc?" |
| | | `CalcHelper.narrow(...)` turns the generic reference into a **`Calc` stub** (an object of the generated `_CalcStub` class), stored in `calc`. |

From now on, **orbd is no longer involved**. The client talks directly to the server, just like RMI after the registry lookup.

#### Step 7: The client reads your input

| Client code | Result |
|---|---|
| `Scanner sc = new Scanner(System.in)` | Keyboard reader (the same as Practical 2) |
| Prints `Enter two numbers: ` and you type `30 25` | `a = 30`, `b = 25` |

#### Step 8: The first remote call, `calc.add(30, 25)`

| Server | Client |
|---|---|
| | `calc.add(30, 25)`. `calc` is the **stub**, not the real calculator. |
| | The stub asks the client **ORB** to pack ("marshal") the call: operation `add`, arguments 30 and 25. |
| | The client ORB sends it over the network using **IIOP**. |
| | The client **waits** ⏸ (the call is **synchronous**: the client is blocked until the answer comes back). |
| The server **ORB** receives the bytes. | |
| The **POA** looks at which object the request is for and finds `CalcImpl`. | |
| The **skeleton** (`CalcPOA`, inherited by `CalcImpl`) unpacks "add, 30, 25" and calls `add(30, 25)`. | |
| `CalcImpl.add` prints **`add(30,25)`** and returns **55**. | |
| The server ORB packs 55 and sends it back. | |
| | The client ORB unpacks 55, and the stub returns it as if `add` had just run locally. |
| | Prints **`Addition       = 55`** |

The round trip:
```
CLIENT                                                         SERVER
calc.add(30,25)
   │
STUB → client ORB ── IIOP over network ──▶ server ORB → POA → skeleton (CalcPOA)
                                                                   │
                                                             CalcImpl.add → 55
STUB ← client ORB ◀── IIOP over network ── server ORB ◀───────────┘
   │
println("Addition = 55")
```

#### Step 9: The remaining calls

The same round trip happens three more times:

| Client calls | Server prints | Returns | Client prints |
|---|---|---|---|
| `calc.subtract(30, 25)` | `subtract(30,25)` | 5 | `Subtraction    = 5` |
| `calc.multiply(30, 25)` | `multiply(30,25)` | 750 | `Multiplication = 750` |
| `calc.divide(30, 25)` | `divide(30,25)` | 1 | `Division       = 1` |

- **Why 1?** Both are whole numbers (`int`), so Java throws away the remainder: 30 / 25 = 1.2, which becomes 1.
- **Divide by zero:** `return b == 0 ? 0 : a / b;` reads as "if b is 0, return 0; otherwise return a / b." So unlike Practical 2, entering `10 0` does **not** crash; it prints `Division = 0`.

The client's `main` ends and the **client exits**. The **server stays in `orb.run()`**, ready for the next client, and orbd keeps running too. (Stop them with Ctrl+C and `pkill orbd`.)

---

### Summary

#### The whole thing on one page

```
 BUILD:   Calc.idl ──idlj -fall──▶ CalcApp/ (stub, skeleton, helper...) ──javac──▶ .class files

 RUN:
 orbd (1050)                SERVER                                   CLIENT
 ───────────                ──────                                   ──────
 naming service up
                            ORB.init
                            RootPOA + activate
                            new CalcImpl (servant) → reference
 "Calc" → ref  ◀──rebind──  
                            orb.run() ⏸
                                                                      ORB.init
               ──resolve_str("Calc")──────────────────────────────▶  stub (via CalcHelper.narrow)
                            ◀─────────── calc.add(30,25) via IIOP ──
                            CalcImpl.add → 55 ─────────────────────▶ "Addition = 55"
```

#### Mapping code to CORBA concepts

| Concept | Code |
|---|---|
| The contract (neutral language) | [Calc.idl](Calc.idl) |
| Generated stub / skeleton / helper | `CalcApp/` folder from `idlj -fall` |
| Start the ORB | `ORB.init(args, null)` (server and client) |
| Open the receptionist | `RootPOA` + `the_POAManager().activate()` |
| The real object (servant) | `class CalcImpl extends CalcPOA` in [CalcServer.java](CalcServer.java) |
| Get the object's address | `rootpoa.servant_to_reference(calc)` |
| Find the phone book | `resolve_initial_references("NameService")` + `NamingContextExtHelper.narrow` |
| Register the name | `ncRef.rebind(ncRef.to_name("Calc"), ref)` |
| Wait for calls | `orb.run()` |
| Look up and get the stub | `CalcHelper.narrow(ncRef.resolve_str("Calc"))` in [CalcClient.java](CalcClient.java) |
| Call remotely | `calc.add(a, b)` |

**The one pattern to remember:** whatever you get from the ORB is a generic object, so you always `XxxHelper.narrow(...)` it into the real type.

**Compared with the earlier practicals:** Practical 1 wrote raw sockets by hand. Practical 2 (RMI) hid the sockets behind a stub but worked for Java only. Practical 3 (CORBA) hides them the same way **and** lets different languages talk, at the cost of the extra IDL step, the `idlj` tool, and the separate orbd naming service.

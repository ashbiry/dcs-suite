# DCS Lab Suite – Distributed Computing Systems Practicals

Java practicals for the Distributed Computing Systems lab. Each practical folder holds the code, a
`NOTES.md` (theory, how to run, how to write it from scratch, viva answers) and a `sample-output.txt`.

Start with [`SETUP.md`](SETUP.md) (WSL / JDK / Maven), then open the practical you need.

## Download a practical

**Linux / WSL / macOS (bash):**
```bash
bash <(curl -fsSL https://raw.githubusercontent.com/ashbiry/dcs-suite/main/install.sh)
```

**Windows (PowerShell):**
```powershell
irm https://raw.githubusercontent.com/ashbiry/dcs-suite/main/install.ps1 | iex
```

Pick a number from the menu. The files land in `./practical-<n>/`. Choose `99` for `SETUP.md` only,
or `0` to download everything.

## Practicals

| # | Topic | Files | Needs |
|---|---|---|---|
| 1 | Concurrent echo server (sockets + threads) | `EchoServer.java`, `EchoClient.java` | any JDK |
| 2 | Java RMI calculator (+ hand-written stub/skeleton demo) | `Calculator.java`, `CalculatorImpl.java`, `RMIServer.java`, `RMIClient.java` | any JDK ≤ 17 |
| 3 | CORBA | coming soon | JDK 8 |
| 4 | Berkeley clock synchronization | `Berkeley.java` | any JDK |
| 5 | Leader election (Bully + Ring) | `Election.java` | any JDK |
| 6 | MapReduce word count | `WordCount.java` | any JDK |
| 7 | Termination detection | coming soon | any JDK |
| 8 | Name server | `NameServer.java`, `NameClient.java` | any JDK |
| 9 | Pub/Sub, ESB, JMS | coming soon | Maven + internet |

## Quick run
```bash
cd practical-<n>
javac *.java
java <MainClass>        # see NOTES.md for which class(es) to start, and in what order
```

## Marking scheme
Understanding 10 | Implementation 20 | Result 10 | Viva 10

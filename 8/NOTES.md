# Practical 8 – Name Server

**Aim:** client-server application implementing a name server (name → address lookup service).

## Run
```
javac NameServer.java NameClient.java
java NameServer        # terminal 1
java NameClient        # terminal 2 (and 3, 4... any number at once)
```
Try: `LOOKUP google.com`, `REGISTER fileserver 10.0.0.7:2100`, `LOOKUP fileserver`, `LIST`,
`UNREGISTER fileserver`, `EXIT`. A second client sees what the first one registered (shared table).

## The idea, from zero
Computers reach each other by **address** (IP + port, e.g. 142.250.195.14). Humans and programs prefer
**names** (google.com, "printer"). Something must translate name → address: that is **name resolution**,
done by a **name server**. You use one every day: typing google.com, your PC first asks a DNS server
"what's the IP of google.com?", then connects to that IP.

Big benefit = **location transparency**: clients only know the NAME. If the printer moves to a new machine,
only its entry in the name server changes; no client code changes.

You've already used two name servers: the **RMI registry** in Practical 2 ("Calculator" → stub) and the
CORBA Naming Service in Practical 3.

## How the program works = Practical 1 + a table
| Practical 1 (echo) | Practical 8 (name server) |
|---|---|
| ServerSocket, accept loop, thread per client | **exactly the same** |
| ClientHandler: read line, send `Echo: line` | ClientHandler: read line, **split into words, look at the command, use the table, reply** |
| EchoClient | NameClient (identical, just a different port and stops on `BYE`) |
| — | `table`: name → address, shared by all threads |

### The table
```java
static ConcurrentHashMap<String, String> table = new ConcurrentHashMap<>();
```
`static` = ONE table for the whole program, shared by every client thread.
Same methods as HashMap: `put(name, addr)`, `get(name)` (null if absent), `remove(name)`.

### Why ConcurrentHashMap and not HashMap? (FAQ 5)
Several ClientHandler threads run at the same time and may change the table at the same moment
(client A registers while client B unregisters). A plain HashMap is **not thread-safe**: two threads modifying
it simultaneously can corrupt it or lose entries (race condition). ConcurrentHashMap handles the locking
internally, so simultaneous reads/writes are safe. You don't write any `synchronized` code.

### Handling one request, traced
Client types `REGISTER fileserver 10.0.0.7:2100`:
1. Client `out.println(...)` sends the line.
2. Handler `in.readLine()` gets it; `line.trim().split(" ")` → `p = ["REGISTER", "fileserver", "10.0.0.7:2100"]`.
3. `p[0]` is the command; `p.length == 3` checks the right number of words.
4. `table.put("fileserver", "10.0.0.7:2100")`; reply `OK registered ...`.
5. Later any client: `LOOKUP fileserver` → `table.get("fileserver")` → `"10.0.0.7:2100"` → `FOUND ...`.
   Unknown name → `get` returns `null` → `NOT FOUND`.

### Protocol (our own text commands)
| Request | Reply |
|---|---|
| `LOOKUP name` | `FOUND name -> address` / `NOT FOUND name` |
| `REGISTER name address` | `OK registered ...` (replaces old address) |
| `UNREGISTER name` | `OK removed name` / `NOT FOUND name` |
| `LIST` | whole table |
| `EXIT` | `BYE`, connection closed |

Typical real flow: a **service registers** its address when it starts → a **client looks up** the name →
client then connects **directly** to that address (name server is not involved in the actual work) →
service **unregisters** when it shuts down.

## Viva answers (manual FAQ), explained
1. **Name vs address vs identifier:**
   - **Name** = human-friendly string: `google.com`, `printer`. Can change meaning over time.
   - **Address** = where the thing actually is right now: `142.250.195.14:443`. Can change if it moves.
   - **Identifier** = a name that refers to **exactly one** entity, never reused, never changes (e.g. a MAC
     address, a database primary key, RMI's object ID).
2. **Name resolution** = converting a name to an address.
   For hierarchical names (DNS) the lookup goes through several servers, two ways:
   - **Iterative:** the client asks root → root says "ask the .org server" → client asks .org → ".org says ask
     aissmsioit.org's server" → client asks that → gets the IP. The **client** does all the asking.
   - **Recursive:** the client asks one server; **that server** asks the next one on the client's behalf, and so on,
     and the final answer comes back down the chain. Less work for the client, more load on servers.
3. **Location transparency:** the user/program uses a name and doesn't know or care where the resource physically is;
   it can move without clients changing anything.
4. **How DNS resolves `www.aissmsioit.org`:** your PC asks its local DNS resolver (ISP / 8.8.8.8). If not cached,
   resolver asks a **root server** → gets the **.org** server → asks it → gets the **aissmsioit.org** authoritative
   server → asks it → gets the IP → returns it to you and **caches** it for a while (TTL). Names are resolved
   **right to left**, level by level (hierarchical).
5. **ConcurrentHashMap:** see above (many threads, thread-safe without manual locking).
6. **Single point of failure:** our one name server dies → nobody can resolve any name → whole system stuck,
   even though the actual services are fine. Solutions: **replication** (several copies of the name server),
   **hierarchy/partitioning** (like DNS: many servers each responsible for part of the names), **caching** at
   clients (keep recent answers, still works for a while).
7. **RMI registry vs CORBA Naming vs DNS:**
   | | RMI registry | CORBA Naming Service | DNS |
   |---|---|---|---|
   | Maps | name → Java remote object stub | name → CORBA object reference | host name → IP address |
   | Languages | Java only | any (via IDL) | any |
   | Structure | flat, one machine | hierarchical (naming contexts) | hierarchical, worldwide, replicated |
   | Port | 1099 | 900 / 1050 (orbd) | 53 |

**Other likely questions**
- *Flat vs hierarchical naming?* Flat = no structure ("printer"), must be unique globally, doesn't scale.
  Hierarchical = parts separated by dots, each level managed by a different server (DNS) → scales.
- *Why is the name server multi-threaded?* Same reason as Practical 1: many clients at once.
- *TCP or UDP?* We used TCP. Real DNS mostly uses UDP port 53 (small, fast queries), TCP for big replies.

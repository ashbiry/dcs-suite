# Practical 1 – Concurrent Echo Client/Server (Java sockets)

**Aim:** a server that echoes back whatever a client sends, and can serve many clients at the same time.

## Run
```
javac *.java
java EchoServer          # terminal 1 (start first)
java EchoClient          # terminal 2
java EchoClient          # terminal 3 (second client -> proves concurrency)
```
Type messages in a client; type `exit` to quit. Expected: `Server: Echo: <your message>`.

## The idea in 6 lines
1. Server: `new ServerSocket(5000)` → "I'm listening on port 5000".
2. `server.accept()` **blocks** until a client connects, then returns a new `Socket` for that client.
3. Server starts a new `Thread` for that socket and goes straight back to `accept()`.
4. Client: `new Socket("localhost", 5000)` connects.
5. Both sides read/write using streams: `getInputStream()` / `getOutputStream()`.
6. Handler thread loops: read a line → print it → send `Echo: line` back → stop on `exit`.

```
Client A --\                         /-- Thread A (echo for A)
Client B ---+--> ServerSocket:5000 --+-- Thread B (echo for B)
Client C --/    (accept loop)        \-- Thread C (echo for C)
```

## Code structure (what to write from scratch)
- `EchoServer.main`: ServerSocket + `while(true){ accept(); new ClientHandler(s).start(); }`
- `ClientHandler extends Thread`: constructor stores socket; `run()` has the read/echo loop.
- `EchoClient.main`: Socket + keyboard reader + loop (send, print reply).
- Remember: `PrintWriter(..., true)` → auto-flush, otherwise the message stays buffered and nothing arrives.
- `readLine()` returns `null` when the other side closes → exit the loop.

## Theory / viva answers (manual FAQ)
1. **IPC (interprocess communication):** mechanisms by which processes exchange data and coordinate:
   sockets, pipes, shared memory, message queues, RPC/RMI.
2. **Socket:** one endpoint of a two-way communication link between two programs on a network;
   identified by IP address + port number.
3. **TCP vs UDP:** TCP = connection-oriented, reliable, ordered, retransmits lost data, slower
   (`Socket`/`ServerSocket`). UDP = connectionless, no guarantee of delivery/order, faster, low overhead
   (`DatagramSocket`/`DatagramPacket`). Echo here uses TCP.
4. **Shared memory programming:** processes/threads communicate by reading/writing a common memory region
   (needs synchronisation: locks/semaphores). Sockets instead pass messages over the network.
5. **Port:** 16-bit number (0–65535) that identifies which application on a host should receive data.
   Examples: 80 HTTP, 443 HTTPS, 22 SSH. We use 5000.
6. **Why threads?** Without threads the server handles one client at a time; others wait in the queue while it
   is stuck inside one client's loop. A thread per client = concurrent service.
7. **Real-world extension:** chat server (broadcast to all clients), file transfer, thread pool
   (`ExecutorService`) instead of unbounded threads, authentication, TLS (`SSLSocket`), logging, timeouts.
8. (Same as 6.) Thread-per-client downside: many threads = heavy → use a thread pool or NIO.

**Other likely questions**
- *What does `accept()` do?* Blocks until a client connects; returns a new socket dedicated to that client.
- *Why two sockets on the server?* `ServerSocket` only listens; the `Socket` from `accept()` carries the data.
- *Concurrent vs iterative server?* Iterative = one client at a time; concurrent = many at once (threads/processes).
- *Why start the server first?* Otherwise client gets `Connection refused`.
- *What if two servers use port 5000?* `BindException: Address already in use`.
- *`Thread.start()` vs `run()`?* `start()` creates a new thread; calling `run()` directly runs in the current thread.

## Tested here
Two clients connected simultaneously; server interleaved their messages (output in `sample-output.txt`).

---
## Concepts explained (long version)

**Why this exists.** Programs in a distributed system share no memory. They cooperate only by sending messages (IPC).
Echo = simplest message exchange: the server just sends back what it gets.

**IP + port.** IP = which machine (building address). Port = which program on that machine (flat number).
`localhost:5000` = this machine, the program listening on 5000.

**ServerSocket vs Socket.** `ServerSocket` = receptionist: only listens on the port, never carries data.
`accept()` waits for a caller and returns a NEW `Socket` = private line for that one client.
Server ends up with 1 ServerSocket + 1 Socket per client.
Precisely: `accept()` creates the Socket in the main thread; OUR code then starts a thread and passes the socket into it.

**TCP.** Connection first (handshake), then guaranteed delivery, in order, no duplicates. UDP = no connection, no guarantees, faster.

**Streams.** `getInputStream()` = what the other side sent; `getOutputStream()` = what I send.
Wrap: InputStreamReader (bytes→chars) → BufferedReader (`readLine()`); PrintWriter (`println()`).
`new PrintWriter(out, true)` → auto-flush. Forget `true` → message stuck in buffer → both sides hang.
`readLine()` blocks until a line arrives; returns `null` when the other side closed the connection.

**Iterative vs concurrent.** Without threads, the server sits in client A's read loop and never calls `accept()` again → B waits.
With a thread per client, main thread only accepts; each ClientHandler thread talks to one client → all served at once.
`start()` creates a new thread that runs `run()`; calling `run()` directly runs it in the main thread (= iterative again).

**Two ways a client "leaves".** `exit` = our own application-level rule (polite goodbye).
`readLine() == null` = TCP-level: the connection closed (client typed exit, pressed Ctrl+C, or crashed).
The null check is the one that always works; exit alone would miss a crashed client.

**Proof of result.** Server log shows lines from two clients interleaved → concurrency.

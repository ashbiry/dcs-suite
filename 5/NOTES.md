# Practical 5 – Leader Election: Bully and Ring algorithms

**Aim:** implement the Bully and Ring algorithms to elect a new coordinator when the old one fails.
One file, simulation (no networking). Both algorithms run on the same input.

## Run
```
javac Election.java
java Election
```
Input from the manual's example: `7` processes, crashed `7`, detected by `4` → both elect **P6**.

## The problem, from zero
Many distributed algorithms need ONE process in charge, the **coordinator** (leader): e.g. the Berkeley
master in Practical 4, a lock manager that decides who may use a shared file, the process that assigns work.
If the coordinator crashes, the others must agree on a new one **by themselves** (no boss to ask).
That agreement procedure = **election algorithm**.

Rules both algorithms assume:
- every process has a **unique number** (ID); **highest alive ID should win**;
- every process knows the others' IDs;
- a process "notices" a crash when the coordinator stops replying (timeout).

## BULLY algorithm – "the biggest alive one wins"
Any process can message any other process directly.

When P notices the coordinator is dead:
1. P sends **ELECTION** to **every process with a higher number**.
2. If **nobody answers** → no one bigger is alive → **P wins**, sends **COORDINATOR** to everyone.
3. If **anyone answers OK** ("I'm alive and bigger, I'll take over") → P stops; P's part is done.
4. Every process that answered OK now holds its **own** election the same way (step 1).
5. Eventually the highest alive process gets no OK → it wins and announces itself.
6. If a crashed higher process comes back later, it starts an election and wins again (it "bullies" its way back).

**Traced (7 processes, P7 dead, P4 notices):**
```
P4 -> P5, P6, P7 : ELECTION      P5 OK, P6 OK, P7 silent   -> P4 stops
P5 -> P6, P7     : ELECTION      P6 OK, P7 silent          -> P5 stops
P6 -> P7         : ELECTION      silent                    -> P6 WINS
P6 -> everyone   : COORDINATOR(6)
```
**Why "Bully"?** The biggest-numbered alive process always takes over, pushing the smaller ones aside.

**In the code:**
- `for q = p+1 .. n` → ELECTION to every higher process; `if (alive[q])` → it replies OK, `gotOk = true`.
- `if (!gotOk)` → I win → print COORDINATOR to every alive process.
- else → call `bully(q)` for the next higher alive process (its own election). In reality ALL who replied
  start elections in parallel; we follow one chain, the result is identical (highest alive wins).
  This is **recursion**: `bully(4)` calls `bully(5)` calls `bully(6)`, which wins.

**Messages:** worst case O(n²) (the lowest process starts, everyone starts an election).

## RING algorithm – "pass a list around the circle"
Processes are arranged in a **logical ring**: each one only knows its **successor** (the next one), 1→2→…→n→1.

1. P notices the crash, makes an **ELECTION message containing a list = [P]**, sends it to its successor.
2. Each process **adds its own ID** to the list and passes it on.
   If the successor is dead, **skip it** and send to the next alive one.
3. When the message **comes back to P** (P sees its own message), the list contains every alive process.
4. P picks the **highest ID in the list** = new coordinator.
5. P sends a second message, **COORDINATOR(winner)**, once around the ring so everyone knows.

**Traced (same example):**
```
P4 -> P5 [4] -> P6 [4,5] -> (P7 dead, skip) -> P1 [4,5,6] -> P2 [4,5,6,1] -> P3 [...,2] -> back to P4 [4,5,6,1,2,3]
max = 6 -> COORDINATOR(6) goes once around the ring
```
**Messages:** 2 trips around the ring ≈ 2n (fewer than Bully's worst case).

**In the code:**
- `nextAlive(p)`: `q = q % n + 1` moves to the next process and wraps around: 7 % 7 + 1 = 1. Loop until alive;
  prints "skipped" for dead ones.
- `ids` (ArrayList) = the ELECTION message. `ids.add(next)` = "each process adds its ID".
- `if (next == start) break` = message came back to the initiator.
- `Collections.max(ids)` = highest ID. (Loop version if you forget it:
  `int leader = ids.get(0); for (int x : ids) if (x > leader) leader = x;`)
- The `do … while (cur != start)` loop = the COORDINATOR message going once around.

## Writing it from scratch (order)
1. `static int n; static boolean[] alive;` and `main` reading n, crashed, initiator; mark `alive[crashed] = false`.
2. `bully(p)`: loop higher, gotOk flag, win or recurse.
3. `nextAlive(p)` with `q % n + 1`.
4. `ring(start)`: list, loop until back to start, max, announce loop.

## Bully vs Ring
| | Bully | Ring |
|---|---|---|
| Topology | anyone can message anyone | logical ring, only to successor |
| Messages sent to | all higher processes | next alive process only |
| Message content | just "ELECTION" | growing list of alive IDs |
| Messages (worst) | O(n²) | ≈ 2n |
| Winner | highest alive ID | highest alive ID |

## Viva answers (manual FAQ)
1. **Coordinator & responsibilities:** a process chosen to organise the others: assign tasks, grant access to shared
   resources (mutual exclusion), sync clocks (Berkeley master), keep shared state/ordering, detect failures.
2. **Need for election:** the coordinator can crash; the system can't stop; processes must pick a new one
   automatically and **all agree** on the same one (no two leaders).
3. **Centralized vs decentralized algorithm:** centralized = one process makes the decisions (simple, but a single point
   of failure and bottleneck). Decentralized = decision shared among all processes, no single boss
   (fault tolerant, more messages). Election algorithms are decentralized: they produce the centralized coordinator.
4. **Working of Ring & Bully:** see the two traced examples above (draw them).
5. **Token:** a special message that circulates around a ring; only the process **holding the token** may do something
   (e.g. enter the critical section). In the Ring election the ELECTION message travels around like a token.
   (Token Ring mutual exclusion is the classic example.)
6. **Why "Bully"?** The highest-numbered alive process always wins and takes over, even pushing out a lower
   coordinator when it recovers.

**Other likely questions**
- *What if two processes start an election at the same time?* Bully: both chains end at the same highest process.
  Ring: two messages go around; both produce the same max, same winner (extra messages, same result).
- *How does a process detect failure?* Timeout: no reply to a request within a time limit.
- *What happens when P7 recovers?* Bully: P7 starts an election, gets no OK, announces itself coordinator.

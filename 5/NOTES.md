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

---

## Detailed walkthrough (for beginners, in execution order)

This program is a **single program with no networking and no threads**. It's a **simulation**: the "processes" are just numbers, and the "messages" are just printed lines. So the side-by-side columns below show **what the code does** next to **what it represents in a real distributed system**.

### Part A: The basics you need first

#### 1. The problem: who's in charge?

In a distributed system, many algorithms need **one process to be the boss**, called the **coordinator** or **leader**. For example:
- the master that syncs everyone's clocks (Practical 4's Berkeley algorithm)
- the process that decides who may use a shared file
- the process that hands out work

**What if the coordinator crashes?** There's no higher boss to appoint a new one. The remaining processes must **agree among themselves** on a new coordinator, and they must all agree on the *same* one, so there are never two leaders. The procedure they follow is an **election algorithm**.

**A process** here just means one running program, such as one computer in the system. We call them P1, P2, … P7.

#### 2. The rules both algorithms follow

- Every process has a **unique number** (ID).
- **A higher number means higher priority**, so **the highest alive process should win**.
- A process notices that the coordinator has died when it stops replying (a **timeout**).

This practical implements two different election algorithms, and both give the same answer.

#### 3. Bully algorithm: "the biggest alive one wins"

Here, **any process can send a message directly to any other**.

When P notices the coordinator is dead:
1. P sends **ELECTION** to every process with a **higher** number.
2. If **anyone replies OK** ("I'm alive and bigger than you, I'll take over"), P **steps back**, and the bigger process runs its own election.
3. If **nobody replies**, nobody bigger is alive, so **P wins** and tells everyone: **COORDINATOR(P)**.

**Analogy:** in a classroom, the teacher leaves and someone has to take charge. You shout "Is anyone senior to me here?" If a senior answers, they take over and ask the same question. The most senior person present eventually hears no answer and takes charge. The biggest one always pushes the smaller ones aside, which is why it's called **Bully**.

#### 4. Ring algorithm: "pass a list around the circle"

Here, processes sit in a **circle** (a logical ring), and each one can only talk to the **next** one: 1→2→3→…→7→1.

1. P starts a message containing a list: `[P]`. It passes it to the next process.
2. Each process **adds its own number** to the list and passes it on. A dead process is **skipped**.
3. When the message **comes back to P**, the list contains every alive process.
4. P picks the **highest number in the list** as the new coordinator.
5. P sends a second message, **COORDINATOR(winner)**, once around the ring so everyone learns the result.

**Analogy:** an attendance sheet passed around a circle of desks. Everyone signs it, and when it returns to the person who started it, they read off the most senior name.

#### 5. The new Java pieces you'll see

| Code | Meaning |
|---|---|
| `static int n;` written at the top of the class (outside any method) | A **shared variable** that every method in the file can see. `n` is the number of processes. |
| `boolean[] alive` | An array of **true/false** values. `alive[3] = false` means "P3 has crashed." |
| `new boolean[n + 1]` | Makes an array of size n+1, so the indexes are 0…n. Index **0 is left unused** so that `alive[1]` can mean P1, `alive[7]` P7, and so on. |
| `if (...) { } else { }` | "If this is true, do the first part; otherwise do the second." |
| `return;` | Exits the method immediately. |
| **Recursion** | A method calling **itself**. Here `bully(4)` calls `bully(5)`, which calls `bully(6)`. |
| `%` (modulo) | The remainder after division: `7 % 7 = 0`, `5 % 7 = 5`. It is used to wrap around the circle. |
| `while (true) { ... break; }` | Loop forever until a `break` jumps out. |
| `do { ... } while (condition);` | Like `while`, but the body always runs **at least once** before the condition is checked. |
| `Collections.max(list)` | Gives the largest number in a list. |

---

### Part B: The execution, step by step

**Example input:** 7 processes, **P7** (the old coordinator) has crashed, and **P4** detects it.

#### Step 1: Read the input and set up the processes

| Code | Real-world meaning |
|---|---|
| `java Election` runs `main` | |
| Prints `Number of processes: ` and you type `7`, so `n = 7` | The system has P1…P7. |
| `alive = new boolean[8]` (all values start as `false`) | |
| `for i = 1..7: alive[i] = true` | Everyone is running. |
| Prints `Crashed process (old coordinator): ` and you type `7`, so `alive[7] = false` | P7, the boss, has crashed. |
| Prints `Process that detects the crash: ` and you type `4`, so `start = 4` | P4 tried to talk to P7, got no reply, and timed out. |

The state is now:
```
index:  0     1    2    3    4    5    6    7
alive:  -    ✔    ✔    ✔    ✔    ✔    ✔    ✘
```

Then `main` prints `===== BULLY ALGORITHM =====` and calls `bully(4)`.

---

### Part B1: The Bully algorithm

#### Step 2: `bully(4)`, so P4 holds an election

| Code | Printed | Real-world meaning |
|---|---|---|
| prints | `P4 starts an election` | |
| `gotOk = false` | | "No one has answered me yet." |
| Loop `q = 5`: `alive[5]` is true, so `gotOk = true` | `P4 -> P5 : ELECTION    P5 -> P4 : OK` | P5: "I'm bigger and alive, back off." |
| `q = 6`: alive, so `gotOk = true` | `P4 -> P6 : ELECTION    P6 -> P4 : OK` | P6 says the same. |
| `q = 7`: `alive[7]` is false | `P4 -> P7 : ELECTION    (P7 is down, no reply)` | Silence. |
| `if (!gotOk)` is false, because P4 *did* get an OK, so P4 does not win | | P4 steps back. |
| Second loop: find the first alive `q` above 4, which is 5, then call **`bully(5)`** | | A bigger process takes over the election. |

**About `!gotOk`:** `!` means "not," so `!gotOk` reads as "didn't get any OK."

#### Step 3: `bully(5)`, so P5 holds its own election

| Code | Printed |
|---|---|
| | `P5 starts an election` |
| `q = 6`: alive, so `gotOk = true` | `P5 -> P6 : ELECTION    P6 -> P5 : OK` |
| `q = 7`: dead | `P5 -> P7 : ELECTION    (P7 is down, no reply)` |
| P5 got an OK, so it steps back and calls **`bully(6)`** | |

#### Step 4: `bully(6)`, so P6 holds its election and wins

| Code | Printed | Real-world meaning |
|---|---|---|
| | `P6 starts an election` | |
| `q = 7`: dead | `P6 -> P7 : ELECTION    (P7 is down, no reply)` | Nobody above P6 is alive. |
| `if (!gotOk)` is **true** | `P6 got no OK, so P6 is the new COORDINATOR` | P6 wins. |
| Loop `q = 1..7`, skipping dead processes and P6 itself (`q != p`) | `P6 -> P1 : COORDINATOR(6)` … up to `P6 -> P5 : COORDINATOR(6)` | P6 tells everyone it's the new boss. |
| `return` | | |

`&&` means "and," and `!=` means "not equal." So `alive[q] && q != p` reads as "q is alive **and** q is not me."

#### Step 5: The recursion unwinds

```
bully(4)
  └─ calls bully(5)
        └─ calls bully(6)  → wins, returns
     bully(5) returns
bully(4) returns  → back in main
```

Each call was paused waiting for the one it made, and they finish in reverse order. Nothing more is printed.

**About the simulation:** in a real system, **both** P5 and P6 would reply OK to P4 and **both** would start elections in parallel. The code follows only one chain (the next alive process upward) to keep it simple. The result is the same, because the highest alive process always wins.

The Bully message flow:
```
P4 ──ELECTION──▶ P5 (OK), P6 (OK), P7 (✘)      → P4 backs off
P5 ──ELECTION──▶ P6 (OK), P7 (✘)               → P5 backs off
P6 ──ELECTION──▶ P7 (✘)                        → nobody bigger, P6 WINS
P6 ──COORDINATOR(6)──▶ P1, P2, P3, P4, P5
```

---

### Part B2: The Ring algorithm

`main` prints `===== RING ALGORITHM =====` and calls `ring(4)`.

First, the helper method that represents "pass to the next process in the circle."

#### The helper: `nextAlive(p)`

```java
q = q % n + 1;     // move to the next position, wrapping 7 → 1
if (alive[q]) return q;
```

The `% n + 1` trick, with n = 7:

| current q | `q % 7` | `+ 1` | next |
|---|---|---|---|
| 4 | 4 | 5 | P5 |
| 6 | 6 | 7 | P7 |
| 7 | **0** | **1** | **P1** (wrapped around the circle) |

If the next process is dead, it prints `(P7 is down, skipped)` and keeps moving until it finds an alive one.

```
        P1
    P7✘     P2
  P6           P3
    P5      P4
```

#### Step 6: The ELECTION message goes around the ring

| Code | `cur` | `next` | Printed | `ids` after |
|---|---|---|---|---|
| prints start, `ids = [4]`, `cur = 4` | 4 | | `P4 starts an election` | `[4]` |
| `next = nextAlive(4)` = 5. Not equal to start, so add 5 | 4 | 5 | `P4 -> P5 : ELECTION [4]` | `[4, 5]` |
| `nextAlive(5)` = 6, add 6 | 5 | 6 | `P5 -> P6 : ELECTION [4, 5]` | `[4, 5, 6]` |
| `nextAlive(6)`: 7 is dead, so it skips to 1. Add 1 | 6 | 1 | `(P7 is down, skipped)` then `P6 -> P1 : ELECTION [4, 5, 6]` | `[4, 5, 6, 1]` |
| `nextAlive(1)` = 2, add 2 | 1 | 2 | `P1 -> P2 : ELECTION [4, 5, 6, 1]` | `[4, 5, 6, 1, 2]` |
| `nextAlive(2)` = 3, add 3 | 2 | 3 | `P2 -> P3 : ELECTION [4, 5, 6, 1, 2]` | `[4, 5, 6, 1, 2, 3]` |
| `nextAlive(3)` = 4, which **equals start**, so **`break`** | 3 | 4 | `P3 -> P4 : ELECTION [4, 5, 6, 1, 2, 3]` | *(unchanged)* |

The order matters: the line is **printed first** (showing the list being sent), and **then** the receiver adds its own number. That matches the real-world flow, where P5 receives `[4]` and then signs it to make `[4, 5]`.

The ArrayList `ids` *is* the ELECTION message, and `ids.add(next)` represents "the receiving process adds its signature."

#### Step 7: P4 picks the winner

| Code | Printed |
|---|---|
| `leader = Collections.max([4, 5, 6, 1, 2, 3])` = **6** | `Message is back at P4. Highest ID in [4, 5, 6, 1, 2, 3] = 6` |

#### Step 8: The COORDINATOR message goes around once

| Code | Printed |
|---|---|
| `cur = 4`, `do {`: next = 5 | `P4 -> P5 : COORDINATOR(6)` |
| cur = 5, next = 6 | `P5 -> P6 : COORDINATOR(6)` |
| cur = 6, next = 1 (P7 skipped) | `(P7 is down, skipped)` then `P6 -> P1 : COORDINATOR(6)` |
| cur = 1 → 2 → 3 | `P1 -> P2 …`, `P2 -> P3 …` |
| cur = 3, next = 4, so `cur = 4`, which equals start, and the loop ends | `P3 -> P4 : COORDINATOR(6)` |
| | `P6 is the new COORDINATOR` |

**Why `do … while` here?** The loop starts with `cur == start` (4). A plain `while (cur != start)` would check the condition first, see 4 == 4, and **never run at all**. `do … while` runs the body once first, then checks.

The program ends.

---

### The whole thing on one page

```
INPUT: n=7, P7 crashed, P4 detects
                         │
        ┌────────────────┴────────────────┐
     BULLY (anyone → anyone)          RING (only → next)
        │                                 │
 P4 → P5,P6,P7  OKs from 5,6       P4 →P5 →P6 →(skip P7)→P1 →P2 →P3 →P4
 P5 → P6,P7     OK from 6           list grows: [4]→[4,5]→…→[4,5,6,1,2,3]
 P6 → P7        silence             max = 6
 P6 wins → tells everyone           COORDINATOR(6) goes around once
        │                                 │
        └──────── both elect P6 ──────────┘
```

### Mapping code to the algorithm

| Algorithm step | Code in [Election.java](Election.java) |
|---|---|
| Who's alive or crashed | `alive[]` array |
| Bully: send ELECTION to higher processes | `for (q = p + 1; q <= n; q++)` |
| Bully: received an OK | `if (alive[q]) gotOk = true` |
| Bully: nobody answered, I win | `if (!gotOk)` → print COORDINATOR to all |
| Bully: a bigger process takes over | `bully(q)` (recursion) |
| Ring: pass to the next process, skipping dead ones | `nextAlive()` with `q % n + 1` |
| Ring: the ELECTION message | `ArrayList<Integer> ids` |
| Ring: message returned to the starter | `if (next == start) break` |
| Ring: pick the winner | `Collections.max(ids)` |
| Ring: announce it | the `do … while` loop |

**Compared with the earlier practicals:** Practicals 1 and 2 were about **how** processes communicate (sockets, RMI). Practical 5 is about **what they say to each other** to reach an agreement, so the communication is just printed lines to keep the focus on the algorithm.

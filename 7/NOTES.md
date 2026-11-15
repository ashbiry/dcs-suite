# Practical 7 – Termination Detection (Dijkstra–Scholten algorithm)

**Aim:** implement termination detection for a distributed computation.
Single-thread simulation of the Dijkstra–Scholten algorithm (the one the manual describes). Deterministic output.

## Run
```
javac TerminationDetection.java
java TerminationDetection
```

## The problem, from zero
A distributed computation = several processes doing work and sending each other messages ("do this part").
Each process at any moment is:
- **active**: working (and may send messages), or
- **passive** (idle): finished its local work. A passive process becomes active again **only if it receives a message**.

**The computation has terminated when: ALL processes are passive AND NO message is in transit.**

Why is that hard? Nobody can see the whole system: no shared memory, no global clock.
Trap: "everyone looks passive, so we're done" is **wrong**. Example:
```
P1 sends WORK to P2, then P1 becomes passive.
P2 is passive (message hasn't arrived yet).
Snapshot: P1 passive, P2 passive  ->  "terminated"?  NO! the message is still travelling;
when it arrives P2 becomes active again.
```
So we need an algorithm that also accounts for messages in flight. (FAQ 2)

Two properties the detector must have:
- **Safety:** never announce termination when it hasn't really happened (no false alarm).
- **Liveness:** if it really terminates, announce it within finite time (don't wait forever).

## Dijkstra–Scholten idea: "every job must be reported back as finished"
Think of a manager (root P0) handing tasks to people, who may hand sub-tasks to others.
Rule: **you may only tell your boss "done" when everyone you gave work to has told you "done".**
When the manager has heard "done" for every task she gave out, the whole project is finished.

It works on a **diffusing computation** (FAQ 3): at the start only the **root** is active; everyone else
is passive and becomes active only when work "diffuses" (spreads) to them through messages.

### Two variables per process
- **parent**: the process that first gave me work (engaged me). −1 = not engaged.
- **deficit**: how many WORK messages I have sent that are **not ACKed yet** ("jobs still out").

### Two message types
- **WORK**: the actual computation message.
- **ACK**: "your WORK message is fully finished". **Every WORK gets exactly one ACK.**

### The rules
1. Send WORK → `deficit++`.
2. Receive WORK while **not engaged** → become engaged, sender becomes **parent**. Do NOT ACK yet
   (this ACK is kept pending; it means "I'm done", sent later).
3. Receive WORK while **already engaged** (or I'm the root) → ACK the sender **immediately**
   (I already have a parent; this sender doesn't need to wait for me).
4. Receive ACK → `deficit--`.
5. Non-root: **passive and deficit == 0** → send the pending ACK to parent, become **disengaged** (leave the tree).
6. Root: **passive and deficit == 0** → **TERMINATION DETECTED**.

The engaged processes form a **tree** under the root (parent links). Work makes the tree grow;
ACKs make it shrink **from the leaves upward**. When the root's deficit returns to 0, the tree is empty:
nobody is active and no message is in transit (every WORK has its ACK back).

Cost: **one ACK per WORK message** → M WORK messages = M extra ACK messages.

## Traced: our example (plan: P0→P1,P2 | P1→P3 | P2→P3 | P3→P4)
```
P0 sends WORK to P1, P2                     deficit P0 = 2
P1 gets WORK: engaged (parent P0), sends WORK to P3       deficit P1 = 1
P2 gets WORK: engaged (parent P0), sends WORK to P3       deficit P2 = 1
P3 gets WORK from P1: engaged (parent P1), sends WORK to P4  deficit P3 = 1
P3 gets WORK from P2: ALREADY engaged -> ACK P2 at once      (rule 3)

Tree now:        P0
               /    \
             P1      P2
             |
             P3
             |
             P4

P4: no work to send, deficit 0 -> ACK to P3, leaves tree        (leaf first)
P2: gets ACK, deficit 0 -> ACK to P0, leaves tree
P3: gets ACK from P4, deficit 0 -> ACK to P1, leaves tree
P0: gets ACK from P2, deficit 1 (still waiting for P1's branch)
P1: gets ACK from P3, deficit 0 -> ACK to P0, leaves tree
P0: gets ACK from P1, deficit 0 -> TERMINATION DETECTED
```
Notice P0 did **not** declare termination after P2's ACK: P1's branch was still working. That's **safety**.

## Code ↔ rules
| Code | Meaning |
|---|---|
| `plan[][]` | who sends WORK to whom (the computation itself; fixed so output is the same every run) |
| `parent[]`, `deficit[]` | the two variables of every process |
| `LinkedList<Msg> network` | messages **in transit**; `add` = send, `removeFirst` = deliver |
| `sendWork`: `deficit[from]++` | rule 1 |
| `if (p != 0 && parent[p] == -1)` → set parent | rule 2 (engage, ACK pending) |
| `else sendAck(p, m.from)` | rule 3 (already engaged, ACK now) |
| `deficit[p]--` on ACK | rule 4 |
| `check(p)` | rules 5 and 6: if deficit 0 → root announces, others ACK parent and disengage |
| `taskDone[]` | each process does its local task once (keeps the computation finite) |

Main loop = "the network delivers messages one by one until nothing is left in transit".
After handling each message the process is passive again, so we call `check(p)`.

## Writing it from scratch (order)
1. `Msg` class (type, from, to), constants WORK/ACK.
2. Arrays: `plan`, `parent` (fill −1), `deficit`, `taskDone`; `LinkedList<Msg> network`.
3. `sendWork` (deficit++ and add), `sendAck` (add).
4. `check(p)`.
5. `main`: root sends its plan; `while (!network.isEmpty())` deliver, apply rules 2/3/4, `check(p)`.

## If the examiner asks "where is the distribution/threads?"
Our simulation represents each process by its own variables and lets them communicate **only through messages**
in the network queue, which is what the algorithm needs. The manual's version makes each process a Thread with its
own inbox (`BlockingQueue`); the rules are exactly the same, only message delivery is concurrent.

## Viva answers (manual FAQ)
1. **Termination of a distributed computation:** the state where **every process is passive and no message is in
   transit**, so nothing can ever become active again.
2. **Why is "all processes passive" not enough?** A message may still be travelling; when it arrives it re-activates
   its receiver. Since no process sees the whole system at one instant, a snapshot of passive processes can miss
   in-flight messages. Termination needs both: all passive AND channels empty.
3. **Diffusing computation:** a computation that starts with only ONE active process (the root/initiator); all others
   are passive and become active only by receiving messages, so the work spreads (diffuses) outward from the root.

**Other likely questions**
- *Safety and liveness?* Safety = no false termination. Liveness = real termination is eventually announced.
- *Why does the root declare it, not any process?* Only the root has the whole tree's result: its deficit hits 0 only
  after every branch has reported back.
- *Message overhead?* One ACK per WORK message.
- *Other termination detection algorithms:* Dijkstra–Feijen–van Gasteren (token passed around a ring, processes coloured
  black/white), Huang's weight-throwing (root has weight 1, splits it with each message, termination when it all
  returns), Safra's algorithm (token-based, handles asynchronous messages).
- *Why is engaging the second WORK sender's ACK immediate?* The process already has a parent that will learn when it
  finishes; the second sender only needs to know its message was received, not to wait for the whole subtree.

---

## Detailed walkthrough (for beginners, in execution order)

Like Practical 5, this is **one program, no networking, no threads**, just a **simulation**. Processes are numbers, and messages are small objects placed in a queue that stands in for the network. The side-by-side columns below show **what the code does**, **what's currently in the network**, and **what it means in a real system**.

### Part A: The basics you need first

#### 1. The problem: how do you know everyone is finished?

Picture a big job split across several processes (P0 to P4). They send each other messages like "here, do this part." At any moment, each process is either:
- **active**: working, and possibly sending messages to others, or
- **passive** (idle): done with its own work. A passive process becomes active again **only if a message arrives** for it.

The **whole computation is finished (terminated)** only when:

> **every process is passive, AND no message is still travelling through the network.**

**Why is this hard?** No process can see the whole system. There's no shared memory and no global clock. The obvious check, "everyone looks idle, so we're done," is **wrong**:

```
P1 sends WORK to P2, then P1 goes idle.
P2 is idle too (the message hasn't arrived yet).
Snapshot: P1 idle, P2 idle → "done"?  NO!
The message is still travelling. When it arrives, P2 becomes busy again.
```

So we need an algorithm that also accounts for **messages in flight**. That's **termination detection**.

A correct detector must have two properties:
- **Safety**: it never announces "done" too early (no false alarm).
- **Liveness**: if the work really is done, it eventually announces it (it doesn't wait forever).

#### 2. Dijkstra–Scholten: "report back when you're done"

**Analogy:** a manager (P0, the **root**) hands tasks to team members. They can hand sub-tasks to others. There is one rule:

> **You may only tell your boss "done" once everyone you gave work to has told *you* "done".**

Reports flow back up the chain. When the manager has heard "done" for every task they handed out, the whole project is finished.

This works for a **diffusing computation**: at the start **only the root is active**, and everyone else becomes active only when work spreads (diffuses) to them through messages.

#### 3. Two message types

| Message | Meaning |
|---|---|
| **WORK** | "Here's some work for you." This is the actual computation. |
| **ACK** (acknowledgement) | "The work you sent me is completely finished." **Every WORK eventually gets exactly one ACK back.** |

#### 4. Two variables each process keeps

| Variable | Meaning | Analogy |
|---|---|---|
| **parent** | The process that **first** gave me work. `-1` means "nobody, I'm not engaged." | "My boss for this project" |
| **deficit** | How many WORK messages I've sent that **haven't been ACKed yet** | "Jobs I handed out that haven't come back" |

**Engaged** means the process has a parent and is part of the work tree. **Disengaged** means it has left the tree.

#### 5. The six rules

1. **Send WORK**: `deficit + 1`.
2. **Receive WORK while not engaged**: become engaged and set the sender as your **parent**. **Don't ACK yet.** You'll ACK your parent later, once you're fully done.
3. **Receive WORK while already engaged** (or if you're the root): **ACK the sender immediately.** You already have a boss, so this sender doesn't need to wait for you.
4. **Receive ACK**: `deficit − 1`.
5. **Non-root, passive, and deficit = 0**: send the delayed ACK to your parent and **leave the tree** (disengage).
6. **Root, passive, and deficit = 0**: **TERMINATION DETECTED.**

The engaged processes form a **tree** hanging from the root (through the parent links). WORK makes the tree **grow**, and ACKs make it **shrink from the bottom up**. When the root's deficit is back to 0, the tree is empty: nobody is busy and every message has been answered.

#### 6. The new Java pieces you'll see

| Code | Meaning |
|---|---|
| `static final int WORK = 0, ACK = 1;` | **Constants**. `final` means they can never change. These are just readable names for the numbers 0 and 1. |
| `class Msg { int type, from, to; }` | A small box holding one message: its type (WORK or ACK), who sent it, and who it's for. It's the same idea as `Pair` in Practical 6. |
| `int[][] plan = { {1, 2}, {3}, {3}, {4}, {} };` | A **2-D array**, an array of arrays. `plan[0]` is `{1, 2}`, `plan[4]` is `{}` (empty). |
| `plan.length` | How many items there are (5), so `n = 5` and the processes are P0…P4. |
| `LinkedList<Msg> network` | A list used as a **queue** (first in, first out, like a line at a ticket counter). `add` puts a message at the back, and `removeFirst` takes the oldest one from the front. |
| `cond ? A : B` | A one-line if/else: "if cond then A, else B." For example, `m.type == WORK ? "WORK" : "ACK"`. |
| `==` vs `=` | `==` **compares** (is it equal?), while `=` **assigns** (store this value). |
| `else if` | Checks a second condition only if the first one was false. |

---

### Part B: The execution, step by step

#### The "plan" (who sends WORK to whom)

```java
plan = { {1, 2}, {3}, {3}, {4}, {} }
```

| Process | When it does its task, it sends WORK to |
|---|---|
| P0 (root) | P1 and P2 |
| P1 | P3 |
| P2 | P3 |
| P3 | P4 |
| P4 | nobody |

```
        P0
       /  \
     P1    P2
       \  /
        P3        ← P3 gets work from TWO processes (this tests rule 3)
        |
        P4
```

`taskDone[]` makes sure each process does its task **only once**, even if it receives WORK twice. Without it, the computation could go on forever.

#### Step 1: Setup

| Code | Real-world meaning |
|---|---|
| `java TerminationDetection` runs `main` | |
| `parent[i] = -1` for every process | Nobody is engaged yet. |
| `deficit[]` starts as all 0, `taskDone[]` as all false, `network` is empty | No work has been handed out and no messages are travelling. |

#### Step 2: The root starts the computation

| Code | Printed | `network` (front → back) |
|---|---|---|
| `taskDone[0] = true` | `P0 (root) starts the computation` | *(empty)* |
| `sendWork(0, 1)`: `deficit[0]++` gives 1, and the message is added to the network | `P0 sends WORK to P1   deficit[P0] = 1` | `W 0→1` |
| `sendWork(0, 2)`: `deficit[0]` becomes 2 | `P0 sends WORK to P2   deficit[P0] = 2` | `W 0→1`, `W 0→2` |

That's **rule 1**: every WORK sent increases the sender's deficit. P0 has now handed out 2 jobs that haven't come back.

#### Step 3: The main loop (the network delivering messages)

```java
while (!network.isEmpty()) {
    Msg m = network.removeFirst();   // deliver the oldest message
    ... apply the rules ...
    check(p);                        // the receiver is now idle again; can it report "done"?
}
```

This loop **is** the network: it delivers messages one at a time, oldest first, until nothing is left in transit. After handling a message, the receiver has finished reacting and is passive again, so `check(p)` asks whether it can report "done" (rules 5 and 6).

The `(in transit: N)` in each printed line is how many messages are **still waiting** after this one was taken out.

---

##### Delivery 1: P1 receives WORK from P0

| Code | Printed | `network` after |
|---|---|---|
| `removeFirst()` gives `W 0→1`, so `p = 1` | `P1 receives WORK from P0   (in transit: 1)` | `W 0→2` |
| `p != 0 && parent[1] == -1` is true, so **rule 2**: `parent[1] = 0` | `P1 engaged, parent = P0 (ACK kept pending)` | |
| `taskDone[1]` is false, so P1 does its task: `sendWork(1, 3)` | `P1 sends WORK to P3   deficit[P1] = 1` | `W 0→2`, `W 1→3` |
| `check(1)`: deficit is 1, not 0, so `return` | | |

P1 joined the tree under P0. It **didn't** ACK P0, because it owes P0 a "done" only after its own sub-job (sent to P3) comes back.

##### Delivery 2: P2 receives WORK from P0

The same thing happens:

| Printed | `network` after |
|---|---|
| `P2 receives WORK from P0   (in transit: 1)` | `W 1→3` |
| `P2 engaged, parent = P0 (ACK kept pending)` | |
| `P2 sends WORK to P3   deficit[P2] = 1` | `W 1→3`, `W 2→3` |

`check(2)`: deficit is 1, so it is not done yet.

##### Delivery 3: P3 receives WORK from P1

| Printed | `network` after |
|---|---|
| `P3 receives WORK from P1   (in transit: 1)` | `W 2→3` |
| `P3 engaged, parent = P1 (ACK kept pending)` (rule 2) | |
| `P3 sends WORK to P4   deficit[P3] = 1` | `W 2→3`, `W 3→4` |

##### Delivery 4: P3 receives WORK from P2 a second time (rule 3)

| Code | Printed | `network` after |
|---|---|---|
| `removeFirst()` gives `W 2→3` | `P3 receives WORK from P2   (in transit: 1)` | `W 3→4` |
| `parent[3]` is 1, not -1, so P3 is **already engaged** and the `else` branch runs | `P3 already engaged, ACKs at once` | |
| `sendAck(3, 2)` | `P3 sends ACK to P2` | `W 3→4`, `A 3→2` |
| `taskDone[3]` is already true, so the task is not repeated | | |
| `check(3)`: deficit is 1 (still waiting on P4), so `return` | | |

**Why ACK P2 right away?** P3 already has a boss (P1), and P1 will be told when P3 finishes. P2 doesn't need to wait for P3's whole sub-tree; it only needs to know its message was handled. This keeps the tree a proper **tree**, where every process has exactly one parent.

The tree at its largest:
```
         P0          deficit[P0] = 2
        /  \
      P1    P2       deficit[P1] = 1, deficit[P2] = 1
      |
      P3             deficit[P3] = 1
      |
      P4             (WORK on its way)
```

##### Delivery 5: P4 receives WORK from P3, and the first leaf finishes

| Code | Printed | `network` after |
|---|---|---|
| `W 3→4` delivered | `P4 receives WORK from P3   (in transit: 1)` | `A 3→2` |
| Rule 2: `parent[4] = 3` | `P4 engaged, parent = P3 (ACK kept pending)` | |
| `taskDone[4]` is false, so it does the task, but `plan[4]` is `{}`, so it **sends nothing** | | |
| `check(4)`: deficit is **0**, `p` is not 0, and `parent[4]` is not -1, so **rule 5** applies | `P4 is passive and deficit = 0, leaves the tree:` | |
| `sendAck(4, 3)`, then `parent[4] = -1` | `P4 sends ACK to P3` | `A 3→2`, `A 4→3` |

P4 is a **leaf** (it handed out no work), so it's done immediately and reports to its parent. **The tree now starts shrinking from the bottom.**

##### Delivery 6: P2 receives the ACK from P3

| Code | Printed | `network` after |
|---|---|---|
| `A 3→2` delivered | `P2 receives ACK from P3   (in transit: 1)` | `A 4→3` |
| **Rule 4**: `deficit[2]--` gives 0 | `deficit[P2] = 0` | |
| `check(2)`: rule 5 applies | `P2 is passive and deficit = 0, leaves the tree:` | |
| `sendAck(2, 0)`, then `parent[2] = -1` | `P2 sends ACK to P0` | `A 4→3`, `A 2→0` |

##### Delivery 7: P3 receives the ACK from P4

| Printed | `network` after |
|---|---|
| `P3 receives ACK from P4   (in transit: 1)` | `A 2→0` |
| `deficit[P3] = 0` | |
| `P3 is passive and deficit = 0, leaves the tree:` | |
| `P3 sends ACK to P1` | `A 2→0`, `A 3→1` |

##### Delivery 8: P0 receives the ACK from P2, which is not enough yet

| Code | Printed | `network` after |
|---|---|---|
| `A 2→0` delivered | `P0 receives ACK from P2   (in transit: 1)` | `A 3→1` |
| `deficit[0]--` gives **1** | `deficit[P0] = 1` | |
| `check(0)`: deficit is 1, not 0, so `return` with **no announcement** | | |

This is **safety** in action. Half of the work has reported back, but P1's branch hasn't. P0 is idle and P2 is done, so a naive "everyone looks idle" check could be fooled, but **P0's deficit says one job is still out**.

##### Delivery 9: P1 receives the ACK from P3

| Printed | `network` after |
|---|---|
| `P1 receives ACK from P3   (in transit: 0)` | *(empty)* |
| `deficit[P1] = 0` | |
| `P1 is passive and deficit = 0, leaves the tree:` | |
| `P1 sends ACK to P0` | `A 1→0` |

##### Delivery 10: P0 receives the ACK from P1, and termination is detected

| Code | Printed | `network` after |
|---|---|---|
| `A 1→0` delivered | `P0 receives ACK from P1   (in transit: 0)` | *(empty)* |
| `deficit[0]--` gives **0** | `deficit[P0] = 0` | |
| `check(0)`: deficit is 0 and `p == 0`, so **rule 6** applies | `P0 is passive and deficit = 0  ==>  TERMINATION DETECTED` | |

#### Step 4: The loop ends

| Code | Printed |
|---|---|
| `network.isEmpty()` is true, so the `while` loop stops | `Network empty, all processes passive. Computation finished.` |

---

### Summary

#### How the tree shrinks

```
After Delivery 5        After 6 and 7          After 9               After 10
     P0                    P0                    P0                    (empty)
    /  \                   |                                         TERMINATION
  P1    P2                P1                                          DETECTED
  |                       
  P3                   
  (P4 left)            (P2, P3 left)          (P1 left)
```

#### The deficit counters over time

| After delivery | P0 | P1 | P2 | P3 | P4 |
|---|---|---|---|---|---|
| Start (P0 sends 2) | 2 | 0 | 0 | 0 | 0 |
| 1, 2, 3 (work spreads) | 2 | 1 | 1 | 1 | 0 |
| 6 (ACK reaches P2) | 2 | 1 | **0** | 1 | 0 |
| 7 (ACK reaches P3) | 2 | 1 | 0 | **0** | 0 |
| 8 (ACK reaches P0) | **1** | 1 | 0 | 0 | 0 |
| 9 (ACK reaches P1) | 1 | **0** | 0 | 0 | 0 |
| 10 (ACK reaches P0) | **0** → done | 0 | 0 | 0 | 0 |

#### Mapping code to the rules

| Rule | Code in [TerminationDetection.java](TerminationDetection.java) |
|---|---|
| Messages in transit | `LinkedList<Msg> network` (`add` sends, `removeFirst` delivers) |
| Rule 1: send WORK, deficit + 1 | `sendWork()`: `deficit[from]++` |
| Rule 2: not engaged, adopt a parent and delay the ACK | `if (p != 0 && parent[p] == -1) parent[p] = m.from;` |
| Rule 3: already engaged (or root), ACK now | `else sendAck(p, m.from);` |
| Rule 4: receive ACK, deficit − 1 | `deficit[p]--;` |
| Rule 5: non-root done, ACK parent and leave | `check()`: `sendAck(p, parent[p]); parent[p] = -1;` |
| Rule 6: root done, termination | `check()`: `if (p == 0)` → print TERMINATION DETECTED |
| Do each task only once | `taskDone[]` |

**Cost:** one ACK for every WORK message. Here there were 5 WORK messages and 5 ACKs.

**Compared with the earlier practicals:** Practical 5 picked a **leader**. Practical 7 answers a different question every distributed system faces: **"Is everyone finished?"** The answer comes from making every piece of handed-out work report back up the chain to the root.

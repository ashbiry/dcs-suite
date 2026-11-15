# Practical 4 – Berkeley Algorithm (clock synchronization)

**Aim:** implement Berkeley's algorithm to synchronize the clocks of n nodes.
Single-file simulation; no networking needed (class rep did the same).

## Run
```
javac Berkeley.java
java Berkeley
```
Input: `3`, then `03:00`, `03:25`, `02:50` → everyone ends at **03:05** (textbook example).

## The problem
Every computer has its own quartz clock. Quartz clocks run at slightly different speeds, so over time they
drift apart (**clock drift**). The difference between two clocks at a moment is the **clock skew**.
In a distributed system, wrong clocks break things: file timestamps (`make` rebuilding wrong files),
log ordering, "which transaction happened first", certificate/token expiry, deadlines.

## Berkeley's idea
No machine has an exact time source (no UTC/GPS receiver). So nobody is "right"; instead the nodes
**agree on the average** of everyone's time. Accuracy with each other (internal sync) matters, not with the real world.

## Algorithm, traced with the example (master 03:00, nodes 03:25 and 02:50)
1. **Pick a master** (time daemon), usually by an election algorithm (Practical 5). Here: node 0.
2. **Master polls every node for its time** and computes `diff = node − master`:
   node0 = 0, node1 = +25, node2 = −10.
   (In a real network the master uses Cristian's method to correct for message delay:
   node time + round-trip-time / 2.)
3. **Average the differences, master included**: (0 + 25 − 10) / 3 = **+5**.
   Agreed time = master + avg = 03:00 + 5 = **03:05**.
   (Improvement: ignore outliers, e.g. a node whose clock is hours off, before averaging.)
4. **Master sends each node an ADJUSTMENT, not the time**: node0 **+5**, node1 **−20**, node2 **+15**.
   Why adjustment? Sending "it is 03:05" would already be stale when it arrives (network delay).
   "Move yourself +15" is still correct whenever it arrives.
5. Everyone is now at 03:05. Repeat periodically because clocks keep drifting.

## Code ↔ algorithm
| Code | Algorithm step |
|---|---|
| `clock[]` read as HH:MM, stored as minutes | each node's local time (minutes make subtraction easy) |
| `diff[i] = clock[i] - clock[0]` | step 2: master polls, computes differences |
| `avg = sum / n` | step 3: average, master's own 0 included (n, not n-1) |
| `target = clock[0] + avg` | agreed time |
| `adj = target - clock[i]` | step 4: adjustment sent to node i |

Write from scratch: read n and times → diffs → avg → adjustments. ~30 lines.

## Note on setting clocks backwards
Node1 gets −20. Jumping time backwards is dangerous (timestamps go "back in time"). Real systems
**slow the clock down** gradually until it catches up instead of jumping back.

## Viva answers (manual FAQ)
1. **Logical vs physical clock:**
   Physical clock = actual time of day (hardware quartz, e.g. 03:05). Needs synchronization (Cristian, Berkeley, NTP).
   Logical clock = just a counter that captures the ORDER of events ("happened-before"), not the real time.
   Lamport clock: each process increments a counter per event, sends it with messages, receiver sets
   `max(own, received) + 1`. Vector clocks extend this to detect concurrent events.
2. **Why synchronize?** Clocks drift. Distributed apps need a consistent notion of time for ordering events, timestamps,
   timeouts, scheduling, transactions and real-time deadlines. In real-time systems a late/early action can be a failure.
3. **Principle of Berkeley:** master polls slaves → computes differences (correcting for delay) → averages them
   (ignoring outliers) → sends each node its adjustment. Internal sync, no external time source, the average is the reference.
4. **Other algorithms:**
   - **Cristian's algorithm:** client asks a time server; sets time = server time + RTT/2. Centralized, needs an accurate server.
   - **NTP (Network Time Protocol):** hierarchical strata of servers syncing to UTC; used on the internet.
   - **Averaging (decentralized) algorithms:** every node broadcasts its time and averages what it receives.
   - **Lamport / vector clocks:** logical ordering instead of real time.
   - **RBS (Reference Broadcast Sync):** for wireless/sensor networks.

**Other likely questions**
- *Berkeley vs Cristian?* Cristian = pull time from one accurate server (external sync). Berkeley = no accurate server,
  average of all (internal sync), master pushes adjustments.
- *What if the master fails?* Run an election (Bully/Ring, Practical 5) to choose a new master.
- *Drift vs skew?* Drift = rate at which a clock deviates over time. Skew = difference between two clocks at an instant.
- *Why is master included in the average?* It's just another clock; it's not assumed correct.

---
# Viva concepts explained from zero

## A. How a computer keeps time (this is where your Digital Electronics counters come in)
- A **quartz crystal** vibrates at a fixed frequency when voltage is applied. Watches use **32,768 Hz = 2^15**.
- Feed those pulses into a **15-bit binary counter** (a divide-by-2^15 ripple counter, as in GATE).
  It overflows exactly once every 32,768 pulses = **once per second**. That overflow = 1 second tick.
- In a PC the idea is the same: a **counter register** is loaded with a value and decremented on every crystal pulse;
  at 0 it raises an **interrupt** ("clock tick", e.g. 1000 per second) and reloads. The OS adds 1 tick to a software
  variable = the time of day. **Physical clock = crystal + counter + software adding ticks.**

## B. Drift and skew
- No crystal is exactly 32,768 Hz; temperature and manufacturing shift it slightly (e.g. 32,769 Hz).
  That counter overflows a little early every second → the clock runs fast. This slow wandering = **drift**.
  Typical quartz: ~1 second off per 11–12 days.
- Two machines drift differently, so at any moment their clocks differ. That difference = **skew**.
  Drift = the cause (rate), skew = the result (gap at one moment).

## C. Why synchronize (FAQ 2)
Distributed apps judge "what happened first" from timestamps. With skew:
- Machine A (2 s ahead) writes a file at "10:00:07"; machine B (correct) edits it at "10:00:06".
  The edit now looks OLDER than the file → a build tool won't recompile, a backup skips it, a database applies updates in the wrong order.
- Timeouts, login tokens, scheduled jobs, real-time deadlines (e.g. a control system acting at an exact instant) all need agreeing clocks.

## D. Two kinds of synchronization
- **External sync:** match the real world time (UTC). Needs an accurate source. → Cristian, NTP.
- **Internal sync:** machines only need to agree with EACH OTHER, even if all are a bit off. → Berkeley.

## E. Cristian's algorithm (external, one accurate time server)
1. Client sends "what's the time?" at its own time T0.
2. Server replies with its time Ts.
3. Client receives the reply at its own time T1. Round-trip time RTT = T1 − T0.
4. The reply took about half the trip to come back, so client sets time = **Ts + RTT/2**.
Example: T0 = 10:00:00.000, reply Ts = 10:00:05.000, T1 = 10:00:00.040 → RTT = 40 ms
→ set clock to 10:00:05.020.
Berkeley's master uses this same trick when polling nodes (to estimate their real current time).
**Berkeley vs Cristian:** Cristian = everyone PULLS time from one trusted server. Berkeley = no trusted server,
the master averages everyone and PUSHES adjustments.

## F. NTP (Network Time Protocol) – what the internet actually uses
A tree of servers by accuracy level, called **strata**:
stratum 0 = atomic clocks/GPS; stratum 1 = servers wired to them; stratum 2 = sync from stratum 1; and so on.
Your PC syncs from a stratum 2/3 server using Cristian-like delay calculations, repeated and filtered.

## G. Physical vs logical clock (FAQ 1)
- **Physical clock:** real time of day (03:05). Crystal + counter. Drifts → needs syncing.
- **Logical clock:** often we don't need the real time, only the **ORDER** of events: did A happen before B?
  A logical clock is just an **integer counter in software** — same "counter" idea as in DE, but it counts
  **events** instead of crystal pulses. No crystal, no drift, nothing to synchronize.

### Lamport's logical clock (the standard example)
Each process keeps a counter C, starting at 0. Three rules:
1. Before every event (local work, send) → C = C + 1.
2. When sending a message, attach C to it.
3. On receiving a message with value t → **C = max(C, t) + 1**.
Why max? The receive must look later than the send, AND later than everything the receiver already did.

Example: P1 and P2 start at 0.
```
P1: event a        C1 = 1
P1: send m (t=2)   C1 = 2   ---- m(2) ---->
P2: event x        C2 = 1
P2: receive m      C2 = max(1, 2) + 1 = 3     (3 > 2: receive is after send ✔)
```
Case 2: P2 had already done 5 events (C2 = 5) and receives t = 2 → C2 = max(5, 2) + 1 = 6.
So: **if A happened before B, then C(A) < C(B).** (Not the other way round: a smaller number doesn't prove A caused B.)

**Vector clocks** (if asked): each process keeps one counter PER process, e.g. [2, 0, 1].
This fixes Lamport's weakness: it can tell whether two events are ordered or happened **concurrently** (independently).

## H. Other algorithms list (FAQ 4) – one line each
- Cristian: pull from a time server, add RTT/2.
- Berkeley: master averages everyone, sends adjustments.
- NTP: hierarchy (strata) of servers, used on the internet.
- Averaging/decentralized: every node broadcasts its time, each averages what it receives (no master).
- Lamport & vector clocks: logical ordering, not real time.
- RBS (Reference Broadcast Sync): sensor networks; receivers compare when they heard the same broadcast.

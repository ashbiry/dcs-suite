# Practical 6 – Word Count using MapReduce

**Aim:** count how many times each word occurs in a collection of documents, using the MapReduce model.
We implement the **simulation** (plain Java, threads), exactly what the manual calls "Program A".
Hadoop version (Program B) is explained at the end only for the viva; installing Hadoop in the exam is not worth it.

## Run
```
javac WordCount.java
java WordCount
```

## The problem MapReduce solves (from zero)
Counting words in one small file is easy: one loop + a HashMap.
Now imagine 10 TB of text (all of Wikipedia, years of logs). One computer would take days and the data
doesn't even fit on one disk. So we split the work over **hundreds of machines**.
But writing distributed code by hand is hard: sending data between machines, deciding who does what,
handling a machine that crashes halfway.

**MapReduce's deal:** you write only TWO small functions, `map` and `reduce`. The framework (Hadoop)
does all the distributed work: splitting input, running tasks on many machines, moving data, retrying failures.

## The phases, traced with our example
Input: 3 documents. `"Deer Bear River"`, `"Car Car River"`, `"Deer Car Bear"`

**1. Split** – input is cut into pieces (splits); each piece goes to one mapper.
Mapper 1 gets "Deer Bear River", Mapper 2 gets "Car Car River", Mapper 3 gets "Deer Car Bear".
These run **in parallel** (on different machines in Hadoop; different threads in our code).

**2. Map** – each mapper looks ONLY at its own piece and emits a **(key, value)** pair per word: `(word, 1)`.
It does NOT count; it just says "I saw this word once".
```
Mapper 1 -> (deer,1) (bear,1) (river,1)
Mapper 2 -> (car,1) (car,1) (river,1)
Mapper 3 -> (deer,1) (car,1) (bear,1)
```
Why not count inside the mapper? Mapper 1 can't know about the "deer" in Mapper 3's piece.
No mapper sees the whole data, so no mapper can give a final answer.

**3. Shuffle & Sort** (done by the framework, not by you) – collect pairs from ALL mappers and **group
by key**: all values of the same word go together, keys sorted.
```
bear  -> [1, 1]
car   -> [1, 1, 1]
deer  -> [1, 1]
river -> [1, 1]
```
In Hadoop, every word is sent to exactly one reducer machine, so that reducer sees ALL 1s of that word.

**4. Reduce** – for each word, add up its list.
```
reduce(car, [1,1,1]) -> (car, 3)
```
**5. Output** – bear 2, car 3, deer 2, river 2.

General form (say this in viva):
```
map    (k1, v1)       -> list(k2, v2)      (offset, "Deer Bear River") -> (deer,1),(bear,1),(river,1)
reduce (k2, list(v2)) -> list(k3, v3)      (car, [1,1,1])              -> (car,3)
```

## Code ↔ phase
| Code | Phase |
|---|---|
| `String[] splits = {...}` | input, already split, one per mapper |
| `class Mapper extends Thread`, `run()` adds `new Pair(w, 1)` | MAP, one thread per split (parallel) |
| `mappers[i].start()` loop, then `mappers[i].join()` loop | run mappers in parallel, then wait for all |
| `TreeMap` + `containsKey` / `put` / `get(...).add(1)` | SHUFFLE (group by word) & SORT (TreeMap sorts keys) |
| `reduce(word, counts)` sums the list | REDUCE |

**Shuffle code in plain words:**
```java
if (!grouped.containsKey(p.key)) {                 // never seen this word before?
    grouped.put(p.key, new ArrayList<Integer>());  // give it an empty list
}
grouped.get(p.key).add(p.value);                   // put this 1 into the word's list
```
After all pairs: `car -> [1, 1, 1]`. `TreeMap` = a HashMap that keeps keys in alphabetical order.

**`split(" ")`**: cuts the line at every space: "Deer Bear River" -> ["Deer", "Bear", "River"].
(If input had commas/full stops you'd use `split("\\W+")` = split on anything that is not a letter/digit.)

## `join()` explained in detail

### What it literally does
`t.join()` = **"the thread calling this stops and waits here until thread `t` has finished its `run()`."**
In our code `main` calls `mappers[i].join()`, so `main` pauses at that line until mapper i is done.
If mapper i has already finished, `join()` returns immediately.

### What happens without it
`start()` does NOT run the mapper and wait. It only **launches** a new thread and returns at once.
So right after the start loop, `main` is already on the next line while the mappers may still be working:
```
time ->
main:     start M1, start M2, start M3, SHUFFLE (reads outputs) ...
Mapper 1:          [deer,1 bear,1 river,1 ...... still running]
Mapper 2:                   [car,1 ...... still running]
Mapper 3:                            [... not even begun]
```
`main` would read the mappers' output lists while they are half-filled → missing words, wrong counts,
different result each run (a **race condition**: the result depends on which thread happens to be faster).

### With join
```
main:     start M1, start M2, start M3, join M1 (wait..), join M2 (wait..), join M3 (wait..), SHUFFLE
Mapper 1:          [deer,1 bear,1 river,1] done
Mapper 2:                   [car,1 car,1 river,1] done
Mapper 3:                            [deer,1 car,1 bear,1] done
```
The mappers still run **in parallel** with each other; only `main` waits. When the 3rd `join` returns,
every output list is complete, so the shuffle sees all words.

### Why two separate loops (start all, then join all)?
If you wrote `start(); join();` inside ONE loop, main would start M1, wait for it, then start M2, wait…
→ mappers run one after another = **no parallelism**. Start all first, then wait for all.

### Link to real MapReduce
This is the **barrier** between the map and reduce phases: Hadoop does not start reducing a word until
**all** map tasks have finished, because the last mapper might still emit another `(car, 1)`.
`join()` is our version of that barrier.

### Why `throws InterruptedException` on main?
`join()` can be interrupted while waiting (another thread calls `interrupt()`); Java forces you to declare it.
We just add `throws InterruptedException` to `main`.

## Viva answers (manual FAQ), explained
1. **What is MapReduce?** A programming model for processing huge data in parallel on a cluster.
   Programmer writes `map` (turns each input record into intermediate key/value pairs) and `reduce` (combines
   all values with the same key into a result). The framework handles splitting, scheduling, data movement, failures.
2. **Shuffle & sort:** the step between map and reduce. Takes all mapper output, groups values by key, sorts keys,
   and sends each key to exactly one reducer. Guarantees a reducer sees ALL values of its key.
3. **Combiner:** a "mini reducer" that runs on the mapper's machine BEFORE the shuffle.
   Mapper 2 emits (car,1),(car,1) → combiner makes (car,2) → less data sent over the network.
   **Why can IntSumReducer be the combiner?** Addition is **associative and commutative**: you can add in any
   grouping/order and get the same total: (1+1)+1 = 1+(1+1) = 3. So partial sums early don't change the result.
   (Average could NOT be used directly as a combiner: avg of avgs ≠ overall avg.)
4. **HDFS (Hadoop Distributed File System):** stores huge files across many machines.
   - **Block:** a file is cut into large chunks (128 MB default); each block stored on different machines,
     **replicated 3 times** so a dead disk loses nothing.
   - **NameNode:** the master; stores only metadata: which blocks make up a file and which machines hold them (the index).
   - **DataNode:** the workers; store the actual blocks and send heartbeats to the NameNode.
5. **Fault tolerance for a failed map task:** the master notices (no heartbeat / task fails) and simply **re-runs that
   map task on another machine** that has a replica of the same block. Possible because map tasks are
   independent and have no side effects: re-running gives the same output.
6. **Data locality:** send the **code to the data**, not data to the code. A map task is scheduled on the machine that
   already stores its block. Moving a few KB of program is cheap; moving 128 MB over the network is slow.
7. **Number of tasks:** map tasks = number of **input splits** (≈ one per HDFS block; a 1 GB file with 128 MB blocks
   → 8 mappers). Reduce tasks = **set by the programmer** (`job.setNumReduceTasks(n)`, default 1).
8. **Bad fits for MapReduce:** iterative algorithms (machine learning, PageRank: each round rewrites to disk → slow;
   Spark is better), real-time/low-latency queries, small data, graph algorithms, problems needing shared state or
   lots of communication between tasks.

## Hadoop components (if asked)
- **HDFS** = storage, **YARN** = resource manager/scheduler (ResourceManager + NodeManagers), **MapReduce** = the library.
- Hadoop WordCount: `TokenizerMapper extends Mapper<Object,Text,Text,IntWritable>` writes `context.write(word, one)`;
  `IntSumReducer extends Reducer<Text,IntWritable,Text,IntWritable>` sums values; `main` creates a `Job`,
  sets mapper/combiner/reducer classes, input/output paths. `Text`/`IntWritable` = Hadoop's serializable String/int.
- Output file: `part-r-00000`. Output directory must not exist before running (FileAlreadyExistsException).

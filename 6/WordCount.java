import java.util.*;

// Word count using the MapReduce model (single-JVM simulation, no Hadoop needed).
// Input -> Split -> Map (one thread per split) -> Shuffle & Sort -> Reduce -> Output
public class WordCount {

    static class Pair {                          // a (key, value) pair, e.g. ("deer", 1)
        String key;
        int value;
        Pair(String k, int v) { key = k; value = v; }
        public String toString() { return "(" + key + "," + value + ")"; }
    }

    // MAP: one split (document) in -> list of (word, 1) out. Each mapper is its own thread.
    static class Mapper extends Thread {
        String split;
        ArrayList<Pair> output = new ArrayList<>();
        Mapper(String split) { this.split = split; }

        public void run() {
            String[] words = split.toLowerCase().split(" ");
            for (String w : words) {
                output.add(new Pair(w, 1));      // emit (word, 1)
            }
        }
    }

    // REDUCE: one word + all its 1s in -> total out
    static int reduce(String word, ArrayList<Integer> counts) {
        int sum = 0;
        for (int c : counts) sum = sum + c;
        return sum;
    }

    public static void main(String[] args) throws InterruptedException {
        // INPUT, already split: one document per mapper
        String[] splits = { "Deer Bear River", "Car Car River", "Deer Car Bear" };
        int n = splits.length;

        // MAP phase: create and start one mapper thread per split
        Mapper[] mappers = new Mapper[n];
        for (int i = 0; i < n; i++) {
            mappers[i] = new Mapper(splits[i]);
            mappers[i].start();
        }
        // wait until EVERY mapper has finished before going on
        for (int i = 0; i < n; i++) {
            mappers[i].join();
        }

        System.out.println("MAP output:");
        for (int i = 0; i < n; i++) {
            System.out.println("  Mapper " + (i + 1) + " [" + splits[i] + "] -> " + mappers[i].output);
        }

        // SHUFFLE & SORT: group all values by word. TreeMap keeps the words sorted.
        TreeMap<String, ArrayList<Integer>> grouped = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            for (Pair p : mappers[i].output) {
                if (!grouped.containsKey(p.key)) {          // first time we see this word
                    grouped.put(p.key, new ArrayList<Integer>());
                }
                grouped.get(p.key).add(p.value);            // add its 1 to the word's list
            }
        }

        System.out.println("\nSHUFFLE & SORT output:");
        for (String word : grouped.keySet()) {
            System.out.println("  " + word + " -> " + grouped.get(word));
        }

        // REDUCE phase: one reduce call per word
        System.out.println("\nREDUCE output (final word count):");
        for (String word : grouped.keySet()) {
            System.out.println("  " + word + "\t" + reduce(word, grouped.get(word)));
        }
    }
}

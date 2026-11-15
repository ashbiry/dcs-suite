import java.util.*;

// Leader election: Bully and Ring algorithms (simulation). Processes are numbered 1..n,
// higher number = higher priority. alive[i] = false means process i has crashed.
public class Election {
    static int n;
    static boolean[] alive;

    // ---------------- BULLY ----------------
    static void bully(int p) {
        System.out.println("P" + p + " starts an election");
        boolean gotOk = false;
        for (int q = p + 1; q <= n; q++) {                     // ELECTION to every HIGHER process
            if (alive[q]) {
                System.out.println("  P" + p + " -> P" + q + " : ELECTION    P" + q + " -> P" + p + " : OK");
                gotOk = true;
            } else {
                System.out.println("  P" + p + " -> P" + q + " : ELECTION    (P" + q + " is down, no reply)");
            }
        }
        if (!gotOk) {                                          // nobody higher alive -> I win
            System.out.println("P" + p + " got no OK, so P" + p + " is the new COORDINATOR");
            for (int q = 1; q <= n; q++)
                if (alive[q] && q != p)
                    System.out.println("  P" + p + " -> P" + q + " : COORDINATOR(" + p + ")");
            return;
        }
        // someone higher answered OK: P stops; a higher process takes over the election
        for (int q = p + 1; q <= n; q++) {
            if (alive[q]) { bully(q); return; }
        }
    }

    // ---------------- RING ----------------
    static int nextAlive(int p) {                              // successor on the ring, skipping dead ones
        int q = p;
        while (true) {
            q = q % n + 1;                                     // after n comes 1 (circle)
            if (alive[q]) return q;
            System.out.println("  (P" + q + " is down, skipped)");
        }
    }

    static void ring(int start) {
        System.out.println("P" + start + " starts an election");
        ArrayList<Integer> ids = new ArrayList<>();            // the ELECTION message: list of IDs
        ids.add(start);
        int cur = start;
        while (true) {
            int next = nextAlive(cur);
            System.out.println("  P" + cur + " -> P" + next + " : ELECTION " + ids);
            if (next == start) break;                          // message came back to initiator
            ids.add(next);                                     // each process adds its own ID
            cur = next;
        }
        int leader = Collections.max(ids);                     // highest ID in the list wins
        System.out.println("Message is back at P" + start + ". Highest ID in " + ids + " = " + leader);

        cur = start;                                           // second round: announce winner
        do {
            int next = nextAlive(cur);
            System.out.println("  P" + cur + " -> P" + next + " : COORDINATOR(" + leader + ")");
            cur = next;
        } while (cur != start);
        System.out.println("P" + leader + " is the new COORDINATOR");
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("Number of processes: ");
        n = sc.nextInt();
        alive = new boolean[n + 1];
        for (int i = 1; i <= n; i++) alive[i] = true;

        System.out.print("Crashed process (old coordinator): ");
        int crashed = sc.nextInt();
        alive[crashed] = false;
        System.out.print("Process that detects the crash: ");
        int start = sc.nextInt();

        System.out.println("\n===== BULLY ALGORITHM =====");
        bully(start);
        System.out.println("\n===== RING ALGORITHM =====");
        ring(start);
    }
}

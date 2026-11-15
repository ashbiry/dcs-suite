import java.util.*;

// Termination detection: Dijkstra-Scholten algorithm (single-thread simulation).
// P0 is the root. Messages in transit are kept in a queue (the "network").
public class TerminationDetection {
    static final int WORK = 0, ACK = 1;

    static class Msg {
        int type, from, to;
        Msg(int type, int from, int to) { this.type = type; this.from = from; this.to = to; }
    }

    // who sends WORK to whom (each process does its task once):
    // P0 -> P1, P2 | P1 -> P3 | P2 -> P3 | P3 -> P4 | P4 -> nobody
    static int[][] plan = { {1, 2}, {3}, {3}, {4}, {} };
    static int n = plan.length;

    static int[] parent = new int[n];        // who engaged me (-1 = not engaged)
    static int[] deficit = new int[n];       // my WORK messages not yet ACKed
    static boolean[] taskDone = new boolean[n];
    static LinkedList<Msg> network = new LinkedList<>();   // messages in transit

    static void sendWork(int from, int to) {
        deficit[from]++;                                   // rule 1
        network.add(new Msg(WORK, from, to));
        System.out.println("  P" + from + " sends WORK to P" + to + "   deficit[P" + from + "] = " + deficit[from]);
    }

    static void sendAck(int from, int to) {
        network.add(new Msg(ACK, from, to));
        System.out.println("  P" + from + " sends ACK to P" + to);
    }

    // rules 5 and 6: called whenever a process becomes passive
    static void check(int p) {
        if (deficit[p] != 0) return;                       // still waiting for ACKs
        if (p == 0) {
            System.out.println("  P0 is passive and deficit = 0  ==>  TERMINATION DETECTED");
        } else if (parent[p] != -1) {
            System.out.println("  P" + p + " is passive and deficit = 0, leaves the tree:");
            sendAck(p, parent[p]);                         // the pending ACK to parent
            parent[p] = -1;                                // disengaged
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < n; i++) parent[i] = -1;

        System.out.println("P0 (root) starts the computation");
        taskDone[0] = true;
        for (int to : plan[0]) sendWork(0, to);

        while (!network.isEmpty()) {
            Msg m = network.removeFirst();                 // deliver the oldest message
            int p = m.to;
            System.out.println("\nP" + p + " receives " + (m.type == WORK ? "WORK" : "ACK")
                    + " from P" + m.from + "   (in transit: " + network.size() + ")");

            if (m.type == WORK) {
                if (p != 0 && parent[p] == -1) {           // rule 2: not engaged -> engage
                    parent[p] = m.from;
                    System.out.println("  P" + p + " engaged, parent = P" + m.from + " (ACK kept pending)");
                } else {                                   // rule 3: already engaged -> ACK now
                    System.out.println("  P" + p + " already engaged, ACKs at once");
                    sendAck(p, m.from);
                }
                if (!taskDone[p]) {                        // do the local work, may send more WORK
                    taskDone[p] = true;
                    for (int to : plan[p]) sendWork(p, to);
                }
            } else {                                       // rule 4: ACK received
                deficit[p]--;
                System.out.println("  deficit[P" + p + "] = " + deficit[p]);
            }
            check(p);                                      // P is now passive
        }
        System.out.println("\nNetwork empty, all processes passive. Computation finished.");
    }
}

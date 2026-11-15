import java.util.*;

// Berkeley clock synchronization (simulation). Node 0 is the master (time daemon).
public class Berkeley {
    static int toMin(String t) {                       // "03:25" -> 205 minutes
        String[] p = t.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }
    static String toTime(int m) { return String.format("%02d:%02d", m / 60, m % 60); }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("Number of nodes (node 0 = master): ");
        int n = sc.nextInt();
        int[] clock = new int[n];
        for (int i = 0; i < n; i++) {
            System.out.print("Clock of node " + i + " (HH:MM): ");
            clock[i] = toMin(sc.next());
        }

        // Step 1: master polls every node and computes difference = node - master
        int[] diff = new int[n];
        int sum = 0;
        System.out.println("\nStep 1: master polls nodes, diff = node - master");
        for (int i = 0; i < n; i++) {
            diff[i] = clock[i] - clock[0];
            sum += diff[i];
            System.out.println("  node " + i + ": " + toTime(clock[i]) + "  diff = " + diff[i] + " min");
        }

        // Step 2: average of all differences (master's own diff 0 included)
        int avg = sum / n;
        int target = clock[0] + avg;
        System.out.println("\nStep 2: average diff = " + sum + " / " + n + " = " + avg + " min");
        System.out.println("  agreed time = master + avg = " + toTime(target));

        // Step 3: master sends each node the ADJUSTMENT (not the time)
        System.out.println("\nStep 3: master sends adjustments");
        for (int i = 0; i < n; i++) {
            int adj = target - clock[i];
            clock[i] += adj;
            System.out.println("  node " + i + ": adjust " + (adj >= 0 ? "+" : "") + adj
                    + " min -> " + toTime(clock[i]));
        }
    }
}

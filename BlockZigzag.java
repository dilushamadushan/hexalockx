public class BlockZigzag {

    public static int railsFor(int[] roundKey) {
        return roundKey[0] % 5 + 2;
    }

    public static int[] railOrder(int n, int rails) {
        int[] order = new int[n];
        int cycle = 2 * (rails - 1);
        int idx = 0;
        for (int rail = 0; rail < rails; rail++) {
            for (int i = 0; i < n; i++) {
                int pos = i % cycle;
                int r = (pos < rails) ? pos : cycle - pos;
                if (r == rail) {
                    order[idx++] = i;
                }
            }
        }
        return order;
    }

    public static int[] keyPermutation(int[] roundKey, int n) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = i;
        long state = 1;
        for (int b : roundKey) {
            state = (state * 257 + b) & 0xFFFFFFFFL;
        }
        for (int i = n - 1; i > 0; i--) {
            state = (state * 1103515245L + 12345L) & 0x7FFFFFFFL;
            int j = (int) ((state >> 8) % (i + 1));
            int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
        }
        return p;
    }

    public static int[] encrypt(int[] block, int[] roundKey) {
        int n = block.length;
        int[] order = railOrder(n, railsFor(roundKey));
        int[] perm = keyPermutation(roundKey, n);
        int[] z = new int[n];
        for (int j = 0; j < n; j++) z[j] = block[order[j]];
        int[] out = new int[n];
        for (int j = 0; j < n; j++) out[j] = z[perm[j]];
        return out;
    }

    public static int[] decrypt(int[] block, int[] roundKey) {
        int n = block.length;
        int[] order = railOrder(n, railsFor(roundKey));
        int[] perm = keyPermutation(roundKey, n);
        int[] z = new int[n];
        for (int j = 0; j < n; j++) z[perm[j]] = block[j];
        int[] out = new int[n];
        for (int j = 0; j < n; j++) out[order[j]] = z[j];
        return out;
    }

    public static void printGrid(int n, int rails) {
        int cycle = 2 * (rails - 1);
        for (int rail = 0; rail < rails; rail++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                int pos = i % cycle;
                int r = (pos < rails) ? pos : cycle - pos;
                sb.append(r == rail ? String.format("%2d ", i) : " . ");
            }
            System.out.println(sb.toString());
        }
    }
}

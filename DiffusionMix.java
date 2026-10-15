public class DiffusionMix {

    public static void forward(int[] b) {
        int n = b.length;
        for (int i = 1; i < n; i++) {
            b[i] = (b[i] + b[i - 1]) & 0xFF;
        }
        for (int i = n - 2; i >= 0; i--) {
            b[i] = (b[i] + b[i + 1]) & 0xFF;
        }
    }

    public static void inverse(int[] b) {
        int n = b.length;
        for (int i = 0; i <= n - 2; i++) {
            b[i] = (b[i] - b[i + 1]) & 0xFF;
        }
        for (int i = n - 1; i >= 1; i--) {
            b[i] = (b[i] - b[i - 1]) & 0xFF;
        }
    }

    public static int bitDifference(int[] a, int[] b) {
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff += Integer.bitCount((a[i] ^ b[i]) & 0xFF);
        }
        return diff;
    }

    public static void avalancheTest(int[] block, int byteIndex, int bitIndex) {
        int[] a = block.clone();
        int[] b = block.clone();
        b[byteIndex] ^= (1 << bitIndex);
        forward(a);
        forward(b);
        int diff = bitDifference(a, b);
        double pct = 100.0 * diff / (block.length * 8);
        System.out.printf("Avalanche (mix only): %d of %d bits changed (%.1f%%)%n",
                diff, block.length * 8, pct);
    }
}

public class SBox {

    public static int[] generate(int[] master) {
        int[] s = new int[256];
        for (int i = 0; i < 256; i++) s[i] = i;

        long state = 7;
        for (int b : master) {
            state = (state * 131 + b) & 0xFFFFFFFFL;
        }
        for (int i = 255; i > 0; i--) {
            state = (state * 1103515245L + 12345L) & 0x7FFFFFFFL;
            int j = (int) ((state >> 8) % (i + 1));
            int tmp = s[i]; s[i] = s[j]; s[j] = tmp;
        }
        return s;
    }

    public static int[] inverse(int[] sbox) {
        int[] inv = new int[256];
        for (int i = 0; i < 256; i++) inv[sbox[i]] = i;
        return inv;
    }

    public static void substitute(int[] block, int[] table) {
        for (int i = 0; i < block.length; i++) {
            block[i] = table[block[i]];
        }
    }

    public static boolean isPermutation(int[] sbox) {
        boolean[] seen = new boolean[256];
        for (int v : sbox) {
            if (v < 0 || v > 255 || seen[v]) return false;
            seen[v] = true;
        }
        return true;
    }

    public static int fixedPoints(int[] sbox) {
        int c = 0;
        for (int i = 0; i < 256; i++) if (sbox[i] == i) c++;
        return c;
    }

    public static void printGrid(int[] sbox) {
        for (int row = 0; row < 16; row++) {
            StringBuilder sb = new StringBuilder();
            for (int col = 0; col < 16; col++) {
                sb.append(String.format("%02X ", sbox[row * 16 + col]));
            }
            System.out.println(sb.toString().trim());
        }
    }
}

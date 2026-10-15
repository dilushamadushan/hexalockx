import java.security.SecureRandom;
import java.util.Base64;

public class ModeOfOperation {

    public static final int BLOCK = 16;
    private static final SecureRandom RNG = new SecureRandom();

    public static int[] pad(byte[] data) {
        int padLen = BLOCK - (data.length % BLOCK);
        int[] out = new int[data.length + padLen];
        for (int i = 0; i < data.length; i++) out[i] = data[i] & 0xFF;
        for (int i = data.length; i < out.length; i++) out[i] = padLen;
        return out;
    }

    public static byte[] unpad(int[] data) {
        if (data.length == 0 || data.length % BLOCK != 0)
            throw new IllegalArgumentException("Invalid data length");
        int padLen = data[data.length - 1];
        if (padLen < 1 || padLen > BLOCK || padLen > data.length)
            throw new IllegalArgumentException("Invalid padding");
        for (int i = data.length - padLen; i < data.length; i++) {
            if (data[i] != padLen) throw new IllegalArgumentException("Invalid padding");
        }
        byte[] out = new byte[data.length - padLen];
        for (int i = 0; i < out.length; i++) out[i] = (byte) data[i];
        return out;
    }

    public static int[][] split(int[] data) {
        int[][] blocks = new int[data.length / BLOCK][BLOCK];
        for (int i = 0; i < data.length; i++) {
            blocks[i / BLOCK][i % BLOCK] = data[i];
        }
        return blocks;
    }

    public static int[] join(int[][] blocks) {
        int[] out = new int[blocks.length * BLOCK];
        for (int b = 0; b < blocks.length; b++) {
            System.arraycopy(blocks[b], 0, out, b * BLOCK, BLOCK);
        }
        return out;
    }

    public static int[] xor(int[] a, int[] b) {
        int[] out = new int[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] ^ b[i];
        return out;
    }

    public static int[] randomBytes(int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = RNG.nextInt(256);
        return out;
    }

    public static int[] mac(int[] data, int[] master) {
        int[] t = master.clone();
        for (int n = 0; n < data.length; n++) {
            int i = n % BLOCK;
            int v = (t[i] ^ data[n]) & 0xFF;
            int rot = n % 7 + 1;
            v = ((v << rot) | (v >> (8 - rot))) & 0xFF;
            t[i] = (v + t[(i + 1) % BLOCK] + n) & 0xFF;
        }
        for (int rep = 0; rep < 32; rep++) {
            for (int i = 0; i < BLOCK; i++) {
                int v = (t[i] + t[(i + 7) % BLOCK]) & 0xFF;
                t[i] = (((v << 3) | (v >> 5)) & 0xFF) ^ rep;
            }
        }
        return t;
    }

    public static boolean macEquals(int[] a, int[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= (a[i] ^ b[i]);
        return diff == 0;
    }

    public static String encodeBase64(int[] data) {
        byte[] raw = new byte[data.length];
        for (int i = 0; i < data.length; i++) raw[i] = (byte) data[i];
        return Base64.getEncoder().encodeToString(raw);
    }

    public static int[] decodeBase64(String text) {
        byte[] raw = Base64.getDecoder().decode(text.trim());
        int[] out = new int[raw.length];
        for (int i = 0; i < raw.length; i++) out[i] = raw[i] & 0xFF;
        return out;
    }

    public static String toHex(int[] data) {
        StringBuilder sb = new StringBuilder();
        for (int v : data) sb.append(String.format("%02X ", v));
        return sb.toString().trim();
    }
}

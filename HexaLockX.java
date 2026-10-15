import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class HexaLockX {
    public static final int ROUNDS = 8;
    public static boolean TRACE = false;

    public static class Keys {
        public int[] master;
        public int[][] rk;
        public int[] sbox;
        public int[] inv;
    }

    public static Keys buildKeys(String password, int[] salt) {
        KeyDerivation.validatePassword(password);
        Keys k = new Keys();
        k.master = KeyDerivation.stretch(password.getBytes(StandardCharsets.UTF_8), salt);
        k.rk = KeyDerivation.roundKeys(k.master, ROUNDS);
        k.sbox = SBox.generate(k.master);
        k.inv = SBox.inverse(k.sbox);
        return k;
    }

    private static void trace(String label, int[] b) {
        if (TRACE) System.out.println(String.format("  %-18s %s", label, ModeOfOperation.toHex(b)));
    }

    public static int[] encryptBlock(int[] in, Keys k) {
        int[] b = in.clone();
        trace("input", b);
        for (int r = 0; r < ROUNDS; r++) {
            SBox.substitute(b, k.sbox);                 trace("R" + (r + 1) + " shuffle", b);
            BitTwist.encrypt(b, k.rk[r], r);            trace("R" + (r + 1) + " twist", b);
            b = BlockZigzag.encrypt(b, k.rk[r]);        trace("R" + (r + 1) + " zigzag", b);
            DiffusionMix.forward(b);                    trace("R" + (r + 1) + " spread", b);
            for (int i = 0; i < b.length; i++) b[i] ^= k.rk[r][i];
            trace("R" + (r + 1) + " xor key", b);
        }
        return b;
    }

    public static int[] decryptBlock(int[] in, Keys k) {
        int[] b = in.clone();
        for (int r = ROUNDS - 1; r >= 0; r--) {
            for (int i = 0; i < b.length; i++) b[i] ^= k.rk[r][i];
            DiffusionMix.inverse(b);
            b = BlockZigzag.decrypt(b, k.rk[r]);
            BitTwist.decrypt(b, k.rk[r], r);
            SBox.substitute(b, k.inv);
        }
        return b;
    }

    public static String encrypt(String plaintext, String password) {
        int[] salt = ModeOfOperation.randomBytes(16);
        int[] iv = ModeOfOperation.randomBytes(16);
        Keys k = buildKeys(password, salt);

        int[][] blocks = ModeOfOperation.split(
                ModeOfOperation.pad(plaintext.getBytes(StandardCharsets.UTF_8)));

        int[][] cipher = new int[blocks.length][];
        int[] prev = iv;
        for (int i = 0; i < blocks.length; i++) {
            int[] x = ModeOfOperation.xor(blocks[i], prev);
            cipher[i] = encryptBlock(x, k);
            prev = cipher[i];
        }

        int[] c = ModeOfOperation.join(cipher);
        int[] body = new int[32 + c.length];
        System.arraycopy(salt, 0, body, 0, 16);
        System.arraycopy(iv, 0, body, 16, 16);
        System.arraycopy(c, 0, body, 32, c.length);

        int[] tag = ModeOfOperation.mac(body, k.master);
        int[] all = new int[body.length + 16];
        System.arraycopy(body, 0, all, 0, body.length);
        System.arraycopy(tag, 0, all, body.length, 16);
        return ModeOfOperation.encodeBase64(all);
    }

    public static String decrypt(String base64, String password) {
        int[] raw = ModeOfOperation.decodeBase64(base64);
        if (raw.length < 64 || (raw.length - 48) % 16 != 0)
            throw new IllegalArgumentException("Invalid ciphertext length");

        int[] salt = Arrays.copyOfRange(raw, 0, 16);
        int[] iv = Arrays.copyOfRange(raw, 16, 32);
        int[] body = Arrays.copyOfRange(raw, 0, raw.length - 16);
        int[] tag = Arrays.copyOfRange(raw, raw.length - 16, raw.length);
        int[] c = Arrays.copyOfRange(raw, 32, raw.length - 16);

        Keys k = buildKeys(password, salt);
        if (!ModeOfOperation.macEquals(tag, ModeOfOperation.mac(body, k.master)))
            throw new IllegalArgumentException("Wrong password or data was tampered with");

        int[][] cipher = ModeOfOperation.split(c);
        int[][] plain = new int[cipher.length][];
        int[] prev = iv;
        for (int i = 0; i < cipher.length; i++) {
            int[] d = decryptBlock(cipher[i], k);
            plain[i] = ModeOfOperation.xor(d, prev);
            prev = cipher[i];
        }
        byte[] out = ModeOfOperation.unpad(ModeOfOperation.join(plain));
        return new String(out, StandardCharsets.UTF_8);
    }
}
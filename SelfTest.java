import java.util.Random;

public class SelfTest {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
        if (ok) passed++; else failed++;
    }

    static int[] randomBlock(Random r) {
        int[] b = new int[16];
        for (int i = 0; i < 16; i++) b[i] = r.nextInt(256);
        return b;
    }

    public static void main(String[] args) {
        Random rnd = new Random(42);
        int[] salt = new int[16];
        HexaLockX.Keys k = HexaLockX.buildKeys("secret", salt);

        check("M1 round keys count", k.rk.length == HexaLockX.ROUNDS);
        check("M1 same password+salt -> same master",
                java.util.Arrays.equals(k.master, HexaLockX.buildKeys("secret", salt).master));
        check("M1 different password -> different master",
                !java.util.Arrays.equals(k.master, HexaLockX.buildKeys("secreT", salt).master));

        check("M2 S-box is a permutation", SBox.isPermutation(k.sbox));
        boolean sb = true;
        for (int i = 0; i < 256; i++) if (k.inv[k.sbox[i]] != i) sb = false;
        check("M2 inverse S-box works", sb);

        boolean tw = true;
        for (int round = 0; round < 8 && tw; round++) {
            int[] b = randomBlock(rnd), o = b.clone();
            BitTwist.encrypt(b, k.rk[round], round);
            BitTwist.decrypt(b, k.rk[round], round);
            tw = java.util.Arrays.equals(b, o);
        }
        check("M3 twist encrypt/decrypt", tw);

        boolean zz = true;
        for (int round = 0; round < 8 && zz; round++) {
            int[] b = randomBlock(rnd);
            int[] back = BlockZigzag.decrypt(BlockZigzag.encrypt(b, k.rk[round]), k.rk[round]);
            zz = java.util.Arrays.equals(b, back);
        }
        check("M4 zigzag encrypt/decrypt", zz);

        boolean mx = true;
        for (int t = 0; t < 50 && mx; t++) {
            int[] b = randomBlock(rnd), o = b.clone();
            DiffusionMix.forward(b);
            DiffusionMix.inverse(b);
            mx = java.util.Arrays.equals(b, o);
        }
        check("M5 mix forward/inverse", mx);

        byte[] data = "HELLO".getBytes();
        check("M6 pad/unpad", java.util.Arrays.equals(data, ModeOfOperation.unpad(ModeOfOperation.pad(data))));
        int[] blk = randomBlock(rnd);
        check("M6 Base64 round trip",
                java.util.Arrays.equals(blk, ModeOfOperation.decodeBase64(ModeOfOperation.encodeBase64(blk))));

        boolean bc = true;
        for (int t = 0; t < 50 && bc; t++) {
            int[] b = randomBlock(rnd);
            bc = java.util.Arrays.equals(b, HexaLockX.decryptBlock(HexaLockX.encryptBlock(b, k), k));
        }
        check("Full block encrypt/decrypt (8 rounds)", bc);

        String[] tests = {"HELLO", "", "a", "Exactly16BytesTxt", "Hello World! 123 @#$ \u0DC3\u0DD2\u0D82\u0DC4\u0DBD",
                "The quick brown fox jumps over the lazy dog. ".repeat(20)};
        boolean full = true;
        for (String t : tests) {
            String enc = HexaLockX.encrypt(t, "MyPassw0rd!");
            if (!HexaLockX.decrypt(enc, "MyPassw0rd!").equals(t)) full = false;
        }
        check("Full cipher round trip (6 texts)", full);

        String e1 = HexaLockX.encrypt("HELLO", "secret"), e2 = HexaLockX.encrypt("HELLO", "secret");
        check("Same text twice -> different ciphertext", !e1.equals(e2));

        boolean wrong = false;
        try { HexaLockX.decrypt(e1, "secreT"); } catch (IllegalArgumentException e) { wrong = true; }
        check("Wrong password rejected", wrong);

        boolean tamper = false;
        int[] raw = ModeOfOperation.decodeBase64(e1);
        raw[40] ^= 1;
        try { HexaLockX.decrypt(ModeOfOperation.encodeBase64(raw), "secret"); }
        catch (IllegalArgumentException e) { tamper = true; }
        check("Tampered data rejected", tamper);

        int[] a = new int[16]; a[0] = 'H';
        int[] b2 = a.clone(); b2[0] ^= 1;
        int diff = DiffusionMix.bitDifference(HexaLockX.encryptBlock(a, k), HexaLockX.encryptBlock(b2, k));
        check("Avalanche: 1-bit change flips 40-88 of 128 bits (got " + diff + ")", diff >= 40 && diff <= 88);

        int[] constBytes = new int[100];
        check("CryptoAnalysis entropy of constant bytes is 0.0",
                Math.abs(CryptoAnalysis.shannonEntropy(constBytes) - 0.0) < 1e-6);

        int[] uniformBytes = new int[256];
        for (int i = 0; i < 256; i++) uniformBytes[i] = i;
        check("CryptoAnalysis entropy of perfectly uniform bytes is 8.0",
                Math.abs(CryptoAnalysis.shannonEntropy(uniformBytes) - 8.0) < 1e-6);
        check("CryptoAnalysis chi-square of perfectly uniform bytes is 0.0",
                Math.abs(CryptoAnalysis.chiSquareUniformity(uniformBytes) - 0.0) < 1e-6);

        CryptoAnalysis.AvalancheStats stats = CryptoAnalysis.runAvalancheTest(k, 1000);
        check("CryptoAnalysis 1000-trial avalanche mean near 64 (got " + String.format("%.2f", stats.mean) + ")",
                stats.mean >= 58.0 && stats.mean <= 70.0);
        check("CryptoAnalysis 1000-trial avalanche stdDev > 0 (got " + String.format("%.2f", stats.stdDev) + ")",
                stats.stdDev > 0.0 && stats.stdDev < 15.0);

        System.out.println("\nPassed: " + passed + "  Failed: " + failed);
    }
}

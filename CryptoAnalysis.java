import java.nio.charset.StandardCharsets;
import java.util.Random;

public class CryptoAnalysis {

    public static class AvalancheStats {
        public final int trials;
        public final int min;
        public final int max;
        public final double mean;
        public final double stdDev;

        public AvalancheStats(int trials, int min, int max, double mean, double stdDev) {
            this.trials = trials;
            this.min = min;
            this.max = max;
            this.mean = mean;
            this.stdDev = stdDev;
        }

        @Override
        public String toString() {
            return String.format("Trials: %d | Min: %d | Max: %d | Mean: %.2f (%.2f%%) | StdDev: %.2f",
                    trials, min, max, mean, (mean * 100.0 / 128.0), stdDev);
        }
    }

    public static AvalancheStats runAvalancheTest(HexaLockX.Keys keys, int trials) {
        Random rng = new Random();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        double sum = 0.0;
        double sumSq = 0.0;

        for (int t = 0; t < trials; t++) {
            int[] blockA = new int[16];
            for (int i = 0; i < 16; i++) {
                blockA[i] = rng.nextInt(256);
            }
            int[] blockB = blockA.clone();
            int byteIdx = rng.nextInt(16);
            int bitIdx = rng.nextInt(8);
            blockB[byteIdx] ^= (1 << bitIdx);

            int[] cipherA = HexaLockX.encryptBlock(blockA, keys);
            int[] cipherB = HexaLockX.encryptBlock(blockB, keys);

            int diff = DiffusionMix.bitDifference(cipherA, cipherB);
            if (diff < min) min = diff;
            if (diff > max) max = diff;
            sum += diff;
            sumSq += ((double) diff * diff);
        }

        double mean = sum / trials;
        double variance = (trials > 1) ? ((sumSq - (sum * sum / trials)) / (trials - 1)) : 0.0;
        double stdDev = Math.sqrt(Math.max(0.0, variance));

        return new AvalancheStats(trials, min, max, mean, stdDev);
    }

    public static int[] byteFrequencies(int[] data) {
        int[] counts = new int[256];
        for (int b : data) {
            if (b >= 0 && b < 256) {
                counts[b]++;
            }
        }
        return counts;
    }

    public static double shannonEntropy(int[] data) {
        if (data == null || data.length == 0) return 0.0;
        int[] counts = byteFrequencies(data);
        double total = data.length;
        double entropy = 0.0;
        for (int count : counts) {
            if (count > 0) {
                double p = count / total;
                entropy -= p * (Math.log(p) / Math.log(2.0));
            }
        }
        return entropy;
    }

    public static double chiSquareUniformity(int[] data) {
        if (data == null || data.length == 0) return 0.0;
        int[] counts = byteFrequencies(data);
        double expected = data.length / 256.0;
        double chiSq = 0.0;
        for (int count : counts) {
            double diff = count - expected;
            chiSq += (diff * diff) / expected;
        }
        return chiSq;
    }

    public static int[] caesarEncrypt(String text, int shift) {
        int[] out = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                out[i] = ((c - 'A' + shift) % 26) + 'A';
            } else if (c >= 'a' && c <= 'z') {
                out[i] = ((c - 'a' + shift) % 26) + 'a';
            } else {
                out[i] = c & 0xFF;
            }
        }
        return out;
    }

    public static int[] vigenereEncrypt(String text, String key) {
        if (key == null || key.isEmpty()) key = "KEY";
        String cleanKey = key.toUpperCase();
        int[] out = new int[text.length()];
        int keyIdx = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                int shift = cleanKey.charAt(keyIdx % cleanKey.length()) - 'A';
                out[i] = ((c - 'A' + shift) % 26) + 'A';
                keyIdx++;
            } else if (c >= 'a' && c <= 'z') {
                int shift = cleanKey.charAt(keyIdx % cleanKey.length()) - 'A';
                out[i] = ((c - 'a' + shift) % 26) + 'a';
                keyIdx++;
            } else {
                out[i] = c & 0xFF;
            }
        }
        return out;
    }

    public static int[] extractCipherBytes(String base64Cipher) {
        int[] raw = ModeOfOperation.decodeBase64(base64Cipher);
        if (raw.length < 48) return raw;
        int cipherLen = raw.length - 48;
        int[] c = new int[cipherLen];
        System.arraycopy(raw, 32, c, 0, cipherLen);
        return c;
    }

    public static void printHistogram(String label, int[] data, int maxBarWidth) {
        System.out.println("\n--- Byte Frequency Histogram: " + label + " (length: " + data.length + ") ---");
        int[] counts = byteFrequencies(data);
        int maxCount = 0;
        for (int c : counts) {
            if (c > maxCount) maxCount = c;
        }
        if (maxCount == 0) {
            System.out.println("  (No data)");
            return;
        }

        int printed = 0;
        for (int i = 0; i < 256; i++) {
            if (counts[i] == 0) continue;
            if ((i >= 32 && i <= 126) || counts[i] > (data.length / 50)) {
                String charRep = (i >= 32 && i <= 126) ? ("'" + (char) i + "'") : String.format("0x%02X", i);
                int barLen = (int) Math.round(((double) counts[i] / maxCount) * maxBarWidth);
                if (barLen == 0 && counts[i] > 0) barLen = 1;
                StringBuilder bar = new StringBuilder();
                for (int b = 0; b < barLen; b++) bar.append('#');
                double pct = (counts[i] * 100.0) / data.length;
                System.out.printf("  %-5s [%3d]: %-40s (%5.1f%%, n=%d)%n",
                        charRep, i, bar.toString(), pct, counts[i]);
                printed++;
                if (printed >= 26) break;
            }
        }
    }

    public static void runFullReport(String sampleText, String password) {
        System.out.println("==========================================================================");
        System.out.println("                     HEXALOCK-X CRYPTANALYSIS SUITE                       ");
        System.out.println("     (Educational security evaluation - NOT an AES-level cipher)          ");
        System.out.println("==========================================================================");

        byte[] ptBytes = sampleText.getBytes(StandardCharsets.UTF_8);
        int[] pt = new int[ptBytes.length];
        for (int i = 0; i < ptBytes.length; i++) pt[i] = ptBytes[i] & 0xFF;

        int[] caesar = caesarEncrypt(sampleText, 3);
        int[] vigenere = vigenereEncrypt(sampleText, "CRYPTO");

        String encBase64 = HexaLockX.encrypt(sampleText, password);
        int[] hexaCipher = extractCipherBytes(encBase64);

        double entPlain = shannonEntropy(pt);
        double entCaesar = shannonEntropy(caesar);
        double entVig = shannonEntropy(vigenere);
        double entHexa = shannonEntropy(hexaCipher);

        double chiPlain = chiSquareUniformity(pt);
        double chiCaesar = chiSquareUniformity(caesar);
        double chiVig = chiSquareUniformity(vigenere);
        double chiHexa = chiSquareUniformity(hexaCipher);

        System.out.println("\n1. ENTROPY & CHI-SQUARE UNIFORMITY COMPARISON:");
        System.out.printf("%-18s | %-8s | %-16s | %-16s | %s%n",
                "Cipher", "Bytes", "Entropy (bits/B)", "Chi-Square", "Uniformity Assessment");
        System.out.println("----------------------------------------------------------------------------------");
        System.out.printf("%-18s | %-8d | %-16.4f | %-16.2f | Non-uniform (natural language)%n",
                "Plaintext", pt.length, entPlain, chiPlain);
        System.out.printf("%-18s | %-8d | %-16.4f | %-16.2f | Non-uniform (monoalphabetic shift)%n",
                "Caesar (shift=3)", caesar.length, entCaesar, chiCaesar);
        System.out.printf("%-18s | %-8d | %-16.4f | %-16.2f | Low uniformity (polyalphabetic)%n",
                "Vigenere (k=CRYPTO)", vigenere.length, entVig, chiVig);
        System.out.printf("%-18s | %-8d | %-16.4f | %-16.2f | High uniformity (diffusion + S-box)%n",
                "HexaLock-X (CBC)", hexaCipher.length, entHexa, chiHexa);
        System.out.println("Note: Max theoretical entropy = 8.0000. Lower Chi-Square indicates closer fit to uniform.");

        System.out.println("\n2. FREQUENCY HISTOGRAMS (Notice how HexaLock-X flattens the frequency spikes):");
        printHistogram("Plaintext Sample", pt, 35);
        printHistogram("Caesar Ciphertext", caesar, 35);
        printHistogram("Vigenere Ciphertext", vigenere, 35);
        printHistogram("HexaLock-X Ciphertext", hexaCipher, 35);

        System.out.println("\n3. AVALANCHE EFFECT TEST (1000 Random Plaintext Blocks with 1-bit Flip):");
        HexaLockX.Keys keys = HexaLockX.buildKeys(password, new int[16]);
        AvalancheStats stats = runAvalancheTest(keys, 1000);
        System.out.println("  " + stats.toString());
        System.out.printf("  Target mean: ~64.0 bits (50.0%%). Observed mean: %.2f bits (%.2f%%)%n",
                stats.mean, (stats.mean * 100.0 / 128.0));
        System.out.println("==========================================================================\n");
    }

    public static void main(String[] args) {
        String sampleText = "CONFIDENTIAL BENCHMARK: THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG. ".repeat(30);
        String password = "SecretBench123!";
        runFullReport(sampleText, password);
    }
}

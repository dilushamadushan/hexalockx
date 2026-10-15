public class KeyDerivation {
    public static final int BLOCK = 16;
    public static final int ITERATIONS = 1000;

    public static void validatePassword(String password) {
        if (password == null || password.length() < 4) {
            throw new IllegalArgumentException("Password must be at least 4 characters");
        }
    }

    public static int strengthScore(String pw) {
        int score = 0;
        if (pw.length() >= 8) score++;
        if (pw.length() >= 12) score++;
        if (pw.matches(".*[a-z].*")) score++;
        if (pw.matches(".*[A-Z].*")) score++;
        if (pw.matches(".*[0-9].*")) score++;
        if (pw.matches(".*[^a-zA-Z0-9].*")) score++;
        return score;
    }

    public static String strengthLabel(int score) {
        if (score <= 2) return "Weak";
        if (score <= 4) return "Medium";
        return "Strong";
    }

    public static int[] stretch(byte[] password, int[] salt) {
        int[] s = new int[BLOCK];
        for (int i = 0; i < BLOCK; i++) {
            s[i] = ((password[i % password.length] & 0xFF) + i) & 0xFF;
        }
        for (int iter = 0; iter < ITERATIONS; iter++) {
            for (int i = 0; i < BLOCK; i++) {
                int v = (s[i] + s[(i + 1) % BLOCK] + salt[i] + iter) & 0xFF;
                int rot = s[(i + 5) % BLOCK] % 7 + 1;
                s[i] = ((v << rot) | (v >> (8 - rot))) & 0xFF;
            }
        }
        return s;
    }

    public static int[][] roundKeys(int[] master, int rounds) {
        int[][] rk = new int[rounds][BLOCK];
        int[] st = master.clone();
        for (int r = 0; r < rounds; r++) {
            for (int i = 0; i < BLOCK; i++) {
                st[i] = (5 * st[i] + 17 + st[(i + 3) % BLOCK] + r) & 0xFF;
            }
            rk[r] = st.clone();
        }
        return rk;
    }

    public static String fingerprint(int[] master) {
        return String.format("%02X%02X%02X%02X", master[0], master[1], master[2], master[3]);
    }

    public static void printRoundKeys(int[][] rk) {
        for (int r = 0; r < rk.length; r++) {
            StringBuilder sb = new StringBuilder();
            for (int v : rk[r]) sb.append(String.format("%02X ", v));
            System.out.println("Round key " + (r + 1) + ": " + sb.toString().trim());
        }
    }
}
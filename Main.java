import java.util.Arrays;
import java.util.Scanner;

public class Main{
    
    static Scanner sc = new Scanner(System.in);
    
    public static void main(String[] args){
        boolean run = true;
        while(run){
            System.out.println("\n===== HexaLock-X =====");
            System.out.println("1. Encrypt text");
            System.out.println("2. Decrypt text");
            System.out.println("3. Avalanche test");
            System.out.println("4. Show key info (round keys, S-box)");
            System.out.println("5. Tamper test");
            System.out.println("6. Cryptanalysis suite (Avalanche 1000, Entropy, Chi-Square, Histogram)");
            System.out.println("7. Exit");
            System.out.print("Choice: ");
            String c = sc.nextLine().trim();
            try{
                switch(c){
                    case "1" : doEncrypt(); break;
                    case "2" : doDecrypt(); break;
                    case "3" : doAvalanche(); break;
                    case "4" : doKeyInfo(); break;
                    case "5" : doTamper(); break;
                    case "6" : doCryptoAnalysis(); break;
                    case "7" : case "exit": run = false; break;
                    default : System.out.println("Invalid choice");
                }
            }catch(IllegalArgumentException e){
                System.out.println("Error : " + e.getMessage());
            }
        }
        System.out.println("Goodbye..!");
    }
    
    static void doEncrypt() {
        System.out.print("Plaintext: ");
        String text = sc.nextLine();
        System.out.print("Password : ");
        String pw = sc.nextLine();
        KeyDerivation.validatePassword(pw);
        int score = KeyDerivation.strengthScore(pw);
        System.out.println("Password strength: " + KeyDerivation.strengthLabel(score) + " (" + score + "/6)");

        System.out.print("Show step-by-step trace of the first block? (y/n): ");
        HexaLockX.TRACE = sc.nextLine().trim().equalsIgnoreCase("y");
        String enc = HexaLockX.encrypt(text, pw);
        HexaLockX.TRACE = false;
        System.out.println("Ciphertext (Base64):\n" + enc);
    }
 
    static void doDecrypt() {
        System.out.print("Ciphertext (Base64): ");
        String enc = sc.nextLine();
        System.out.print("Password           : ");
        String pw = sc.nextLine();
        System.out.println("Decrypted text: " + HexaLockX.decrypt(enc, pw));
    }

    static void doAvalanche() {
        int[] salt = new int[16];
        HexaLockX.Keys k = HexaLockX.buildKeys("secret", salt);
        int[] a = new int[16];
        byte[] h = "HELLO".getBytes();
        for (int i = 0; i < h.length; i++) a[i] = h[i];
        int[] b = a.clone();
        b[0] ^= 1;                                   
        int[] ca = HexaLockX.encryptBlock(a, k);
        int[] cb = HexaLockX.encryptBlock(b, k);
        System.out.println("Input A : " + ModeOfOperation.toHex(a));
        System.out.println("Input B : " + ModeOfOperation.toHex(b) + "  (1 bit different)");
        System.out.println("Cipher A: " + ModeOfOperation.toHex(ca));
        System.out.println("Cipher B: " + ModeOfOperation.toHex(cb));
        int diff = DiffusionMix.bitDifference(ca, cb);
        System.out.printf("Output bits changed: %d of 128 (%.1f%%)%n", diff, diff * 100.0 / 128);
        DiffusionMix.avalancheTest(a, 0, 0);
    }

    static void doKeyInfo() {
        System.out.print("Password: ");
        String pw = sc.nextLine();
        HexaLockX.Keys k = HexaLockX.buildKeys(pw, new int[16]);
        System.out.println("Key fingerprint: " + KeyDerivation.fingerprint(k.master));
        KeyDerivation.printRoundKeys(k.rk);
        System.out.println("S-box valid permutation: " + SBox.isPermutation(k.sbox)
                + ", fixed points: " + SBox.fixedPoints(k.sbox));
        SBox.printGrid(k.sbox);
        int rails = BlockZigzag.railsFor(k.rk[0]);
        System.out.println("Round 1 zigzag grid (" + rails + " rails):");
        BlockZigzag.printGrid(16, rails);
        BitTwist.printRotationTable(k.rk[0]);
    }

    static void doTamper() {
        String enc = HexaLockX.encrypt("Secret message", "password1");
        int[] raw = ModeOfOperation.decodeBase64(enc);
        raw[40] ^= 1;                                
        String bad = ModeOfOperation.encodeBase64(raw);
        System.out.println("Original decrypts OK: " + HexaLockX.decrypt(enc, "password1"));
        try {
            HexaLockX.decrypt(bad, "password1");
            System.out.println("Tamper NOT detected (unexpected)");
        } catch (IllegalArgumentException e) {
            System.out.println("Tampered data result: " + e.getMessage());
        }
    }

    static void doCryptoAnalysis() {
        System.out.println("\n--- HexaLock-X Cryptanalysis Suite ---");
        System.out.print("Run standard benchmark report? (Y/n): ");
        String ans = sc.nextLine().trim();
        String sampleText;
        String pw = "SecretBench123!";
        if (ans.equalsIgnoreCase("n")) {
            System.out.print("Plaintext: ");
            sampleText = sc.nextLine();
            System.out.print("Password : ");
            pw = sc.nextLine();
        } else {
            sampleText = "CONFIDENTIAL BENCHMARK: THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG. ".repeat(30);
            System.out.println("Using standard repetitive plaintext (" + sampleText.length() + " chars).");
        }
        CryptoAnalysis.runFullReport(sampleText, pw);
    }
}
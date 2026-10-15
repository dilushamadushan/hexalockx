public class BitTwist {

    public static int rotl(int b, int n) {
        return ((b << n) | (b >> (8 - n))) & 0xFF;
    }

    public static int rotr(int b, int n) {
        return ((b >> n) | (b << (8 - n))) & 0xFF;
    }

    public static int amount(int keyByte) {
        return keyByte % 7 + 1;
    }

    public static void encrypt(int[] block, int[] roundKey, int round) {
        for (int i = 0; i < block.length; i++) {
            block[i] = rotl(block[i], amount(roundKey[i]));
            if (round % 2 == 1) {
                block[i] = 255 - block[i];
            }
        }
    }

    public static void decrypt(int[] block, int[] roundKey, int round) {
        for (int i = 0; i < block.length; i++) {
            if (round % 2 == 1) {
                block[i] = 255 - block[i];
            }
            block[i] = rotr(block[i], amount(roundKey[i]));
        }
    }

    public static String toBinary(int b) {
        String s = Integer.toBinaryString(b & 0xFF);
        return "00000000".substring(s.length()) + s;
    }

    public static void printRotationTable(int[] roundKey) {
        StringBuilder sb = new StringBuilder();
        for (int v : roundKey) sb.append(amount(v)).append(' ');
        System.out.println("Rotations: " + sb.toString().trim());
    }
}

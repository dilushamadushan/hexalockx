# HexaLock-X

HexaLock-X is a custom 128-bit symmetric block-cipher project written in
standard Java. It is intended for learning how key derivation, substitution,
permutation, diffusion, block chaining, padding, integrity checks, and basic
cryptanalysis measurements fit together.

> **Important:** HexaLock-X is an educational cipher, not a replacement for
> AES-GCM, ChaCha20-Poly1305, or another reviewed cryptographic library. The
> algorithm has not received professional cryptanalysis and must not protect
> real secrets.

## 1. Requirements and quick start

The project is deliberately flat and has no external dependencies or build
tool configuration. A JDK is required.

```text
javac *.java
java SelfTest
java Main
java CryptoAnalysis
```

Run the commands from the directory containing the `.java` files. `SelfTest`
checks the primitive transformations, complete encrypt/decrypt round trips,
wrong-password rejection, tamper detection, and statistical properties.

## 2. Interactive application

`Main` displays a boxed menu with the following options:

| Choice | Operation |
|---|---|
| 1 | Encrypt text |
| 2 | Decrypt Base64 ciphertext |
| 3 | Run a one-bit avalanche demonstration |
| 4 | Display derived-key information, the S-box, and round-1 layouts |
| 5 | Demonstrate tamper detection |
| 6 | Run the entropy, chi-square, histogram, and 1000-trial avalanche report |
| 7 | Exit |

### Encrypting text

1. Select `1`.
2. Enter the plaintext.
3. Enter a password of at least four characters.
4. Choose whether to print the first-block trace.
5. Copy the resulting Base64 ciphertext.

The password-strength score is only a usability indicator. It has six possible
points: length at least 8, length at least 12, lowercase, uppercase, digit,
and special character. It does not prove that a password is safe.

### Decrypting text

1. Select `2`.
2. Paste the complete Base64 ciphertext, including its salt, IV, encrypted
   blocks, and tag.
3. Enter the same password.

The program verifies the integrity tag before decrypting. A wrong password,
modified ciphertext, invalid length, invalid Base64, or invalid padding causes
an error instead of returning plaintext.

## 3. High-level algorithm

For a concise standalone description of the algorithm, see
[ALGORITHM.md](doc/ALGORITHM.md).
For the runtime class/method execution order, see
[CODE_EXECUTION_ORDER.md](doc/CODE_EXECUTION_ORDER.md).
Contribution and testing guidance is available in
[CONTRIBUTING.md](doc/CONTRIBUTING.md).

HexaLock-X uses 16-byte blocks (128 bits) and eight rounds. Every byte is
represented internally as an integer in the range `0..255`.

The complete encryption pipeline is:

```text
plaintext
  -> UTF-8 bytes
  -> PKCS-style padding to a multiple of 16 bytes
  -> split into 16-byte blocks
  -> for each block:
       XOR with IV (first block) or previous ciphertext block
       encryptBlock()
  -> prepend salt and IV
  -> calculate a 16-byte custom MAC over salt || IV || ciphertext
  -> append MAC
  -> Base64 encode
```

The chaining is CBC-like:

```text
C[i] = E(K, P[i] XOR previous)
previous = IV                 when i == 0
previous = C[i - 1]           otherwise
```

This project calls the construction CBC-like because the block cipher and tag
are custom implementations, not a standardized authenticated-encryption
scheme.

## 4. Key setup

For every encryption, the program generates a new random 16-byte salt and a
new random 16-byte IV with Java `SecureRandom`. This means encrypting the same
plaintext with the same password twice normally produces different
ciphertexts.

### 4.1 Password stretching

`KeyDerivation.stretch` creates a 16-byte master key:

1. Initialize each key byte from a repeating password byte plus its index.
2. Repeat 1,000 times.
3. For each byte `i`, calculate:

   ```text
   v = (s[i] + s[(i+1) mod 16] + salt[i] + iteration) mod 256
   rotation = (s[(i+5) mod 16] mod 7) + 1
   s[i] = rotate_left_8_bits(v, rotation)
   ```

The salt makes the derived key different for different encryptions. The
stretching function is a project-specific construction; it is not PBKDF2,
scrypt, Argon2, or a formally specified password hashing standard.

### 4.2 Round keys

Eight 16-byte round keys are derived from the master key. For every round `r`
and byte position `i`:

```text
state[i] = (5 * state[i] + 17 + state[(i+3) mod 16] + r) mod 256
roundKey[r][i] = state[i]
```

The previous round's state is used as the input to the next round.

### 4.3 Key-dependent S-box

`SBox.generate` starts with the values `0..255` and performs a deterministic
Fisher-Yates-style shuffle seeded from the master key. The result is a
permutation of all byte values, so it has an inverse table:

```text
inverse[S-box[x]] = x
```

The inverse table is used during decryption.

## 5. The eight-round block cipher

`HexaLockX.encryptBlock` clones a 16-byte input block and applies the
following operations in order during each of the eight rounds:

```text
1. S-box substitution
2. Bit twist
3. Keyed zigzag permutation
4. Forward diffusion mix
5. XOR with the round key
```

For a round `r`, where `r` is zero-based, the pseudocode is:

```text
b = input
for r = 0 .. 7:
    b = SBox.substitute(b, sbox)
    b = BitTwist.encrypt(b, roundKey[r], r)
    b = BlockZigzag.encrypt(b, roundKey[r])
    b = DiffusionMix.forward(b)
    b[i] = b[i] XOR roundKey[r][i] for every i
return b
```

### 5.1 S-box substitution

Each byte is replaced independently:

```text
b[i] = sbox[b[i]]
```

This provides nonlinear substitution. Since the S-box is a permutation,
decryption uses `inverseSbox` to reverse it.

### 5.2 Bit twist

For each byte:

```text
rotation = (roundKey[i] mod 7) + 1
b[i] = rotate_left_8_bits(b[i], rotation)
```

On odd-numbered rounds (rounds 2, 4, 6, and 8 in human numbering), the value
is also complemented:

```text
b[i] = 255 - b[i]
```

Decryption complements first on those rounds and then rotates right by the
same amount.

### 5.3 Keyed zigzag permutation

This step changes byte positions rather than byte values:

1. Select a rail count using `roundKey[0] mod 5 + 2`, producing 2 to 6 rails.
2. Visit the 16 positions in rail-fence zigzag order.
3. Generate a second deterministic permutation from the round key.
4. Apply the key permutation to the zigzag output.

Decryption applies both permutations in reverse order. No data is lost because
both operations are permutations.

### 5.4 Diffusion mix

The forward mix uses modular addition in two passes:

```text
for i = 1 .. 15:
    b[i] = (b[i] + b[i-1]) mod 256

for i = 14 .. 0:
    b[i] = (b[i] + b[i+1]) mod 256
```

This causes a change in one byte to influence many other bytes. The inverse
uses the corresponding subtraction passes in reverse order:

```text
for i = 0 .. 14:
    b[i] = (b[i] - b[i+1]) mod 256

for i = 15 .. 1:
    b[i] = (b[i] - b[i-1]) mod 256
```

### 5.5 Round-key XOR

The final operation in each encryption round is:

```text
b[i] = b[i] XOR roundKey[r][i]
```

During decryption, the eight rounds run in reverse order and undo operations
in reverse order:

```text
for r = 7 .. 0:
    b = b XOR roundKey[r]
    b = DiffusionMix.inverse(b)
    b = BlockZigzag.decrypt(b, roundKey[r])
    b = BitTwist.decrypt(b, roundKey[r], r)
    b = SBox.substitute(b, inverseSbox)
```

## 6. Padding, ciphertext format, and integrity

### 6.1 Padding

The input is UTF-8 encoded and padded using a PKCS-style rule. If `n` bytes
are needed to reach the next 16-byte boundary, `n` bytes with value `n` are
appended. A full 16-byte plaintext receives a complete 16-byte padding block.
Therefore even an empty plaintext produces one encrypted block.

### 6.2 Binary ciphertext layout

Before Base64 encoding, the binary output has this exact layout:

```text
offset 0..15                         16-byte salt
offset 16..31                       16-byte IV
offset 32..(N-17)                   encrypted blocks
offset (N-16)..(N-1)                16-byte integrity tag
```

The encrypted-block section is always a positive multiple of 16 bytes. The
smallest valid binary ciphertext is 64 bytes:

```text
16 salt + 16 IV + 16 ciphertext + 16 tag
```

The complete binary value is Base64 encoded for convenient terminal and text
file transport.

### 6.3 Custom integrity tag

The tag is calculated over `salt || IV || ciphertext` using the derived master
key. It starts with a copy of the master key, processes every input byte with
XOR, rotation, addition, and index-dependent mixing, then performs 32 final
mixing repetitions.

Decryption:

1. Decodes Base64 and validates the length.
2. Extracts salt, IV, ciphertext, and tag.
3. Rebuilds the key using the supplied password and extracted salt.
4. Recomputes the tag.
5. Compares tags without early exit.
6. Only then decrypts and removes padding.

The tag detects accidental changes and rejects the wrong password. It is a
custom MAC-like construction and must not be treated as a standardized MAC.

## 7. Diagnostics and cryptanalysis tools

### Avalanche test

Menu option `3` encrypts two blocks differing by one input bit and counts
changed output bits out of 128. It also measures the diffusion mix by itself.
For a good avalanche effect, a full cipher should approach 64 changed bits
on average.

### Key information

Menu option `4` displays:

- a short master-key fingerprint;
- all eight round keys;
- whether the generated S-box is a valid permutation;
- the number of S-box fixed points;
- the round-1 rail layout;
- byte rotation amounts.

The fingerprint is for comparison while debugging; it is not a password or
key-recovery mechanism.

### Tamper test

Menu option `5` encrypts a sample, flips one ciphertext bit, and shows that
the original decrypts while the modified value is rejected by the tag.

### Cryptanalysis suite

Menu option `6`, or `java CryptoAnalysis`, reports:

- Shannon entropy, where the maximum for a byte stream is 8 bits per byte;
- chi-square distance from a uniform byte distribution;
- frequency histograms;
- comparison with Caesar and Vigenere examples;
- a 1,000-trial avalanche test using random 128-bit blocks.

These are educational measurements, not a proof of cryptographic security.
The benchmark itself uses a fresh `Random` instance for test data and should
not be confused with key or nonce generation.

## 8. Suggested workflow for modifying the algorithm

When changing a primitive, preserve its inverse at the same time:

1. Change the forward operation.
2. Derive and implement the exact inverse operation.
3. Add a focused round-trip test to `SelfTest`.
4. Run `javac *.java`.
5. Run `java SelfTest`.
6. Run the interactive avalanche and tamper demonstrations.
7. Re-run the cryptanalysis report and compare results with the previous
   version.

Useful invariants are:

```text
decryptBlock(encryptBlock(block, keys), keys) == block
unpad(pad(utf8Text)) == utf8Text
inverseSbox[sbox[x]] == x
decrypt(encrypt(text, password), password) == text
```

Changing the block size, salt/IV size, round count, or ciphertext layout is a
format change. Such a change requires coordinated updates to
`HexaLockX`, `ModeOfOperation`, tests, and this documentation.

## 9. Source-file map

| File | Responsibility |
|---|---|
| `Main.java` | Interactive command-line menu and demonstrations |
| `HexaLockX.java` | Key assembly, block rounds, CBC-like encryption/decryption |
| `KeyDerivation.java` | Password validation, stretching, round-key generation |
| `SBox.java` | Key-dependent S-box and inverse |
| `BitTwist.java` | Byte rotations and odd-round complement |
| `BlockZigzag.java` | Rail-fence and key-dependent position permutations |
| `DiffusionMix.java` | Forward/inverse modular diffusion and bit comparisons |
| `ModeOfOperation.java` | Padding, block handling, randomness, MAC, Base64 |
| `CryptoAnalysis.java` | Entropy, chi-square, histograms, and avalanche report |
| `SelfTest.java` | Automated unit and integration checks |
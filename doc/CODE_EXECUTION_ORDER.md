# HexaLock-X Code Execution Order

This document explains how the Java classes work together at runtime and
identifies the algorithm type used by each part of the system.

## 1. Algorithm type

HexaLock-X is a **custom symmetric-key block cipher** with these layers:

| Layer | Type used in this project |
|---|---|
| Main cipher | Custom 128-bit block cipher |
| Cipher design | Substitution-permutation network (SPN)-style design |
| Block size | 16 bytes / 128 bits |
| Number of rounds | 8 |
| Substitution | Key-dependent 256-byte S-box |
| Permutation | Keyed rail-fence zigzag plus key permutation |
| Diffusion | Modular-addition forward and inverse mixing |
| Multi-block mode | CBC-like chaining |
| Integrity | Custom 16-byte MAC/tag |
| Output encoding | Base64 |
| Password processing | Custom salted key-stretching routine |

It is not AES, DES, RSA, or a standard authenticated-encryption algorithm.
It is an educational design intended to demonstrate cryptographic building
blocks.

## 2. Java class responsibilities

| Class | Responsibility |
|---|---|
| `Main` | Interactive command-line menu and user input |
| `HexaLockX` | Main encryption, decryption, key setup, and block-round control |
| `KeyDerivation` | Password validation, master-key stretching, and round keys |
| `SBox` | Key-dependent substitution table and inverse table |
| `BitTwist` | Byte rotations and odd-round byte complementation |
| `BlockZigzag` | Keyed position permutation |
| `DiffusionMix` | Forward/inverse byte diffusion and bit-difference calculations |
| `ModeOfOperation` | Padding, block handling, XOR, random bytes, MAC, and Base64 |
| `CryptoAnalysis` | Entropy, chi-square, histogram, and avalanche reports |
| `SelfTest` | Automated correctness and behavior checks |

## 3. Program startup order

When the program is started with `java Main`, execution follows this order:

```text
Main.main()
  -> create Scanner
  -> display menu
  -> read menu choice
  -> call the selected do... method
  -> catch IllegalArgumentException and print an error
  -> repeat until option 7 or "exit"
```

`Main` does not perform the cryptographic transformations itself. It collects
input and delegates the work to the other classes.

## 4. Encryption execution order

Selecting option `1` calls:

```text
Main.main()
  -> Main.doEncrypt()
     -> read plaintext and password
     -> KeyDerivation.validatePassword()
     -> KeyDerivation.strengthScore()
     -> KeyDerivation.strengthLabel()
     -> HexaLockX.encrypt()
```

### 4.1 Preparing keys and random values

Inside `HexaLockX.encrypt()`:

```text
ModeOfOperation.randomBytes(16)  -> salt
ModeOfOperation.randomBytes(16)  -> IV
HexaLockX.buildKeys(password, salt)
  -> KeyDerivation.validatePassword()
  -> password.getBytes(UTF-8)
  -> KeyDerivation.stretch()
  -> KeyDerivation.roundKeys()
  -> SBox.generate()
  -> SBox.inverse()
```

The result is a `HexaLockX.Keys` object containing:

```text
master key
8 round keys
forward S-box
inverse S-box
```

### 4.2 Preparing plaintext blocks

The plaintext is converted and divided as follows:

```text
String plaintext
  -> UTF-8 byte array
  -> ModeOfOperation.pad()
  -> ModeOfOperation.split()
  -> 16-byte plaintext blocks
```

Padding uses a PKCS-style rule. If the plaintext already fills a complete
block, a complete 16-byte padding block is added.

### 4.3 Encrypting each block

For every plaintext block, `HexaLockX.encrypt()` performs:

```text
previous = IV                         for block 0
previous = previous ciphertext block  otherwise

mixedBlock = ModeOfOperation.xor(plaintextBlock, previous)
cipherBlock = HexaLockX.encryptBlock(mixedBlock, keys)
```

`encryptBlock()` then executes eight rounds. Every round has this exact order:

```text
1. SBox.substitute()
2. BitTwist.encrypt()
3. BlockZigzag.encrypt()
4. DiffusionMix.forward()
5. XOR with the current round key
```

The round pipeline is:

```text
input block
  -> S-box substitution
  -> key-controlled rotations
  -> complement on odd zero-based rounds
  -> zigzag/key permutation
  -> forward diffusion
  -> round-key XOR
  -> next round
```

After round 8, the resulting 16-byte block becomes the ciphertext block.

### 4.4 Building the final output

After all blocks are encrypted:

```text
ModeOfOperation.join(cipherBlocks)
  -> ciphertext bytes

body = salt || IV || ciphertext
tag = ModeOfOperation.mac(body, masterKey)
all = body || tag
output = ModeOfOperation.encodeBase64(all)
```

The returned string has this structure:

```text
Base64(
    16-byte salt
  || 16-byte IV
  || encrypted 16-byte blocks
  || 16-byte authentication tag
)
```

## 5. Decryption execution order

Selecting option `2` calls:

```text
Main.main()
  -> Main.doDecrypt()
     -> read Base64 ciphertext and password
     -> HexaLockX.decrypt()
```

Inside `HexaLockX.decrypt()`:

```text
ModeOfOperation.decodeBase64()
  -> validate minimum length and block alignment
  -> extract salt
  -> extract IV
  -> extract ciphertext
  -> extract supplied tag
  -> HexaLockX.buildKeys(password, salt)
  -> ModeOfOperation.mac(body, masterKey)
  -> ModeOfOperation.macEquals(suppliedTag, calculatedTag)
```

If the tag does not match, decryption stops with:

```text
Wrong password or data was tampered with
```

If the tag matches, each ciphertext block is processed in this order:

```text
HexaLockX.decryptBlock(cipherBlock, keys)
  -> reverse round 8 to reverse round 1
     -> XOR with round key
     -> DiffusionMix.inverse()
     -> BlockZigzag.decrypt()
     -> BitTwist.decrypt()
     -> SBox.substitute(inverseSBox)

plaintextBlock = decryptedBlock XOR IV/previous ciphertext
```

Finally:

```text
ModeOfOperation.join(plaintextBlocks)
  -> ModeOfOperation.unpad()
  -> UTF-8 String
  -> returned plaintext
```

## 6. Other execution paths

### Avalanche demonstration: option 3

```text
Main.doAvalanche()
  -> HexaLockX.buildKeys()
  -> create two blocks differing by one bit
  -> HexaLockX.encryptBlock() for both blocks
  -> DiffusionMix.bitDifference()
  -> DiffusionMix.avalancheTest()
```

This measures how many output bits change after a one-bit input change.

### Key information: option 4

```text
Main.doKeyInfo()
  -> HexaLockX.buildKeys()
  -> KeyDerivation.fingerprint()
  -> KeyDerivation.printRoundKeys()
  -> SBox.isPermutation()
  -> SBox.fixedPoints()
  -> SBox.printGrid()
  -> BlockZigzag.printGrid()
  -> BitTwist.printRotationTable()
```

### Tamper demonstration: option 5

```text
Main.doTamper()
  -> HexaLockX.encrypt()
  -> decode Base64
  -> modify one ciphertext byte
  -> encode Base64
  -> HexaLockX.decrypt()
  -> MAC comparison rejects the modified data
```

### Cryptanalysis suite: option 6

```text
Main.doCryptoAnalysis()
  -> choose standard or custom sample text
  -> CryptoAnalysis.runFullReport()
     -> create Caesar comparison
     -> create Vigenere comparison
     -> encrypt with HexaLock-X
     -> calculate entropy
     -> calculate chi-square uniformity
     -> print byte histograms
     -> run 1,000 avalanche trials
```

### Self-test execution

Running `java SelfTest` directly tests the components in increasing scope:

```text
key derivation
  -> S-box and inverse
  -> bit twist
  -> zigzag permutation
  -> diffusion mix
  -> padding and Base64
  -> block encryption/decryption
  -> complete text encryption/decryption
  -> random ciphertext behavior
  -> wrong-password rejection
  -> tamper rejection
  -> statistical analysis
```

## 7. Decryption round order

Encryption applies operations from left to right. Decryption must apply the
inverse operations from right to left:

```text
Encryption round:
S-box -> BitTwist -> Zigzag -> Diffusion -> XOR key

Decryption round:
XOR key -> Inverse Diffusion -> Inverse Zigzag
          -> Inverse BitTwist -> Inverse S-box
```

The eight rounds are also processed in reverse order, from round 8 back to
round 1. This ordering is what makes the block transformation reversible.

## Security note

The code demonstrates the structure of a cryptographic system, but the
algorithm is custom and has not been professionally reviewed. The custom
password stretching, block cipher, and MAC must not be used to protect real
secrets. For real applications, use a reviewed authenticated-encryption
library such as AES-GCM or ChaCha20-Poly1305.

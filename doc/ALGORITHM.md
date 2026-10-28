# HexaLock-X Algorithm

## Overview

HexaLock-X is an educational 128-bit symmetric block cipher written in
standard Java. It encrypts UTF-8 text in 16-byte blocks and uses eight rounds
of substitution, bit transformations, permutation, diffusion, and round-key
mixing.

The complete process is:

```text
plaintext
  -> UTF-8 bytes
  -> PKCS-style padding to 16-byte blocks
  -> CBC-like XOR with the IV or previous ciphertext block
  -> eight-round block encryption
  -> salt || IV || ciphertext
  -> custom 16-byte integrity tag
  -> Base64
```

The output format is therefore:

```text
Base64(salt[16] || IV[16] || ciphertext[n] || tag[16])
```

`n` is a positive multiple of 16. The salt and IV are generated with
`SecureRandom`, so encrypting the same text and password normally produces
different output.

## 1. Key setup

The password and 16-byte salt produce a 16-byte master key. The
`KeyDerivation.stretch` method initializes the key from the password and then
performs 1,000 rounds of byte addition, salt mixing, and 8-bit rotation:

```text
v        = (s[i] + s[(i+1) mod 16] + salt[i] + iteration) mod 256
rotation = (s[(i+5) mod 16] mod 7) + 1
s[i]     = rotate_left_8_bits(v, rotation)
```

Eight 16-byte round keys are then generated from the master key:

```text
state[i] = (5 * state[i] + 17 + state[(i+3) mod 16] + round) mod 256
roundKey[round][i] = state[i]
```

Finally, a key-dependent S-box is created by shuffling the values `0..255`.
Its inverse is saved for decryption.

## 2. Block encryption

Each input block is 16 bytes. Every one of the eight rounds performs these
operations in order:

```text
for round = 0 .. 7:
    substitute every byte through the key-dependent S-box
    rotate every byte using its round-key byte
    complement bytes on odd zero-based rounds
    apply the keyed zigzag permutation
    apply the forward diffusion mix
    XOR every byte with the round key
```

### S-box substitution

Each byte is replaced independently:

```text
block[i] = sbox[block[i]]
```

The S-box is a permutation, so decryption uses the inverse lookup table.

### Bit twist

The rotation amount for byte `i` is:

```text
rotation = (roundKey[i] mod 7) + 1
```

The byte is rotated left by this amount. On zero-based odd rounds, it is also
replaced with `255 - byte`. Decryption reverses the complement and then
rotates right.

### Keyed zigzag permutation

The 16 byte positions are first read in a rail-fence zigzag order. The number
of rails is between 2 and 6:

```text
rails = roundKey[0] mod 5 + 2
```

A second deterministic permutation derived from the round key is applied to
the zigzag result. Both operations only rearrange positions, so they can be
reversed exactly.

### Diffusion mix

The forward mix uses two modular-addition passes:

```text
for i = 1 .. 15:
    block[i] = (block[i] + block[i-1]) mod 256

for i = 14 .. 0:
    block[i] = (block[i] + block[i+1]) mod 256
```

The inverse uses the matching subtraction passes in reverse order. This
spreads a change in one byte across the block.

## 3. Multi-block encryption

Plaintext is padded so its length is a multiple of 16 bytes. For each block:

```text
previous = IV                         for the first block
previous = ciphertext[i - 1]          otherwise
ciphertext[i] = encryptBlock(plaintext[i] XOR previous, keys)
```

This is CBC-like chaining. During decryption, each ciphertext block is
decrypted first and then XORed with the IV or previous ciphertext block.

## 4. Integrity protection and decryption

The program calculates a custom 16-byte tag over:

```text
salt || IV || ciphertext
```

The tag uses the master key, byte-wise XOR, rotations, modular addition, and
32 final mixing passes. `macEquals` compares the tag without returning early
when bytes differ.

Decryption follows this order:

1. Base64-decode and validate the ciphertext length.
2. Read the salt, IV, ciphertext, and supplied tag.
3. Derive the master key from the password and salt.
4. Recalculate and compare the tag.
5. Decrypt and unchain every block.
6. Validate and remove padding.
7. Decode the result as UTF-8.

Wrong passwords or modified data are rejected before plaintext is returned.

## 5. Built-in evaluation

`SelfTest.java` checks:

- S-box permutation and inverse behavior
- Bit-twist, zigzag, and diffusion reversibility
- Full block and text round trips
- Randomized salt/IV behavior
- Wrong-password and tamper rejection
- Avalanche behavior
- Entropy and chi-square calculations

`CryptoAnalysis.java` provides avalanche, entropy, chi-square, and byte
histogram measurements. These measurements are useful for learning and
comparison, but they do not prove that the cipher is secure.

## Security warning

HexaLock-X is a custom educational algorithm and has not received professional
cryptanalysis. Its password stretching and MAC are project-specific rather
than standard constructions such as Argon2, PBKDF2, HMAC, or an authenticated
encryption mode. Do not use it to protect real secrets; use a reviewed
standard such as AES-GCM or ChaCha20-Poly1305 instead.

# Contributing to HexaLock-X

Thank you for helping improve HexaLock-X. This project is an educational
Java implementation of a custom symmetric block cipher, so contributions
should preserve clarity, reproducibility, and the project's learning purpose.

## Project layout

The project intentionally uses a flat Java source layout:

```text
HexaLockX.java          Main encryption and decryption flow
KeyDerivation.java      Password processing and round-key generation
SBox.java               Key-dependent substitution table
BitTwist.java           Byte rotations and complements
BlockZigzag.java        Keyed byte-position permutation
DiffusionMix.java       Reversible diffusion operations
ModeOfOperation.java    Padding, blocks, MAC, and Base64 handling
CryptoAnalysis.java     Statistical and avalanche analysis
SelfTest.java           Automated checks
Main.java               Interactive command-line application
doc/                    Project documentation
```

## Before making a change

1. Read the relevant source class and the existing documentation.
2. Check the current behavior with the self-test:

   ```text
   javac *.java
   java SelfTest
   ```

3. Keep the change focused on one feature, bug, or documentation improvement.
4. Do not commit generated `.class` files, secrets, passwords, or real
   private data.

## Coding guidelines

- Use standard Java and the existing project style.
- Keep the cryptographic pipeline reversible when changing a primitive.
- Preserve the 16-byte block size unless the change explicitly requires a
  redesign.
- Validate invalid input and report useful errors rather than silently
  continuing.
- Reuse existing helper methods instead of duplicating padding, XOR, Base64,
  or block-processing logic.
- Keep public method behavior clear and update documentation when it changes.
- Add or update self-tests for every behavior change.

## Testing changes

Run the complete self-test after modifying encryption, decryption, key
derivation, padding, MAC, or block transformations:

```text
javac *.java
java SelfTest
```

At minimum, verify:

- The project compiles without errors.
- Existing plaintexts decrypt to their original text.
- Empty, short, block-sized, and multi-block inputs work.
- Wrong passwords are rejected.
- Modified ciphertext is rejected.
- Primitive encrypt/decrypt operations remain reversible.

For changes to the analysis suite, also run:

```text
java CryptoAnalysis
```

## Documentation changes

Keep documentation in the `doc` folder:

- [ALGORITHM.md](ALGORITHM.md) describes the cipher construction.
- [CODE_EXECUTION_ORDER.md](CODE_EXECUTION_ORDER.md) describes runtime class
  and method order.
- This file describes contribution and validation practices.

Update the README when adding a new document or changing a common workflow.
Use short examples that match the actual source code.

## Pull request or change description

A useful change description should include:

1. What was changed.
2. Why the change was needed.
3. Which files were affected.
4. How the change was tested.
5. Any known limitations or security considerations.

## Security limitations

HexaLock-X is a custom educational cipher and has not received professional
cryptanalysis. Do not contribute changes that describe it as suitable for
protecting real secrets. For production security, recommend a reviewed
standard such as AES-GCM or ChaCha20-Poly1305.

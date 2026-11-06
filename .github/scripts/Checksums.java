import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Writes a signed {@code checksums.txt} next to the release files.
 * <p>
 * Usage: {@code java Checksums.java <title> <version> <channel> <commit> <api-version> <file>...}.
 * The ed25519 key is read from {@code SIGNING_KEY} as base64 PKCS#8, and the signature goes to
 * {@code checksums.txt.sig} as base64.
 */
public final class Checksums {

    public static void main(final String[] args) throws Exception {
        if (args.length < 6) {
            throw new IllegalArgumentException("usage: Checksums <title> <version> <channel> <commit> <api-version> <file>...");
        }
        final String key = System.getenv("SIGNING_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("SIGNING_KEY is not set");
        }

        final StringBuilder text = new StringBuilder()
                .append("# ").append(args[0]).append(" checksums\n")
                .append("#\n")
                .append("# version: ").append(args[1]).append('\n')
                .append("# channel: ").append(args[2]).append('\n')
                .append("# commit: ").append(args[3]).append('\n')
                .append("# date: ").append(Instant.now().truncatedTo(ChronoUnit.SECONDS)).append('\n')
                .append("# api-version: ").append(args[4]).append('\n')
                .append("#\n");

        Path directory = null;
        for (int i = 5; i < args.length; i++) {
            final Path file = Path.of(args[i]);
            directory = file.toAbsolutePath().getParent();
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            text.append(HexFormat.of().formatHex(digest)).append("  ").append(file.getFileName()).append('\n');
        }

        final byte[] data = text.toString().getBytes(StandardCharsets.UTF_8);
        final PrivateKey privateKey = KeyFactory.getInstance("Ed25519")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(key.strip())));
        final Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(data);

        Files.write(directory.resolve("checksums.txt"), data);
        Files.writeString(directory.resolve("checksums.txt.sig"),
                Base64.getEncoder().encodeToString(signer.sign()) + "\n", StandardCharsets.US_ASCII);
        System.out.print(text);
    }

}

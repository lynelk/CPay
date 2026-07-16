package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SqlSafetyTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java/net/citotech/cito");

    @Test
    void sourceDoesNotConcatenatePrimaryKeySqlPredicates() throws IOException {
        Pattern unsafeIdPredicate = Pattern.compile(
                "WHERE\\s+id\\s*=\\s*['\\\"][^\\r\\n;]*\\+",
                Pattern.CASE_INSENSITIVE);

        try (Stream<Path> sources = Files.walk(SOURCE_ROOT)) {
            assertThat(sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, unsafeIdPredicate))
                    .map(SOURCE_ROOT::relativize)
                    .map(Path::toString)
                    .toList())
                    .isEmpty();
        }
    }

    @Test
    void transactionQueriesUseTheCanonicalTransactionMapper() throws IOException {
        Pattern duplicateTransactionMapper = Pattern.compile("new\\s+RowMapper\\s*<\\s*Transaction\\s*>\\s*\\(");

        try (Stream<Path> sources = Files.walk(SOURCE_ROOT)) {
            assertThat(sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, duplicateTransactionMapper))
                    .map(SOURCE_ROOT::relativize)
                    .map(Path::toString)
                    .toList())
                    .isEmpty();
        }
    }

    private static boolean contains(Path path, Pattern pattern) {
        try {
            return pattern.matcher(Files.readString(path)).find();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect " + path, exception);
        }
    }
}

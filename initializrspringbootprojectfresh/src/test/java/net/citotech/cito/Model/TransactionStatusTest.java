package net.citotech.cito.Model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TransactionStatusTest {

    @Test
    void terminalStatusesCannotTransitionAgain() {
        assertThat(TransactionStatus.SUCCESSFUL.canTransitionTo(TransactionStatus.FAILED)).isFalse();
        assertThat(TransactionStatus.FAILED.canTransitionTo(TransactionStatus.SUCCESSFUL)).isFalse();
        assertThat(TransactionStatus.SUCCESSFUL.isTerminal()).isTrue();
        assertThat(TransactionStatus.FAILED.isTerminal()).isTrue();
    }

    @Test
    void nonTerminalStatusesCanTransition() {
        assertThat(TransactionStatus.PENDING.canTransitionTo(TransactionStatus.SUCCESSFUL)).isTrue();
        assertThat(TransactionStatus.UNDETERMINED.canTransitionTo(TransactionStatus.FAILED)).isTrue();
        assertThat(TransactionStatus.PENDING.isTerminal()).isFalse();
    }

    @Test
    void fromStringIsCaseInsensitiveAndNullSafe() {
        assertThat(TransactionStatus.fromString("successful")).isEqualTo(TransactionStatus.SUCCESSFUL);
        assertThat(TransactionStatus.fromString("PENDING")).isEqualTo(TransactionStatus.PENDING);
        assertThat(TransactionStatus.fromString("bogus")).isNull();
        assertThat(TransactionStatus.fromString(null)).isNull();
    }
}

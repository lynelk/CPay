package net.citotech.cito.Model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TransactionStatusTest {

    @Test
    void terminalStatusesCannotTransitionAgain() {
        assertThat(TransactionStatus.SUCCESSFUL.canTransitionTo(TransactionStatus.FAILED))
                .isFalse();
        assertThat(TransactionStatus.FAILED.canTransitionTo(TransactionStatus.SUCCESSFUL))
                .isFalse();
        assertThat(TransactionStatus.REVERSED.canTransitionTo(TransactionStatus.SUCCESSFUL))
                .isFalse();
        assertThat(TransactionStatus.CANCELLED.canTransitionTo(TransactionStatus.PENDING))
                .isFalse();
        assertThat(TransactionStatus.SUCCESSFUL.isTerminal()).isTrue();
        assertThat(TransactionStatus.FAILED.isTerminal()).isTrue();
        assertThat(TransactionStatus.REVERSED.isTerminal()).isTrue();
        assertThat(TransactionStatus.CANCELLED.isTerminal()).isTrue();
    }

    @Test
    void validProviderAndRepairTransitionsAreAllowed() {
        assertThat(TransactionStatus.RECEIVED.canTransitionTo(TransactionStatus.VALIDATED))
                .isTrue();
        assertThat(TransactionStatus.VALIDATED.canTransitionTo(TransactionStatus.AUTHORIZED))
                .isTrue();
        assertThat(TransactionStatus.AUTHORIZED.canTransitionTo(TransactionStatus.RESERVED))
                .isTrue();
        assertThat(TransactionStatus.RESERVED.canTransitionTo(TransactionStatus.SENT_TO_PROVIDER))
                .isTrue();
        assertThat(TransactionStatus.SENT_TO_PROVIDER.canTransitionTo(TransactionStatus.PENDING))
                .isTrue();
        assertThat(TransactionStatus.PENDING.canTransitionTo(TransactionStatus.SUCCESSFUL))
                .isTrue();
        assertThat(TransactionStatus.PENDING.canTransitionTo(TransactionStatus.FAILED)).isTrue();
        assertThat(TransactionStatus.PENDING.canTransitionTo(TransactionStatus.UNDETERMINED))
                .isTrue();
        assertThat(TransactionStatus.UNDETERMINED.canTransitionTo(TransactionStatus.FAILED))
                .isTrue();
        assertThat(TransactionStatus.UNDETERMINED.canTransitionTo(TransactionStatus.SUCCESSFUL))
                .isTrue();
        assertThat(TransactionStatus.PENDING.isTerminal()).isFalse();
    }

    @Test
    void invalidBackwardOrSkippedTransitionsAreRejected() {
        assertThat(TransactionStatus.PENDING.canTransitionTo(TransactionStatus.RECEIVED)).isFalse();
        assertThat(TransactionStatus.AUTHORIZED.canTransitionTo(TransactionStatus.RECEIVED))
                .isFalse();
        assertThat(TransactionStatus.SENT_TO_PROVIDER.canTransitionTo(TransactionStatus.RESERVED))
                .isFalse();
        assertThat(TransactionStatus.RECEIVED.canTransitionTo(TransactionStatus.SUCCESSFUL))
                .isFalse();
    }

    @Test
    void sameNonTerminalStatusCanBeAppliedIdempotently() {
        assertThat(TransactionStatus.PENDING.canTransitionTo(TransactionStatus.PENDING)).isTrue();
        assertThat(TransactionStatus.UNDETERMINED.canTransitionTo(TransactionStatus.UNDETERMINED))
                .isTrue();
    }

    @Test
    void fromStringIsCaseInsensitiveAndNullSafe() {
        assertThat(TransactionStatus.fromString("successful"))
                .isEqualTo(TransactionStatus.SUCCESSFUL);
        assertThat(TransactionStatus.fromString("PENDING")).isEqualTo(TransactionStatus.PENDING);
        assertThat(TransactionStatus.fromString("sent_to_provider"))
                .isEqualTo(TransactionStatus.SENT_TO_PROVIDER);
        assertThat(TransactionStatus.fromString("bogus")).isNull();
        assertThat(TransactionStatus.fromString(null)).isNull();
    }
}

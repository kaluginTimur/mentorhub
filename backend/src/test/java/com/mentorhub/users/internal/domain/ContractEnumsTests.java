package com.mentorhub.users.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.api.model.HistoryAction;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Enums whose Javadoc requires them to equal an enum of the API contract. */
class ContractEnumsTests {

    @Test
    void userAuditActionsEqualTheContractsHistoryActions() {
        assertThat(Arrays.stream(UserAuditAction.values()).map(Enum::name))
                .containsExactly(
                        Arrays.stream(HistoryAction.values()).map(Enum::name).toArray(String[]::new));
    }

    @Test
    void invitationStatusesEqualTheContracts() {
        assertThat(Arrays.stream(InvitationStatus.values()).map(Enum::name))
                .containsExactly(Arrays.stream(com.mentorhub.api.model.InvitationStatus.values())
                        .map(Enum::name)
                        .toArray(String[]::new));
    }
}

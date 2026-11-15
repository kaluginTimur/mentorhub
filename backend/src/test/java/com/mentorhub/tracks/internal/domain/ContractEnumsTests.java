package com.mentorhub.tracks.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.api.model.TrackHistoryAction;
import com.mentorhub.api.model.TrackStatusFilter;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Enums whose Javadoc requires them to equal an enum of the API contract. */
class ContractEnumsTests {

    @Test
    void trackAuditActionsEqualTheContractsTrackHistoryActions() {
        assertThat(Arrays.stream(TrackAuditAction.values()).map(Enum::name))
                .containsExactly(Arrays.stream(TrackHistoryAction.values())
                        .map(Enum::name)
                        .toArray(String[]::new));
    }

    @Test
    void trackStatusesEqualTheContracts() {
        assertThat(Arrays.stream(TrackStatus.values()).map(Enum::name))
                .containsExactly(Arrays.stream(com.mentorhub.api.model.TrackStatus.values())
                        .map(Enum::name)
                        .toArray(String[]::new));
    }

    @Test
    void trackStatusFiltersAreTheContractsTrackStatusesPlusAll() {
        assertThat(Arrays.stream(TrackStatusFilter.values()).map(Enum::name))
                .containsExactly(Stream.concat(
                                Arrays.stream(com.mentorhub.api.model.TrackStatus.values())
                                        .map(Enum::name),
                                Stream.of("ALL"))
                        .toArray(String[]::new));
    }
}

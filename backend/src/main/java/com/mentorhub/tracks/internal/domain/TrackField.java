package com.mentorhub.tracks.internal.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/** An editable field of a track; the JSON names are the ones the audit details and the contract use. */
public enum TrackField {
    @JsonProperty("code")
    CODE,
    @JsonProperty("name")
    NAME
}

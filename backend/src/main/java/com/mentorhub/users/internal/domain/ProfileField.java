package com.mentorhub.users.internal.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/** An editable profile field; the JSON names are the ones the audit details and the contract use. */
public enum ProfileField {
    @JsonProperty("firstName")
    FIRST_NAME,
    @JsonProperty("lastName")
    LAST_NAME,
    @JsonProperty("phone")
    PHONE
}

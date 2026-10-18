package com.mentorhub.users.internal.service;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param phoneDefaultRegion the ISO 3166 region used to parse phones written without {@code +}
 */
@ConfigurationProperties("mentorhub.users")
record UsersProperties(@DefaultValue("KZ") String phoneDefaultRegion) {

    UsersProperties {
        phoneDefaultRegion = phoneDefaultRegion.strip().toUpperCase(Locale.ROOT);
        if (!PhoneNumberUtil.getInstance().getSupportedRegions().contains(phoneDefaultRegion)) {
            throw new IllegalArgumentException(
                    "mentorhub.users.phone-default-region: '" + phoneDefaultRegion + "' is not a supported region");
        }
    }
}

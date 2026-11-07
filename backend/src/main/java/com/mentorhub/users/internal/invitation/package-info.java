/**
 * Invitations: creating and managing them, the public preview of a link, and acceptance.
 *
 * <p>They live in {@code users}, not in a module of their own: linking identities, reactivation, the gate exemption
 * and the row locks are internals of {@code users}, and a separate module would need a public API to link an identity
 * to a user, which only an invitation or the bootstrap may do (IDN-4).
 */
@NullMarked
package com.mentorhub.users.internal.invitation;

import org.jspecify.annotations.NullMarked;

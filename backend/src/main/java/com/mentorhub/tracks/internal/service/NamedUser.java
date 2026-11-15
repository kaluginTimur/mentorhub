package com.mentorhub.tracks.internal.service;

import java.util.UUID;

/** A user as a track's history names them, with the name they have now. */
public record NamedUser(UUID id, String name) {}

package com.mentorhub.users.internal.service;

import java.util.UUID;

/** @param name first and last name joined by a space, or the email when both are missing */
public record HistoryActor(UUID id, String name) {}

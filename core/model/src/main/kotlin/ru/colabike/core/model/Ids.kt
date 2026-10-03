package ru.colabike.core.model

/** Stable keys of the API (UUID strings). A username is not a key: it can change. */
@JvmInline value class BikeId(val value: String)

@JvmInline value class UserId(val value: String)

@JvmInline value class RideId(val value: String)

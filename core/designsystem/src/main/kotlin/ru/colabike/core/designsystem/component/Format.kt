package ru.colabike.core.designsystem.component

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** "Cube Travel SL · 2020": what a bike is, under its own name. */
fun bikeSubtitle(brand: String, model: String, year: Int?): String =
    listOfNotNull("$brand $model".trim().ifBlank { null }, year?.toString()).joinToString(" · ")

internal fun kilometers(meters: Int, locale: Locale): String =
    NumberFormat.getNumberInstance(locale)
        .apply { maximumFractionDigits = 1 }
        .format(meters / 1000.0)

internal fun date(instant: Instant, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .withZone(zone)
        .format(instant)

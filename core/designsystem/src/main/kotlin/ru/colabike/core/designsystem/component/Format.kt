package ru.colabike.core.designsystem.component

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
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

internal fun date(day: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(day)

internal fun integer(value: Int, locale: Locale): String =
    NumberFormat.getIntegerInstance(locale).format(value)

/** "2 500" for roubles, "2 500,5" when there are kopecks or cents: no more digits than needed. */
internal fun amount(value: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale)
        .apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
        .format(value)

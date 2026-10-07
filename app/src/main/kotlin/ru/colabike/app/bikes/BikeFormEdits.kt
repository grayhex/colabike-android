package ru.colabike.app.bikes

import ru.colabike.core.model.BikeRules
import ru.colabike.core.model.PriceVisibility

// What typing into a field of the form of a bike does to it: the text is cut one character past
// what the server takes (so a form that is too long says so, and is not silently shortened), and a
// number field never holds more characters than its biggest value. The same in every form that has
// these fields.

/** A number field never needs more characters than the biggest value it can hold. */
internal const val MAX_NUMBER_TEXT = 14

internal fun BikeForm.withName(value: String) = copy(name = value.take(BikeRules.MAX_NAME + 1))

internal fun BikeForm.withBrand(value: String) = copy(brand = value.take(BikeRules.MAX_BRAND + 1))

internal fun BikeForm.withModel(value: String) = copy(model = value.take(BikeRules.MAX_MODEL + 1))

internal fun BikeForm.withTrim(value: String) = copy(trim = value.take(BikeRules.MAX_TRIM + 1))

internal fun BikeForm.withYear(value: String) = copy(year = value.take(MAX_NUMBER_TEXT))

internal fun BikeForm.withDescription(value: String) =
    copy(description = value.take(BikeRules.MAX_DESCRIPTION + 1))

internal fun BikeForm.withColor(value: String) = copy(color = value.take(BikeRules.MAX_COLOR + 1))

internal fun BikeForm.withSize(value: String) = copy(size = value.take(BikeRules.MAX_SIZE + 1))

internal fun BikeForm.withWeight(value: String) = copy(weight = value.take(MAX_NUMBER_TEXT))

internal fun BikeForm.withMileage(value: String) = copy(mileage = value.take(MAX_NUMBER_TEXT))

internal fun BikeForm.withManufacturerUrl(value: String) =
    copy(manufacturerUrl = value.take(BikeRules.MAX_LINK + 1))

internal fun BikeForm.withPrice(value: String) = copy(price = value.take(MAX_NUMBER_TEXT))

internal fun BikeForm.withPriceVisibility(value: PriceVisibility) = copy(priceVisibility = value)

internal fun BikeForm.withFormer(value: Boolean) = copy(isFormer = value)

internal fun BikeForm.withPublic(value: Boolean) = copy(isPublic = value)

internal fun BikeForm.withElectric(value: Boolean) =
    copy(classification = classification.copy(electric = value))

internal fun BikeForm.withFatbike(value: Boolean) =
    copy(classification = classification.copy(fatbike = value))

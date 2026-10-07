package ru.colabike.app.bikes

import ru.colabike.core.model.CatalogOption
import ru.colabike.core.model.ClassificationCatalog
import ru.colabike.core.model.ClassificationDraft

// How the type of a bike changes when the person picks something. The rules are the site's and are
// the same in every form that has a type (the form of a bike, the wizard): a key outside the
// dictionaries in force is not taken from a pick, and a pick never leaves the type contradicting
// itself.

private fun keys(options: List<CatalogOption>) = options.map { it.key }

/** A new category takes its own subtypes: one of another category is not kept. */
internal fun ClassificationDraft.withCategory(
    key: String,
    types: ClassificationCatalog,
): ClassificationDraft =
    if (key == category || types.category(key) == null) this
    else
        copy(
            category = key,
            subtype = subtype?.takeIf { it in keys(types.subtypesOf(key)) },
        )

/** Choosing the same subtype again takes it back: none is a choice too. */
internal fun ClassificationDraft.withSubtype(
    key: String,
    types: ClassificationCatalog,
): ClassificationDraft =
    if (key !in keys(types.subtypesOf(category))) this
    else copy(subtype = key.takeIf { it != subtype })

internal fun ClassificationDraft.withSuspension(
    key: String,
    types: ClassificationCatalog,
): ClassificationDraft =
    if (key !in keys(types.suspensions)) this
    else copy(suspension = key.takeIf { it != suspension })

internal fun ClassificationDraft.withConstruction(
    key: String,
    types: ClassificationCatalog,
): ClassificationDraft =
    if (key !in keys(types.constructions)) this
    else copy(construction = key.takeIf { it != construction })

/**
 * Up to the limit of the site; one over is not added, and a chosen one is taken back by choosing
 * it.
 */
internal fun ClassificationDraft.withUseToggled(
    key: String,
    types: ClassificationCatalog,
): ClassificationDraft =
    when {
        key !in keys(types.uses) -> this
        key in uses -> copy(uses = uses - key)
        uses.size >= types.maxUses -> this
        else -> copy(uses = uses + key)
    }

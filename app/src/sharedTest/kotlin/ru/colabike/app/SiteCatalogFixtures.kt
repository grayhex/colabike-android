package ru.colabike.app

import ru.colabike.app.catalog.BuiltinCatalog
import ru.colabike.core.model.CatalogBrand
import ru.colabike.core.model.CatalogOption
import ru.colabike.core.model.CatalogPurpose
import ru.colabike.core.model.SiteCatalog

/** The dictionaries of a site, as the tests have them: the built-in types and a few of the rest. */
object SiteCatalogFixtures {
    val catalog: SiteCatalog =
        BuiltinCatalog.value.copy(
            version = 7,
            purposes = listOf(CatalogPurpose("city", "Город"), CatalogPurpose("sport", "Спорт")),
            brands =
                listOf(
                    CatalogBrand("Giant", listOf("Contend", "Defy", "Talon")),
                    CatalogBrand("Canyon", listOf("Grizl", "Neuron", "Endurace")),
                    CatalogBrand("Cube", listOf("Nuroad", "Reaction")),
                    CatalogBrand("Cannondale", listOf("Topstone", "Trail")),
                ),
            manufacturers = listOf("Shimano", "SRAM"),
            sizes = listOf("XS", "S", "M", "L", "XL"),
            components =
                BuiltinCatalog.value.components.copy(
                    names =
                        mapOf(
                            "Седло" to listOf("Fizik Antares", "Brooks B17", "Selle Italia SLR"),
                            "Групсет" to
                                listOf("Shimano 105", "Shimano GRX RX600", "SRAM Rival AXS"),
                        )
                ),
        )

    /** A dictionary of a site that renamed a kind of bike and added a subtype of its own. */
    fun renamed(): SiteCatalog =
        catalog.copy(
            classification =
                catalog.classification.copy(
                    categories =
                        catalog.classification.categories.map { category ->
                            if (category.key == "mtb")
                                category.copy(
                                    name = "Горные",
                                    subtypes = category.subtypes + CatalogOption("fat", "Фэтбайк"),
                                )
                            else category
                        }
                )
        )
}

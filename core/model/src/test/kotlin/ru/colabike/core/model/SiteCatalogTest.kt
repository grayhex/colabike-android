package ru.colabike.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SiteCatalogTest {
    private val dictionary =
        ComponentDictionary(
            groups =
                listOf(
                    ComponentDictionary.Group("frame", "Рама и подвеска", listOf("Рама", "Вилка")),
                    ComponentDictionary.Group(
                        "drivetrain",
                        "Трансмиссия",
                        listOf("Групсет", "Цепь"),
                    ),
                    ComponentDictionary.Group(
                        "equipment",
                        "Оборудование",
                        listOf("Звонок", "Замок"),
                    ),
                ),
            buildCategories = listOf("Рама", "Вилка", "Групсет", "Цепь"),
            accessoryCategories = listOf("Звонок", "Замок", "Другое"),
            names =
                mapOf(
                    "Групсет" to listOf("Shimano 105", "Shimano Ultegra", "SRAM Rival AXS"),
                    "Седло" to listOf("Fizik Antares", "Brooks B17"),
                ),
        )

    private val catalog =
        SiteCatalog(
            version = 5,
            classification =
                ClassificationCatalog(
                    categories =
                        listOf(
                            CatalogCategory(
                                "mtb",
                                "MTB",
                                listOf(CatalogOption("xc", "XC"), CatalogOption("trail", "Trail")),
                            ),
                            CatalogCategory("bmx", "BMX", emptyList()),
                        ),
                    suspensions = emptyList(),
                    constructions = emptyList(),
                    uses = emptyList(),
                    maxUses = 3,
                ),
            purposes = emptyList(),
            brands =
                listOf(
                    CatalogBrand("Giant", listOf("Contend", "Defy", "Talon")),
                    CatalogBrand("Canyon", listOf("Grizl", "Neuron")),
                    CatalogBrand("Cannondale", listOf("Topstone")),
                ),
            manufacturers = emptyList(),
            sizes = listOf("S", "M", "L"),
            components = dictionary,
        )

    // --- parts -----------------------------------------------------------------------------------

    @Test
    fun `a category typed in any case is the site's spelling, and one of the person's own stays`() {
        assertThat(dictionary.canonical(" звонок ")).isEqualTo("Звонок")
        assertThat(dictionary.canonical("Что-то своё ")).isEqualTo("Что-то своё")
    }

    @Test
    fun `a part is placed by the site's groups and sections, not by the built-in ones`() {
        assertThat(dictionary.groupIdOf("цепь")).isEqualTo("drivetrain")
        assertThat(dictionary.groupIdOf("Что-то своё")).isEmpty()
        assertThat(dictionary.sectionOf("Звонок")).isEqualTo("accessories")
        // "Other" is an accessory category of the site, with no group of its own.
        assertThat(dictionary.sectionOf("другое")).isEqualTo("accessories")
        assertThat(dictionary.sectionOf("Цепь")).isEqualTo("build")
        assertThat(dictionary.groupName("equipment")).isEqualTo("Оборудование")
        assertThat(dictionary.groupName("nothing")).isNull()
    }

    @Test
    fun `categories are suggested by what was typed, the ones that begin with it first`() {
        assertThat(dictionary.suggestions("ц")).containsExactly("Цепь").inOrder()
        assertThat(dictionary.suggestions("ам")).containsExactly("Рама", "Замок").inOrder()
        assertThat(dictionary.suggestions("зам")).containsExactly("Замок")
        assertThat(dictionary.suggestions("")).isEmpty()
        // A category that is already typed in full is not offered again.
        assertThat(dictionary.suggestions("Цепь")).isEmpty()
    }

    @Test
    fun `names are suggested within the category the person is in, in any case`() {
        assertThat(dictionary.nameSuggestions("групсет", "shimano"))
            .containsExactly("Shimano 105", "Shimano Ultegra")
            .inOrder()
        assertThat(dictionary.nameSuggestions("Групсет", "sram")).containsExactly("SRAM Rival AXS")
        assertThat(dictionary.nameSuggestions("Цепь", "shimano")).isEmpty()
        assertThat(dictionary.nameSuggestions("Групсет", "")).isEmpty()
        assertThat(dictionary.namesOf("седло")).containsExactly("Fizik Antares", "Brooks B17")
    }

    // --- bikes -----------------------------------------------------------------------------------

    @Test
    fun `a category has its own subtypes and no others`() {
        assertThat(catalog.classification.subtypesOf("mtb").map { it.key })
            .containsExactly("xc", "trail")
            .inOrder()
        assertThat(catalog.classification.subtypesOf("bmx")).isEmpty()
        assertThat(catalog.classification.subtypesOf("nothing")).isEmpty()
        assertThat(catalog.classification.subtypesOf(null)).isEmpty()
        assertThat(catalog.classification.category("mtb")?.name).isEqualTo("MTB")
    }

    @Test
    fun `brands are suggested as typed, the ones that begin with it first`() {
        assertThat(catalog.brandSuggestions("can"))
            .containsExactly("Canyon", "Cannondale")
            .inOrder()
        assertThat(catalog.brandSuggestions("ant")).containsExactly("Giant")
        assertThat(catalog.brandSuggestions("zzz")).isEmpty()
        // Nothing typed: the list as the site has it.
        assertThat(catalog.brandSuggestions("")).containsExactly("Giant", "Canyon", "Cannondale")
    }

    @Test
    fun `models are those of the brand, in any case, and only its own`() {
        assertThat(catalog.modelSuggestions("giant", "t")).containsExactly("Talon", "Contend")
        assertThat(catalog.modelSuggestions("Canyon", "")).containsExactly("Grizl", "Neuron")
        // A brand of the person's own has no list.
        assertThat(catalog.modelSuggestions("Свой бренд", "x")).isEmpty()
    }

    @Test
    fun `a search line is completed with whole names of a brand and a model`() {
        assertThat(catalog.searchSuggestions("giant d")).containsExactly("Giant Defy")
        assertThat(catalog.searchSuggestions("cann")).containsExactly("Cannondale Topstone")
        assertThat(catalog.searchSuggestions("neu")).containsExactly("Canyon Neuron")
        // One letter is not a search; a name that is typed in full is not offered again.
        assertThat(catalog.searchSuggestions("g")).isEmpty()
        assertThat(catalog.searchSuggestions("Giant Defy")).isEmpty()
        assertThat(catalog.searchSuggestions("  giant   defy  ")).isEmpty()
    }
}

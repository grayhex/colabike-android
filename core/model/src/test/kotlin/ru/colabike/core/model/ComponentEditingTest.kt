package ru.colabike.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ComponentEditingTest {
    private val base =
        ComponentDraft(
            section = "build",
            category = "Цепь",
            name = "KMC X11",
            notes = "",
            priceRub = 1200.0,
            url = "",
            groupId = "drivetrain",
        )

    @Test
    fun `a part nobody touched changes nothing`() {
        assertThat(base.diff(base).isEmpty).isTrue()
    }

    @Test
    fun `only the fields that differ are named, and a price taken away is a clearing`() {
        val patch = base.copy(name = "KMC X12", priceRub = null).diff(base)

        assertThat(patch).isEqualTo(ComponentPatch(name = "KMC X12", clearPrice = true))
    }

    @Test
    fun `a part that never had a price is not cleared`() {
        val free = base.copy(priceRub = null)

        assertThat(free.diff(free).isEmpty).isTrue()
    }

    @Test
    fun `a good part has no problems`() {
        assertThat(ComponentRules.check(base)).isEmpty()
    }

    @Test
    fun `a part needs a category and a name`() {
        assertThat(ComponentRules.check(base.copy(category = " ", name = "")))
            .containsExactly(ComponentProblem.NoCategory, ComponentProblem.NoName)
    }

    @Test
    fun `the limits are the server's`() {
        val problems =
            ComponentRules.check(
                base.copy(
                    category = "к".repeat(61),
                    name = "н".repeat(151),
                    notes = "з".repeat(501),
                    url = "http://insecure.example",
                    priceRub = 1_000_000_000.0,
                )
            )

        assertThat(problems)
            .containsExactly(
                ComponentProblem.CategoryTooLong,
                ComponentProblem.NameTooLong,
                ComponentProblem.NotesTooLong,
                ComponentProblem.LinkInvalid,
                ComponentProblem.PriceInvalid,
            )
    }

    @Test
    fun `a category of the site gives its group and section`() {
        assertThat(ComponentCatalog.groupIdOf("Цепь")).isEqualTo("drivetrain")
        assertThat(ComponentCatalog.groupIdOf(" Рама ")).isEqualTo("frame")
        assertThat(ComponentCatalog.groupIdOf("Что-то своё")).isEmpty()
        assertThat(ComponentCatalog.sectionOf("Цепь")).isEqualTo("build")
        assertThat(ComponentCatalog.sectionOf("Звонок")).isEqualTo("accessories")
        assertThat(ComponentCatalog.sectionOf("Что-то своё")).isEqualTo("build")
    }

    @Test
    fun `a category of the site is known in any case, and takes the site's spelling`() {
        assertThat(ComponentCatalog.canonical(" звонок ")).isEqualTo("Звонок")
        assertThat(ComponentCatalog.canonical("Что-то своё ")).isEqualTo("Что-то своё")
        assertThat(ComponentCatalog.groupIdOf("звонок")).isEqualTo("equipment")
        assertThat(ComponentCatalog.sectionOf("ЗВОНОК")).isEqualTo("accessories")
        assertThat(ComponentCatalog.groupIdOf("цепь")).isEqualTo("drivetrain")
    }

    @Test
    fun `suggestions are the categories that hold the typed text, beginnings first`() {
        val found = ComponentCatalog.suggestions("пер")

        assertThat(found).isNotEmpty()
        assertThat(found.first()).startsWith("Пер")
        assertThat(found.size).isAtMost(ComponentCatalog.MAX_SUGGESTIONS)
        assertThat(ComponentCatalog.suggestions("  ")).isEmpty()
        assertThat(ComponentCatalog.suggestions("Цепь")).isEmpty()
    }

    @Test
    fun `every category belongs to one group only`() {
        val all = ComponentCatalog.groups.flatMap { it.categories }

        assertThat(all).containsNoDuplicates()
        assertThat(ComponentCatalog.groupName("drivetrain")).isEqualTo("Трансмиссия")
        assertThat(ComponentCatalog.groupName("unknown")).isNull()
    }
}

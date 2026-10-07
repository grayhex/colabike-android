package ru.colabike.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The search line, taken apart as the site's wizard does (cola `tests` of `parseBikeSearch`). */
class BuildQueryParserTest {
    private val brands =
        listOf(
            CatalogBrand("Giant", listOf("Contend", "Defy", "Talon", "TCR")),
            CatalogBrand("Santa Cruz", listOf("Chameleon", "Tallboy")),
            CatalogBrand("Canyon", listOf("Grizl", "Grand Canyon", "Grand")),
        )

    @Test
    fun `brand, model, version and year are told apart`() {
        assertThat(BuildQueryParser.parse("Giant Contend AR 1 2024", brands))
            .isEqualTo(BuildQuery("Giant", "Contend", "AR 1", 2024))
        assertThat(BuildQueryParser.parse("2024 Giant Contend AR 1", brands))
            .isEqualTo(BuildQuery("Giant", "Contend", "AR 1", 2024))
    }

    @Test
    fun `the year and the version are not required, and none is made up`() {
        assertThat(BuildQueryParser.parse("Giant Talon", brands))
            .isEqualTo(BuildQuery("Giant", "Talon", null, null))
        assertThat(BuildQueryParser.parse("Giant Talon 2", brands))
            .isEqualTo(BuildQuery("Giant", "Talon", "2", null))
    }

    @Test
    fun `a brand of two words is the brand when the site knows it`() {
        assertThat(BuildQueryParser.parse("santa cruz Tallboy 4 2023", brands))
            .isEqualTo(BuildQuery("Santa Cruz", "Tallboy", "4", 2023))
    }

    @Test
    fun `the longest model of the brand that the text begins with is the model`() {
        assertThat(BuildQueryParser.parse("Canyon Grand Canyon 7", brands))
            .isEqualTo(BuildQuery("Canyon", "Grand Canyon", "7", null))
        assertThat(BuildQueryParser.parse("Canyon Grand Tour", brands))
            .isEqualTo(BuildQuery("Canyon", "Grand", "Tour", null))
    }

    @Test
    fun `the site's spelling is taken when the text names its model in another case`() {
        assertThat(BuildQueryParser.parse("giant tcr", brands))
            .isEqualTo(BuildQuery("Giant", "TCR", null, null))
    }

    @Test
    fun `a brand the site does not know is the first word, the rest is the model`() {
        assertThat(BuildQueryParser.parse("Orbea Terra H30 2022", brands))
            .isEqualTo(BuildQuery("Orbea", "Terra H30", null, 2022))
        // With no lists at all the same holds: the app can search before it has read them.
        assertThat(BuildQueryParser.parse("Orbea Terra H30 2022"))
            .isEqualTo(BuildQuery("Orbea", "Terra H30", null, 2022))
    }

    @Test
    fun `a line that says too little or too much is not a search`() {
        assertThat(BuildQueryParser.parse("", brands)).isNull()
        assertThat(BuildQueryParser.parse("   ", brands)).isNull()
        assertThat(BuildQueryParser.parse("Giant", brands)).isNull()
        assertThat(BuildQueryParser.parse("Giant 2024", brands)).isNull()
        // Two years are two things.
        assertThat(BuildQueryParser.parse("Giant Contend 2023 2024", brands)).isNull()
        assertThat(BuildQueryParser.parse("Giant " + "x".repeat(300), brands)).isNull()
        assertThat(BuildQueryParser.parse("B".repeat(61) + " model", brands)).isNull()
    }

    @Test
    fun `spaces are one space and a number that is not a year is part of the name`() {
        assertThat(BuildQueryParser.parse("  Giant   Talon   29  ", brands))
            .isEqualTo(BuildQuery("Giant", "Talon", "29", null))
        assertThat(BuildQueryParser.parse("Giant Talon 1800", brands))
            .isEqualTo(BuildQuery("Giant", "Talon", "1800", null))
    }

    @Test
    fun `the line is written back as the person would write it`() {
        assertThat(BuildQuery("Giant", "Contend", "AR 1", 2024).line)
            .isEqualTo("Giant Contend AR 1 2024")
        assertThat(BuildQuery("Giant", "Talon").line).isEqualTo("Giant Talon")
    }
}

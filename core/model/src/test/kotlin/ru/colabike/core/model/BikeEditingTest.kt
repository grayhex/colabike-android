package ru.colabike.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BikeEditingTest {
    private val base =
        BikeDraft(
            name = "Мой гравел",
            brand = "Canyon",
            model = "Grail",
            trim = "CF",
            year = 2023,
            classification = ClassificationDraft(category = "road_gravel", subtype = "gravel"),
            description = "",
            color = "Серый",
            size = "M",
            weightKg = 9.5,
            mileageKm = 1200,
            manufacturerUrl = "https://www.canyon.com",
            priceRub = 250000.0,
            priceVisibility = PriceVisibility(bike = true),
            isFormer = false,
            isPublic = false,
        )

    @Test
    fun `a form nobody touched changes nothing`() {
        assertThat(base.diff(base).isEmpty).isTrue()
    }

    @Test
    fun `only the fields that differ are named`() {
        val patch = base.copy(name = "Новое имя", mileageKm = 1500).diff(base)

        assertThat(patch).isEqualTo(BikePatch(name = "Новое имя", mileageKm = 1500))
    }

    @Test
    fun `a weight or a price taken away is a clearing, not a missing field`() {
        val patch = base.copy(weightKg = null, priceRub = null).diff(base)

        assertThat(patch.clearWeight).isTrue()
        assertThat(patch.clearPrice).isTrue()
        assertThat(patch.weightKg).isNull()
        assertThat(patch.priceRub).isNull()
        assertThat(patch.isEmpty).isFalse()
    }

    @Test
    fun `a weight that was empty and still is, is not a clearing`() {
        val noWeight = base.copy(weightKg = null)

        assertThat(noWeight.diff(noWeight).isEmpty).isTrue()
    }

    @Test
    fun `a changed classification goes whole and the audience goes alone`() {
        val patch =
            base
                .copy(
                    classification = ClassificationDraft(category = "mtb", subtype = "trail"),
                    isPublic = true,
                )
                .diff(base)

        assertThat(patch.classification)
            .isEqualTo(ClassificationDraft(category = "mtb", subtype = "trail"))
        assertThat(patch.isPublic).isTrue()
        assertThat(patch.publishes).isTrue()
        assertThat(patch.name).isNull()
    }

    @Test
    fun `taking a bike off the public is not a publication`() {
        val patch = base.copy(isPublic = false).diff(base.copy(isPublic = true))

        assertThat(patch.isPublic).isFalse()
        assertThat(patch.publishes).isFalse()
    }

    @Test
    fun `a good form has no problems`() {
        assertThat(BikeRules.check(base, creating = true)).isEmpty()
    }

    @Test
    fun `a new bike needs a name, a year and a type`() {
        val problems =
            BikeRules.check(
                base.copy(name = "   ", year = null, classification = ClassificationDraft("")),
                creating = true,
            )

        assertThat(problems)
            .containsExactly(BikeProblem.NoName, BikeProblem.NoYear, BikeProblem.NoCategory)
    }

    @Test
    fun `a bike the server holds without a year may stay so when it is changed`() {
        val withoutYear = base.copy(year = null)

        assertThat(BikeRules.check(withoutYear, creating = false)).isEmpty()
        assertThat(BikeRules.check(withoutYear, creating = true))
            .containsExactly(BikeProblem.NoYear)
    }

    @Test
    fun `the limits are the server's`() {
        val problems =
            BikeRules.check(
                base.copy(
                    name = "н".repeat(101),
                    brand = "б".repeat(61),
                    model = "м".repeat(101),
                    trim = "т".repeat(101),
                    year = 1899,
                    description = "о".repeat(2001),
                    color = "ц".repeat(61),
                    size = "р".repeat(31),
                    weightKg = 100.5,
                    mileageKm = 10_000_001,
                    manufacturerUrl = "http://insecure.example",
                    priceRub = -1.0,
                ),
                creating = true,
            )

        assertThat(problems)
            .containsExactly(
                BikeProblem.NameTooLong,
                BikeProblem.BrandTooLong,
                BikeProblem.ModelTooLong,
                BikeProblem.TrimTooLong,
                BikeProblem.YearOutOfRange,
                BikeProblem.DescriptionTooLong,
                BikeProblem.ColorTooLong,
                BikeProblem.SizeTooLong,
                BikeProblem.WeightInvalid,
                BikeProblem.MileageInvalid,
                BikeProblem.LinkInvalid,
                BikeProblem.PriceInvalid,
            )
    }

    @Test
    fun `the edges of the limits are allowed`() {
        val problems =
            BikeRules.check(
                base.copy(
                    name = "н".repeat(100),
                    year = 2100,
                    weightKg = 100.0,
                    mileageKm = 10_000_000,
                    priceRub = 0.0,
                    manufacturerUrl = "",
                ),
                creating = true,
            )

        assertThat(problems).isEmpty()
    }

    @Test
    fun `a zero weight is not a weight`() {
        assertThat(BikeRules.check(base.copy(weightKg = 0.0), creating = true))
            .containsExactly(BikeProblem.WeightInvalid)
    }

    @Test
    fun `a link is empty or a plain https address without credentials`() {
        assertThat(BikeRules.isLink("")).isTrue()
        assertThat(BikeRules.isLink("  ")).isTrue()
        assertThat(BikeRules.isLink("https://www.canyon.com/grail?x=1#top")).isTrue()
        assertThat(BikeRules.isLink("HTTPS://example.com")).isTrue()
        assertThat(BikeRules.isLink("http://example.com")).isFalse()
        assertThat(BikeRules.isLink("javascript:alert(1)")).isFalse()
        assertThat(BikeRules.isLink("https://user:pass@example.com")).isFalse()
        assertThat(BikeRules.isLink("https://")).isFalse()
        assertThat(BikeRules.isLink("https://exa mple.com")).isFalse()
        assertThat(BikeRules.isLink("https://example.com/" + "a".repeat(2048))).isFalse()
    }

    @Test
    fun `the catalog gives every subtype to one category only`() {
        val all = BikeCatalog.subtypes.values.flatten()

        assertThat(all).containsNoDuplicates()
        assertThat(BikeCatalog.subtypes.keys).containsExactlyElementsIn(BikeCatalog.categories)
        assertThat(BikeCatalog.uses).hasSize(14)
        assertThat(BikeCatalog.MAX_USES).isEqualTo(3)
    }
}

package ru.colabike.core.model

/**
 * A part of a bike's build as its owner's form holds it and the server takes it. [section] is
 * `build` or `accessories`; [groupId] names the group the part is shown in (a key of
 * [ComponentCatalog], or empty: then the part is grouped by its category).
 */
data class ComponentDraft(
    val section: String,
    val category: String,
    val name: String,
    val notes: String,
    val priceRub: Double?,
    val url: String,
    val groupId: String,
)

/**
 * What changed in a part: only the named fields go to the server. A price taken away is
 * [clearPrice], because `null` is a value of its own there.
 */
data class ComponentPatch(
    val section: String? = null,
    val category: String? = null,
    val name: String? = null,
    val notes: String? = null,
    val priceRub: Double? = null,
    val clearPrice: Boolean = false,
    val url: String? = null,
    val groupId: String? = null,
) {
    val isEmpty: Boolean
        get() = this == ComponentPatch()
}

/** The fields that differ from [original]; an empty patch means nothing needs to be sent. */
fun ComponentDraft.diff(original: ComponentDraft): ComponentPatch =
    ComponentPatch(
        section = section.takeIf { it != original.section },
        category = category.takeIf { it != original.category },
        name = name.takeIf { it != original.name },
        notes = notes.takeIf { it != original.notes },
        priceRub = priceRub.takeIf { it != original.priceRub },
        clearPrice = priceRub == null && original.priceRub != null,
        url = url.takeIf { it != original.url },
        groupId = groupId.takeIf { it != original.groupId },
    )

/** The part as the form starts from it when its owner changes it. */
fun BikeComponent.toDraft(): ComponentDraft =
    ComponentDraft(
        section = section,
        category = category,
        name = name,
        notes = notes,
        priceRub = priceRub,
        url = url.orEmpty(),
        groupId = groupId,
    )

/**
 * The built-in copy of the site's groups of parts and the categories in each (cola
 * `lib/garage-layout.ts`, `defaultGroups`). The app reads the live ones from the site
 * ([SiteCatalog.components]); this stands in until it has, and for a person who has never been
 * online. A category the site's operator added is simply not in it: a part with it is grouped by
 * its category, which the page already does for a part with no group.
 */
object ComponentCatalog {
    val groups: List<ComponentDictionary.Group> =
        listOf(
            ComponentDictionary.Group(
                "frame",
                "Рама и подвеска",
                listOf("Рама", "Вилка", "Амортизатор"),
            ),
            ComponentDictionary.Group(
                "drivetrain",
                "Трансмиссия",
                listOf(
                    "Групсет",
                    "Задний переключатель",
                    "Передний переключатель",
                    "Манетки / дуалы",
                    "Левая манетка",
                    "Правая манетка",
                    "Система / шатуны",
                    "Каретка",
                    "Передняя звезда",
                    "Кассета",
                    "Трещотка",
                    "Цепь",
                    "Ремень",
                    "Задняя звезда",
                    "Педали",
                    "Измеритель мощности",
                ),
            ),
            ComponentDictionary.Group(
                "brakes",
                "Тормоза",
                listOf(
                    "Тормоза",
                    "Передний тормоз",
                    "Задний тормоз",
                    "Тормозная ручка",
                    "Роторы",
                    "Передний ротор",
                    "Задний ротор",
                ),
            ),
            ComponentDictionary.Group(
                "wheels",
                "Колёса",
                listOf(
                    "Колёса",
                    "Переднее колесо",
                    "Заднее колесо",
                    "Обода",
                    "Передний обод",
                    "Задний обод",
                    "Втулки",
                    "Передняя втулка",
                    "Задняя втулка",
                    "Покрышки",
                    "Передняя покрышка",
                    "Задняя покрышка",
                    "Камеры / бескамерка",
                ),
            ),
            ComponentDictionary.Group(
                "cockpit",
                "Управление и посадка",
                listOf(
                    "Руль",
                    "Вынос",
                    "Рулевая",
                    "Грипсы / обмотка",
                    "Седло",
                    "Подседельный штырь",
                    "Дроппер",
                    "Подседельный зажим",
                ),
            ),
            ComponentDictionary.Group(
                "electric",
                "Электрооборудование",
                listOf("Мотор", "Батарея", "Дисплей", "Зарядное устройство"),
            ),
            ComponentDictionary.Group(
                "equipment",
                "Оборудование и аксессуары",
                listOf(
                    "Передний свет",
                    "Задний свет",
                    "Крылья",
                    "Багажник",
                    "Подножка",
                    "Звонок",
                    "Велокомпьютер",
                    "Датчики",
                    "Замок",
                    "Насос",
                    "Инструменты",
                    "Фляга / держатель",
                    "Подседельная сумка",
                    "Рамная сумка",
                    "Сумка на руль",
                ),
            ),
        )

    private val accessoryCategories: List<String> = groups.first { it.id == "equipment" }.categories

    /**
     * The built-in copy of the site's dictionary of parts, which stands in until the app has read
     * the site's own (`GET /catalog`): the same groups, no names to suggest.
     */
    val dictionary: ComponentDictionary =
        ComponentDictionary(
            groups = groups,
            buildCategories = groups.filter { it.id != "equipment" }.flatMap { it.categories },
            accessoryCategories = accessoryCategories,
            names = emptyMap(),
        )

    /** See [ComponentDictionary.canonical]. */
    fun canonical(category: String): String = dictionary.canonical(category)

    /** See [ComponentDictionary.groupIdOf]. */
    fun groupIdOf(category: String): String = dictionary.groupIdOf(category)

    /** See [ComponentDictionary.sectionOf]. */
    fun sectionOf(category: String): String = dictionary.sectionOf(category)

    /** See [ComponentDictionary.groupName]. */
    fun groupName(key: String): String? = dictionary.groupName(key)

    /** See [ComponentDictionary.suggestions]. */
    fun suggestions(typed: String, limit: Int = MAX_SUGGESTIONS): List<String> =
        dictionary.suggestions(typed, limit)

    const val SECTION_BUILD = ComponentDictionary.SECTION_BUILD
    const val SECTION_ACCESSORIES = ComponentDictionary.SECTION_ACCESSORIES
    const val MAX_SUGGESTIONS = ComponentDictionary.MAX_SUGGESTIONS
}

/** What is wrong with a part form, in the server's own terms. */
sealed interface ComponentProblem {
    data object NoCategory : ComponentProblem

    data object CategoryTooLong : ComponentProblem

    data object NoName : ComponentProblem

    data object NameTooLong : ComponentProblem

    data object NotesTooLong : ComponentProblem

    data object LinkInvalid : ComponentProblem

    data object PriceInvalid : ComponentProblem
}

/** The limits of the server for one part (cola `componentInput`). */
object ComponentRules {
    const val MAX_CATEGORY = 60
    const val MAX_NAME = 150
    const val MAX_NOTES = 500
    const val MAX_PRICE_RUB = 999_999_999.0

    fun check(draft: ComponentDraft): List<ComponentProblem> = buildList {
        val category = draft.category.trim()
        if (category.isEmpty()) add(ComponentProblem.NoCategory)
        if (category.length > MAX_CATEGORY) add(ComponentProblem.CategoryTooLong)
        val name = draft.name.trim()
        if (name.isEmpty()) add(ComponentProblem.NoName)
        if (name.length > MAX_NAME) add(ComponentProblem.NameTooLong)
        if (draft.notes.trim().length > MAX_NOTES) add(ComponentProblem.NotesTooLong)
        if (!BikeRules.isLink(draft.url)) add(ComponentProblem.LinkInvalid)
        draft.priceRub?.let {
            if (!(it >= 0.0 && it <= MAX_PRICE_RUB)) add(ComponentProblem.PriceInvalid)
        }
    }
}

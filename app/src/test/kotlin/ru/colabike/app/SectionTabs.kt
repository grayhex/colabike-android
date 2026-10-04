package ru.colabike.app

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * A section tab of the floating bar by its name. With more than three sections the bar writes the
 * name only under the selected tab; the others are named for TalkBack by a description, so a test
 * finds a tab by either, as a person with a screen reader does.
 */
fun sectionTab(name: String): SemanticsMatcher =
    isTab and (hasText(name) or hasContentDescription(name)) and hasClickAction()

val isTab: SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

fun ComposeTestRule.section(name: String): SemanticsNodeInteraction = onNode(sectionTab(name))

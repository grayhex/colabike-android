package ru.colabike.app

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

/**
 * A section tab of the bar or the rail by its name. Every tab writes its name, and a test finds it
 * by that or by a description, as a person with a screen reader does.
 */
fun sectionTab(name: String): SemanticsMatcher =
    isTab and (hasText(name) or hasContentDescription(name)) and hasClickAction()

val isTab: SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

fun ComposeTestRule.section(name: String): SemanticsNodeInteraction = onNode(sectionTab(name))

/** The chats: the button beside the bell, over the screen the person is on. */
fun ComposeTestRule.openChats() {
    onNodeWithContentDescription("Чаты").performClick()
    waitForIdle()
}

/**
 * A value of a drop-down filter: the filter is opened by its tag and the value is tapped in the
 * list that opens (the list's own words, not the label of the filter, which may say the same).
 */
fun ComposeTestRule.choose(filter: String, value: String) {
    onNodeWithTag(filter).performClick()
    waitForIdle()
    onNode(hasText(value) and hasAnyAncestor(isPopup())).performClick()
    waitForIdle()
}

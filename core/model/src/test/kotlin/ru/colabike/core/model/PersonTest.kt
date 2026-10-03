package ru.colabike.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PersonTest {
    @Test
    fun `a person without a name is called by the username`() {
        assertThat(Person(UserId("1"), "anna", " ", null).displayName).isEqualTo("anna")
        assertThat(Person(UserId("1"), "anna", "Анна", null).displayName).isEqualTo("Анна")
    }

    @Test
    fun `the last page has no cursor`() {
        assertThat(Page(listOf(1), nextCursor = null).hasMore).isFalse()
        assertThat(Page(listOf(1), nextCursor = "c").hasMore).isTrue()
    }
}

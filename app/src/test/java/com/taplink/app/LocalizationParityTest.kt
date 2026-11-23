package com.taplink.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the promise that every interface string exists in every shipped language.
 *
 * A missing translation would otherwise only show up as an English line inside a Chinese screen,
 * which is exactly the kind of defect a reviewer finds during a demonstration. The check runs on
 * the resource files themselves, so it needs no device and fails fast in CI.
 *
 * Every `*.xml` file in a values directory is read, not just `strings.xml`: the widget labels live
 * in their own file and would silently escape the check otherwise. Entries marked
 * `translatable="false"` (brand names) are collected separately and excluded from the parity rules,
 * because Android resolves them from the default values folder in every language.
 */
class LocalizationParityTest {

    private val root: File = sequenceOf(File("src/main/res"), File("app/src/main/res"))
        .firstOrNull { it.isDirectory } ?: error("resource directory not found")

    private val locales = mapOf(
        "en" to File(root, "values"),
        "zh-TW" to File(root, "values-zh-rTW"),
        "zh-CN" to File(root, "values-zh-rCN")
    )

    private val entry = Regex("""<string([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val nameAttribute = Regex("""name="([^"]+)"""")
    private val placeholder = Regex("""%(\d+)\$[sd]""")

    private class Strings(val translatable: Map<String, String>, val fixed: Set<String>)

    private fun read(directory: File): Strings {
        assertTrue("missing resource directory: ${directory.path}", directory.isDirectory)
        val translatable = mutableMapOf<String, String>()
        val fixed = mutableSetOf<String>()
        directory.listFiles { file -> file.extension == "xml" }!!.sortedBy { it.name }.forEach { file ->
            val text = file.readText()
            // Also catch an empty <string name="x" /> element, which the value regex would skip.
            val names = Regex("""<string([^>]*)/>""").findAll(text)
            entry.findAll(text).forEach { match ->
                val attributes = match.groupValues[1]
                val key = nameAttribute.find(attributes)?.groupValues?.get(1) ?: return@forEach
                if ("translatable=\"false\"" in attributes) fixed += key
                else translatable[key] = match.groupValues[2]
            }
            names.forEach { match ->
                val attributes = match.groupValues[1]
                val key = nameAttribute.find(attributes)?.groupValues?.get(1) ?: return@forEach
                if ("translatable=\"false\"" in attributes) fixed += key
                else translatable[key] = ""
            }
        }
        return Strings(translatable, fixed)
    }

    @Test
    fun `every locale defines the same keys`() {
        val english = read(locales.getValue("en")).translatable.keys
        locales.forEach { (tag, directory) ->
            val keys = read(directory).translatable.keys
            assertEquals("keys missing in $tag", emptySet<String>(), english - keys)
            assertEquals("extra keys in $tag", emptySet<String>(), keys - english)
        }
    }

    @Test
    fun `no translated value is blank`() {
        locales.forEach { (tag, directory) ->
            read(directory).translatable.forEach { (key, value) ->
                assertTrue("blank value for $key in $tag", value.isNotBlank())
            }
        }
    }

    @Test
    fun `format placeholders survive translation`() {
        val english = read(locales.getValue("en")).translatable
        locales.forEach { (tag, directory) ->
            read(directory).translatable.forEach { (key, value) ->
                val expected = placeholder.findAll(english.getValue(key)).map { it.value }.toSet()
                val actual = placeholder.findAll(value).map { it.value }.toSet()
                assertEquals("placeholder mismatch for $key in $tag", expected, actual)
            }
        }
    }

    @Test
    fun `the product name is not translated`() {
        assertEquals("app_name must stay TapLink", "TapLink", read(locales.getValue("en")).translatable.getValue("app_name"))
        locales.filterKeys { it != "en" }.forEach { (tag, directory) ->
            val strings = read(directory)
            assertTrue("app_name must not be redefined in $tag", "app_name" !in strings.translatable || strings.translatable.getValue("app_name") == "TapLink")
        }
    }
}

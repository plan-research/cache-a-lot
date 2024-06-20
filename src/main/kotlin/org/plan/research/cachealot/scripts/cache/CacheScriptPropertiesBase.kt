package org.plan.research.cachealot.scripts.cache

import org.ini4j.Config
import org.ini4j.Wini
import java.nio.file.Path

abstract class CacheScriptPropertiesBase(propertiesFile: Path) {
    protected val ini = Wini().apply {
        config = Config().apply {
            isMultiOption = true
            isMultiSection = false
        }
        load(propertiesFile.toFile())
    }

    protected fun getStringProperty(section: String, key: String): String? =
        ini.get(section, key)

    protected inline fun <reified T> getProperty(section: String, key: String): T? =
        ini.get(section, key, T::class.java)

    protected fun getListStringProperty(section: String, key: String): List<String> =
        ini[section]?.getAll(key) ?: emptyList()

    protected inline fun <reified T> getListProperty(section: String, key: String): Array<T> =
        ini[section]?.getAll(key, Array<T>::class.java) ?: emptyArray()
}
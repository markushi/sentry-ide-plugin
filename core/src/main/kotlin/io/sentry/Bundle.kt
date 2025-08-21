package io.sentry

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.jewel.ui.icon.PathIconKey

private const val BUNDLE = "messages.Bundle"

object Bundle : DynamicBundle(BUNDLE) {

    val EMPTY_STATE = PathIconKey("icons/search.png", Bundle::class.java)

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
        getMessage(key, *params)
}

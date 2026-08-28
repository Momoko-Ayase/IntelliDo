package moe.momokko.intellido.connect

import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import java.util.Locale

object ConnectProgressFormatter {
    fun label(progress: ConnectProgress, locale: Locale): String = when (progress) {
        is ConnectProgress.Official -> IntelliDoStrings.message("connect.official", locale)
        is ConnectProgress.Estimate -> IntelliDoStrings.message("connect.estimate", locale)
        ConnectProgress.Unavailable -> IntelliDoStrings.message("connect.unavailable", locale)
    }

    fun isAuthoritative(progress: ConnectProgress): Boolean = progress is ConnectProgress.Official
}

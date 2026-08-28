package moe.momokko.intellido.ui.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.platform.reading.ReadingAppearance
import moe.momokko.intellido.platform.reading.ReadingPreferences
import moe.momokko.intellido.platform.reading.ReadingStyle
import moe.momokko.intellido.ui.startup.IntelliDoRuntime
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.Locale
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class ReadingConfigurable : Configurable {
    private var panel: ReadingSettingsPanel? = null

    override fun getDisplayName(): String =
        IntelliDoStrings.message("settings.reading", locale())

    override fun createComponent(): JComponent {
        val view = ReadingSettingsPanel(locale(), ReadingAppearance.current)
        panel = view
        return view
    }

    override fun isModified(): Boolean {
        val current = panel?.toStyle() ?: return false
        return current != ReadingAppearance.current
    }

    override fun apply() {
        val style = panel?.toStyle() ?: return
        val runtime = service<IntelliDoRuntime>()
        ReadingPreferences.save(runtime.preferences, style)
        ReadingAppearance.replace(style)
    }

    override fun reset() {
        panel?.setStyle(ReadingAppearance.current)
    }

    override fun disposeUIResources() {
        panel = null
    }

    private fun locale(): Locale =
        runCatching { service<IntelliDoRuntime>().locale }.getOrDefault(Locale.SIMPLIFIED_CHINESE)
}

class ReadingSettingsPanel(
    private val locale: Locale,
    initial: ReadingStyle,
) : JBPanel<ReadingSettingsPanel>(BorderLayout()) {
    private val fontSize = JSpinner(SpinnerNumberModel(initial.fontSize, ReadingStyle.MIN_FONT, ReadingStyle.MAX_FONT, 1))
    private val lineHeight = JSpinner(
        SpinnerNumberModel(initial.lineHeight.toDouble(), ReadingStyle.MIN_LINE.toDouble(), ReadingStyle.MAX_LINE.toDouble(), 0.05),
    )
    private val maxWidth = JComboBox(widthLabels().toTypedArray())

    init {
        border = JBUI.Borders.empty(8)
        val form = JBPanel<JBPanel<*>>(GridBagLayout())
        val constraints = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(4, 0, 4, 12)
        }
        fun row(index: Int, key: String, field: JComponent) {
            constraints.gridx = 0
            constraints.gridy = index
            constraints.weightx = 0.0
            form.add(JBLabel(IntelliDoStrings.message(key, locale)), constraints)
            constraints.gridx = 1
            constraints.weightx = 1.0
            form.add(field, constraints)
        }
        row(0, "settings.reading.fontSize", fontSize)
        row(1, "settings.reading.lineHeight", lineHeight)
        row(2, "settings.reading.maxWidth", maxWidth)
        add(form, BorderLayout.NORTH)
        setStyle(initial)
    }

    fun setStyle(style: ReadingStyle) {
        fontSize.value = style.clamped().fontSize
        lineHeight.value = style.clamped().lineHeight.toDouble()
        val index = ReadingStyle.WIDTH_CHOICES.indexOf(style.clamped().maxWidth).coerceAtLeast(0)
        maxWidth.selectedIndex = index
    }

    fun toStyle(): ReadingStyle = ReadingStyle(
        fontSize = (fontSize.value as Number).toInt(),
        lineHeight = (lineHeight.value as Number).toFloat(),
        maxWidth = ReadingStyle.WIDTH_CHOICES.getOrElse(maxWidth.selectedIndex) { 0 },
    ).clamped()

    private fun widthLabels(): List<String> = ReadingStyle.WIDTH_CHOICES.map { width ->
        if (width <= 0) {
            IntelliDoStrings.message("settings.reading.maxWidth.fill", locale)
        } else {
            "${width}px"
        }
    }
}

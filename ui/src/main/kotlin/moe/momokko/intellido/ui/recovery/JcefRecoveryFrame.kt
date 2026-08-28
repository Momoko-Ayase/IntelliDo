package moe.momokko.intellido.ui.recovery

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.browser.JcefDiagnostics
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.Locale
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.WindowConstants

class JcefRecoveryFrame(
    private val diagnostics: JcefDiagnostics,
    private val locale: Locale,
    private val onRetry: () -> Unit,
) : JFrame(IntelliDoStrings.message("recovery.title", locale)) {
    init {
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        contentPane = buildContent()
        setSize(720, 480)
        setLocationRelativeTo(null)
    }

    fun showRecovery() {
        isVisible = true
        toFront()
    }

    private fun buildContent(): JBPanel<*> {
        val root = JBPanel<JBPanel<*>>(BorderLayout())
        root.border = JBUI.Borders.empty(16)

        val body = JBLabel(
            "<html><h2>${IntelliDoStrings.message("recovery.title", locale)}</h2>" +
                "<p>${IntelliDoStrings.message("recovery.body", locale)}</p></html>",
        )
        val diagnosticsArea = JBTextArea(diagnostics.copyableText())
        diagnosticsArea.isEditable = false
        diagnosticsArea.lineWrap = true
        diagnosticsArea.wrapStyleWord = true

        val buttons = JBPanel<JBPanel<*>>()
        buttons.add(JButton(IntelliDoStrings.message("recovery.retry", locale)).also { button ->
            button.addActionListener {
                isVisible = false
                dispose()
                onRetry()
            }
        })
        buttons.add(JButton(IntelliDoStrings.message("recovery.openRepairGuide", locale)).also { button ->
            button.addActionListener { showRepairGuide() }
        })
        buttons.add(JButton(IntelliDoStrings.message("recovery.copyDiagnostics", locale)).also { button ->
            button.addActionListener {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(
                    StringSelection(diagnostics.copyableText()),
                    null,
                )
            }
        })
        buttons.add(JButton(IntelliDoStrings.message("recovery.exit", locale)).also { button ->
            button.addActionListener {
                ApplicationManager.getApplication().exit()
            }
        })

        root.add(body, BorderLayout.NORTH)
        root.add(JBScrollPane(diagnosticsArea), BorderLayout.CENTER)
        root.add(buttons, BorderLayout.SOUTH)
        return root
    }

    private fun showRepairGuide() {
        val resource = if (locale.language == "zh") {
            "/docs/jcef-repair.zh.md"
        } else {
            "/docs/jcef-repair.md"
        }
        val text = javaClass.getResourceAsStream(resource)?.bufferedReader()?.readText()
            ?: javaClass.getResourceAsStream("/docs/jcef-repair.md")?.bufferedReader()?.readText()
            ?: IntelliDoStrings.message("recovery.body", locale)
        val dialog = JDialog(this, IntelliDoStrings.message("recovery.openRepairGuide", locale), true)
        val area = JBTextArea(text)
        area.isEditable = false
        area.lineWrap = true
        area.wrapStyleWord = true
        dialog.contentPane = JBScrollPane(area)
        dialog.setSize(640, 420)
        dialog.setLocationRelativeTo(this)
        dialog.isVisible = true
    }
}

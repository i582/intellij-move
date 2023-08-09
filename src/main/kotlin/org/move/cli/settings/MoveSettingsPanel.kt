package org.move.cli.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import org.move.cli.runConfigurations.aptos.AptosCli
import org.move.openapiext.UiDebouncer
import org.move.openapiext.pathField
import org.move.openapiext.showSettings
import org.move.stdext.toPathOrNull

class MoveSettingsPanel(
    private val showDefaultSettingsLink: Boolean,
    private val updateListener: (() -> Unit)? = null
) : Disposable {
    private val aptosPathField =
        pathField(
            FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor(),
            this,
            "Aptos Binary"
        ) { onUpdate() }
    private val versionUpdateDebouncer = UiDebouncer(this)
    private val versionLabel = VersionLabel()

    data class Data(val aptosPath: String) {
        fun aptosCli(): AptosCli? = aptosPath.toPathOrNull()?.let { AptosCli(it) }
    }

    var data: Data
        get() {
            return Data(aptosPathField.text)
        }
        set(value) {
            aptosPathField.text = value.aptosPath
            onUpdate()
        }

    fun attachTo(layout: Panel) = with(layout) {
        // Don't use `Project.toolchain` or `Project.rustSettings` here because
        // `getService` can return `null` for default project after dynamic plugin loading.
        // As a result, you can get `java.lang.IllegalStateException`
        // So let's handle it manually
        val projectSettings =
            ProjectManager.getInstance().defaultProject.getService(MoveProjectSettingsService::class.java)
        data = Data(
            aptosPath = projectSettings.settingsState.aptosPath,
        )
        row("Aptos CLI") {
            cell(aptosPathField)
                .align(AlignX.FILL)
//                .horizontalAlign(HorizontalAlign.FILL)
                .resizableColumn()
            comment("(required)")
        }
        row("Version") { cell(versionLabel) }
//        row {
//            button("Download Aptos CLI") {
//                val dialog = DownloadAptosDialog(parentComponent = aptosPathField)
//                dialog.show()
//
//                val newAptosPath = dialog.outPath
//                if (dialog.isOK && newAptosPath != null) {
//                    data = Data(newAptosPath)
//                }
//            }
//        }
        row {
            link("Set default project settings") {
                ProjectManager.getInstance().defaultProject.showSettings<PerProjectMoveConfigurable>()
            }
                .visible(showDefaultSettingsLink)
                .align(AlignX.FILL)
//                .horizontalAlign(HorizontalAlign.RIGHT)
        }
    }

    private fun onUpdate() {
        val aptosPath = aptosPathField.text.toPathOrNull()
        versionUpdateDebouncer.run(
            onPooledThread = {
                val aptosCliVersion = aptosPath?.let { AptosCli(it) }?.version()
                aptosCliVersion
            },
            onUiThread = { version ->
                versionLabel.setVersion(version)
                updateListener?.invoke()
            }
        )
    }

    override fun dispose() {
        Disposer.dispose(aptosPathField)
    }
}

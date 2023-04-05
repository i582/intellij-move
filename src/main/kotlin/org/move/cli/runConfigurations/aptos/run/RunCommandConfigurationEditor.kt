package org.move.cli.runConfigurations.aptos.run

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.icons.AllIcons
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.MessageType
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import org.move.cli.moveProjects
import org.move.cli.runConfigurations.aptos.FunctionCallParametersDialog
import org.move.cli.runConfigurations.aptos.Profile
import org.move.cli.runConfigurations.aptos.FunctionCall
import org.move.lang.index.MvEntryFunctionIndex
import org.move.utils.ui.whenItemSelectedFromUi
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel

class RunCommandConfigurationEditor(
    val project: Project,
    private var command: String,
    private var workingDirectory: Path?,
) : SettingsEditor<RunCommandConfiguration>() {

    private var functionCall: FunctionCall

    private val commandTextField = EditorTextField("")
    private val environmentVariablesField = EnvironmentVariablesComponent()

    private val typeParametersLabel = JLabel("")
    private val parametersLabel = JLabel("")

    private val editParametersButton = JButton("Edit Parameters")
    private val errorLabel = JLabel("")

    private val profilesComboBox: ComboBox<Profile> = ComboBox()

    init {
        commandTextField.isViewer = true
        errorLabel.foreground = JBColor.RED
        project.moveProjects.allProjects
            .forEach { proj ->
                val projectProfiles = proj.aptosConfigYaml?.profiles.orEmpty()
                projectProfiles.forEach { profileName ->
                    val profile = Profile(profileName, proj)
                    profilesComboBox.addItem(profile)
                }
            }
        functionCall = FunctionCall.empty(profilesComboBox.selectedItem as Profile)

        editParametersButton.addActionListener {
            val functionId = functionCall.functionId?.cmdText() ?: return@addActionListener
            val entryFunction = MvEntryFunctionIndex.getEntryFunction(project, functionId)
            if (entryFunction == null) return@addActionListener

            // TODO: button inactive if no params required
            val parametersDialog = FunctionCallParametersDialog(
                entryFunction,
                functionCall,
            )
            val ok = parametersDialog.showAndGet()
            if (!ok) return@addActionListener

            refreshEditorState()
        }
    }

    private val functionIdField: TextFieldWithAutoCompletion<String> =
        textFieldWithCompletion(
            functionCall.functionId?.cmdText() ?: "",
            MvEntryFunctionIndex.getAllKeysForCompletion(project)
        )

    override fun applyEditorTo(s: RunCommandConfiguration) {
        s.command = command
        s.workingDirectory = workingDirectory
    }

    override fun resetEditorFrom(s: RunCommandConfiguration) {
        this.command = s.command
        this.workingDirectory = s.workingDirectory
    }

    override fun createEditor(): JComponent {
        val editorPanel = createEditorPanel()
        return DumbService.getInstance(project).wrapGently(editorPanel, this)
    }

    private fun createEditorPanel(): JComponent {
        val parsedFunctionCall = FunctionCall.parseFromCommand(project, command, workingDirectory)
        if (parsedFunctionCall == null) {
            setErrorText("Cannot parse serialized command")
            return panel {
                row { cell(errorLabel) }
            }
        }

        functionCall.functionId = parsedFunctionCall.functionId
        functionCall.profile = parsedFunctionCall.profile ?: functionCall.profile
        functionCall.typeParams = parsedFunctionCall.typeParams
        functionCall.params = parsedFunctionCall.params

        functionIdField.text = parsedFunctionCall.functionId?.cmdText() ?: ""
        refreshEditorState()

        val editorPanel = panel {
            row("Command (generated):") {
                cell(commandTextField)
                    .horizontalAlign(HorizontalAlign.FILL)
                    .resizableColumn()
            }
            separator()
            row("Profile:") {
                cell(profilesComboBox)
                    .horizontalAlign(HorizontalAlign.FILL)
                    .resizableColumn()
                    .whenItemSelectedFromUi {
                        functionCall.profile = it
                        refreshEditorState()
                    }
            }
            row("Entry Function:") {
                // TODO: try to change it into combo box with search over the functions,
                // TODO: then there will be no need for Apply Change button
                // TODO: change comment into "required" popup
                cell(functionIdField)
                    .horizontalAlign(HorizontalAlign.FILL)
                    .resizableColumn()
                comment("(required)")
            }
            row {
                // TODO: validate if function id is not selected or invalid
                // TODO: disable button if previous function is not modified
                // TODO: disable button is profile is not set
                button("Apply FunctionId Change") {
                    val functionId = functionIdField.text.trim()
                    val profile = functionCall.profile
                    val entryFunction = MvEntryFunctionIndex.getEntryFunction(project, functionId)
                    if (profile == null || entryFunction == null) {
                        // TODO: show error popup requiring profile + functionId correct
                        functionCall = FunctionCall.empty(functionCall.profile)
                    } else {
                        functionCall = FunctionCall.template(profile, entryFunction)
                    }
                    refreshEditorState()
                }
            }
            row("Type Parameters:") { cell(typeParametersLabel) }
            row("Parameters:") { cell(parametersLabel) }
            row { cell(editParametersButton) }
            separator()
            row(environmentVariablesField.label) {
                cell(environmentVariablesField)
                    .horizontalAlign(HorizontalAlign.FILL)
            }
            row { cell(errorLabel) }
        }
        editorPanel.registerValidators(this)
        return editorPanel
    }

    private fun refreshEditorState() {
        fireEditorStateChanged()
        validateEditor()
        editParametersButton.isEnabled =
            functionCall.functionId != null && functionCall.hasRequiredParameters()

        val commandText = generateCommandText()

        command = commandText
        workingDirectory = functionCall.profile?.moveProject?.contentRootPath

        commandTextField.text = commandText
        typeParametersLabel.text =
            functionCall.typeParams.map { "${it.key}=${it.value ?: ""}" }
                .joinToString(", ")
        parametersLabel.text =
            functionCall.params.map { "${it.key}=${it.value?.value ?: ""}" }
                .joinToString(", ")
    }

    private fun validateEditor() {
        setErrorText("")
    }

    private fun setErrorText(text: String) {
        errorLabel.text = text
        errorLabel.foreground = MessageType.ERROR.titleForeground
        errorLabel.icon = if (text.isBlank()) null else AllIcons.Actions.Lightning
    }

    private fun generateCommandText(): String {
        val commandLine = functionCall.toAptosCommandLine() ?: TODO("Invalid generated command")
        return commandLine.joinedCommand()
    }

    private fun textFieldWithCompletion(
        initialValue: String,
        variants: Collection<String>
    ): TextFieldWithAutoCompletion<String> {
        val completionProvider = TextFieldWithAutoCompletion.StringsCompletionProvider(variants, null)
        val textField = TextFieldWithAutoCompletion(
            project,
            completionProvider,
            false,
            initialValue
        )
        return textField
    }
}

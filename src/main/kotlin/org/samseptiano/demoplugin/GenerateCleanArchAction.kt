package org.samseptiano.demoplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.psi.PsiDocumentManager.getInstance
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.*

class GenerateCleanArchAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        val dialog = GenerateSolidArchDialog(project)

        if (dialog.showAndGet()) {
            val activity = dialog.getActivityName()
            val viewModel = dialog.getViewModelName()
            val useCase = dialog.getUseCaseName()
            val repository = dialog.getRepositoryName()

            if (project != null
                && !useCase.isNullOrEmpty()
                && !repository.isNullOrEmpty()
                && !viewModel.isNullOrEmpty()
                && !activity.isNullOrEmpty()) {

                val activityFormat = if (activity.any { it.isWhitespace() }) activity.toPascalCase() else activity.capitalizeFirstChar()
                val viewModelFormat = if (viewModel.any { it.isWhitespace() }) viewModel.toPascalCase() else viewModel.capitalizeFirstChar()
                val useCaseFormat = if (useCase.any { it.isWhitespace() }) useCase.toPascalCase() else useCase.capitalizeFirstChar()
                val repositoryFormat = if (repository.any { it.isWhitespace() }) repository.toPascalCase() else repository.capitalizeFirstChar()

                FileGenerator(project, activityFormat, viewModelFormat, useCaseFormat, repositoryFormat).execute()
                addDependencyToGradle(project)
            }
        }
    }

    fun addDependencyToGradle(project: Project) {
        val baseDir = project.baseDir
        val appDir = baseDir.findChild("app") ?: return

        val gradleFile =
            appDir.findChild("build.gradle.kts") ?: appDir.findChild("build.gradle") ?: return
        val isKtsFile = gradleFile.extension == "kts"

        val psiFile = PsiManager.getInstance(project)
            .findFile(gradleFile) ?: return

        var textToGenerate = ""
        var textToWrite = ""

        WriteCommandAction.runWriteCommandAction(project) {
            val document = getInstance(project)
                .getDocument(psiFile) ?: return@runWriteCommandAction

            val text = document.text

            getListOfAdditionalGradle().mapIndexed { i, gradleFile ->
                if (text.contains(gradleFile)) {
                    return@mapIndexed
                } else {
                    if (i > 0) {
                        textToGenerate += "    "
                    }
                    textToGenerate += formattingRawStringInfoGradle(isKtsFile, gradleFile, gradleVer = getListOfAdditionalGradleVersion()[i])
                    textToGenerate += "\n"

                    textToWrite += formattingRawStringInfoGradle(isKtsFile, gradleFile, gradleVer = getListOfAdditionalGradleVersion()[i])
                    textToWrite += "\n"
                }
            }

            val dependenciesIndex = text.indexOf("dependencies {")

            if (dependenciesIndex != -1) {
                val insertIndex = text.indexOf("{", dependenciesIndex) + 1
                document.insertString(insertIndex, "\n    $textToGenerate")
            }

            getInstance(project).commitDocument(document)
        }

        SuccessAlertDialog(textToWrite).show()
    }

    fun formattingRawStringInfoGradle(isKts: Boolean, gradleName: String, gradleVer: String): String {
        return when (isKts) {
            true -> """
                    implementation("$gradleName:$gradleVer")
                """.trimIndent()

            else -> """
                    implementation "$gradleName:$gradleVer"
                """.trimIndent()
        }
    }

    fun getListOfAdditionalGradle(): List<String> {
        return listOf(
            "androidx.lifecycle:lifecycle-viewmodel-ktx",
            "androidx.lifecycle:lifecycle-livedata-ktx",
            "org.jetbrains.kotlinx:kotlinx-coroutines-android",
            "com.squareup.retrofit2:retrofit",
            "com.squareup.retrofit2:converter-gson"
        )
    }
    fun getListOfAdditionalGradleVersion(): List<String> {
        return listOf(
            "2.8.7",
            "2.8.7",
            "1.8.1",
            "2.11.0",
            "2.11.0"
        )
    }
}

class GenerateSolidArchDialog(project: Project?) : DialogWrapper(project) {
    private val activityField = JTextField()
    private val viewModelField = JTextField()
    private val useCaseField = JTextField()
    private val repositoryField = JTextField()

    init {
        title = "Generate Android Clean Architecture Skeleton"
        init()
    }

    override fun doValidate(): ValidationInfo? {
        if (activityField.text.isBlank()) {
            return ValidationInfo(
                "Activity name cannot be empty",
                activityField
            )
        }

        if (viewModelField.text.isBlank()) {
            return ValidationInfo(
                "ViewModel name cannot be empty",
                viewModelField
            )
        }

        if (useCaseField.text.isBlank()) {
            return ValidationInfo(
                "UseCase name cannot be empty",
                useCaseField
            )
        }

        if (repositoryField.text.isBlank()) {
            return ValidationInfo(
                "Repository name cannot be empty",
                repositoryField
            )
        }

        if (activityField.text.any { it.isDigit() }) {
            return ValidationInfo(
                "Activity name cannot contains numbers",
                activityField
            )
        }

        if (viewModelField.text.any { it.isDigit() }) {
            return ValidationInfo(
                "ViewModel name cannot contains numbers",
                viewModelField
            )
        }

        if (useCaseField.text.any { it.isDigit() }) {
            return ValidationInfo(
                "UseCase name cannot contains numbers",
                useCaseField
            )
        }

        if (repositoryField.text.any { it.isDigit() }) {
            return ValidationInfo(
                "Repository name cannot contains numbers",
                repositoryField
            )
        }

        val values = listOf(activityField.text.trim().lowercase(), viewModelField.text.trim().lowercase(), useCaseField.text.trim().lowercase(), repositoryField.text.trim().lowercase())
        val allSame = values.all { it == values.first() }

        if (allSame) {
            return ValidationInfo(
                "Cannot contains similar identical name among 4 fields",
                repositoryField
            )
        }
        return null
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.minimumSize = Dimension(400, 200)
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // helper to make fields stretch horizontally
        fun stretch(field: JTextField) {
            field.alignmentX = JComponent.LEFT_ALIGNMENT
            field.maximumSize = Dimension(Int.MAX_VALUE, field.preferredSize.height)
        }

        // Activity
        val labelActivity = JLabel("Insert Main Activity Class Name:")
        activityField.text = "MainActivity"
        labelActivity.alignmentX = JComponent.LEFT_ALIGNMENT
        panel.add(labelActivity)
        panel.add(Box.createVerticalStrut(4))
        stretch(activityField)
        panel.add(activityField)

        // ViewModel
        val labelVM = JLabel("Insert ViewModel Name (e.g UserViewModel):")
        labelVM.alignmentX = JComponent.LEFT_ALIGNMENT
        panel.add(labelVM)
        panel.add(Box.createVerticalStrut(4))
        stretch(viewModelField)
        panel.add(viewModelField)
        panel.add(Box.createVerticalStrut(10))

        // UseCase
        val labelUseCase = JLabel("Insert UseCase (e.g GetUserUseCase):")
        labelUseCase.alignmentX = JComponent.LEFT_ALIGNMENT
        panel.add(labelUseCase)
        panel.add(Box.createVerticalStrut(4))
        stretch(useCaseField)
        panel.add(useCaseField)
        panel.add(Box.createVerticalStrut(10))

        // Repository
        val labelRepos = JLabel("Insert Repository Name (e.g UserRepository):")
        labelRepos.alignmentX = JComponent.LEFT_ALIGNMENT
        panel.add(labelRepos)
        panel.add(Box.createVerticalStrut(4))
        stretch(repositoryField)
        panel.add(repositoryField)

        return panel
    }

    fun getActivityName() = activityField.text
    fun getViewModelName() = viewModelField.text
    fun getUseCaseName() = useCaseField.text
    fun getRepositoryName() = repositoryField.text
}

class SuccessAlertDialog(val gradle: String) : DialogWrapper(true) {
    init {
        title = "Info"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val title = if (gradle.isEmpty()) {
            "File generated successfully! please sync your build.gradle"
        } else {
            "File generated successfully!\n\nWill auto write these to your build.gradle file. please sync your build.gradle"
        }

        val panel = JPanel(BorderLayout())

        val textArea = JTextArea(title).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            background = UIManager.getColor("Label.background")
        }

        val headerPane = JScrollPane(textArea).apply {
            preferredSize = Dimension(400, 60)
            border = BorderFactory.createEmptyBorder()
        }

        val codeArea = JTextArea(gradle).apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 13)
            margin = JBUI.insets(8)
            tabSize = 2
        }

        val codePane = JBScrollPane(codeArea).apply {
            preferredSize = Dimension(520, 130)
        }

        panel.add(headerPane, BorderLayout.NORTH)

        if (gradle.isNotEmpty()) {
            panel.add(codePane, BorderLayout.CENTER)
        }

        return panel
    }
}
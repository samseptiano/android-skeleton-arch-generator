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
            val entity = dialog.getEntityName()
            val database = dialog.getDatabaseName()

            if (project != null
                && activity.isNotEmpty()
                && viewModel.isNotEmpty()
                && useCase.isNotEmpty()
                && repository.isNotEmpty()
                && entity.isNotEmpty()
                && database.isNotEmpty()
            ) {

                val activityFormat =
                    if (activity.any { it.isWhitespace() }) activity.toPascalCase() else activity.capitalizeFirstChar()
                val viewModelFormat =
                    if (viewModel.any { it.isWhitespace() }) viewModel.toPascalCase() else viewModel.capitalizeFirstChar()
                val useCaseFormat =
                    if (useCase.any { it.isWhitespace() }) useCase.toPascalCase() else useCase.capitalizeFirstChar()
                val repositoryFormat =
                    if (repository.any { it.isWhitespace() }) repository.toPascalCase() else repository.capitalizeFirstChar()
                val entityFormat =
                    if (entity.any { it.isWhitespace() }) entity.toPascalCase() else entity.capitalizeFirstChar()
                val databaseFormat =
                    if (database.any { it.isWhitespace() }) database.toPascalCase() else database.capitalizeFirstChar()

                FileGenerator(
                    project,
                    activityFormat,
                    viewModelFormat,
                    useCaseFormat,
                    repositoryFormat,
                    entityFormat,
                    databaseFormat
                ).execute()
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
                    textToGenerate += formattingRawStringInfoGradle(
                        isKtsFile,
                        gradleFile,
                        gradleVer = getListOfAdditionalGradleVersion()[i]
                    )
                    textToGenerate += "\n"

                    textToWrite += formattingRawStringInfoGradle(
                        isKtsFile,
                        gradleFile,
                        gradleVer = getListOfAdditionalGradleVersion()[i]
                    )
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
            "com.squareup.retrofit2:converter-gson",
            "androidx.room:room-runtime",
            "androidx.room:room-ktx",
            "androidx.room:room-compiler"
        )
    }

    fun getListOfAdditionalGradleVersion(): List<String> {
        return listOf(
            "2.8.7",
            "2.8.7",
            "1.8.1",
            "2.11.0",
            "2.11.0",
            "2.6.0",
            "2.6.0",
            "2.6.0"
        )
    }

    class GenerateSolidArchDialog(project: Project?) : DialogWrapper(project) {
        private val activityField = JTextField("MainActivity")
        private val viewModelField = JTextField("MainViewModel")
        private val useCaseField = JTextField("GetDataUseCase")
        private val repositoryField = JTextField("MainRepository")
        private val entityField = JTextField("User")
        private val dbField = JTextField("AppDatabase")

        init {
            title = "Generate S.O.L.I.D Arch + Room DB"
            init()
        }

        override fun doValidate(): ValidationInfo? {
            val fields = listOf(
                activityField to "Activity",
                viewModelField to "ViewModel",
                useCaseField to "UseCase",
                repositoryField to "Repository",
                entityField to "Entity",
                dbField to "Database"
            )

            for ((field, name) in fields) {
                val text = field.text.trim()
                if (text.isBlank()) return ValidationInfo("$name name cannot be empty", field)
                if (text.any { it.isDigit() }) return ValidationInfo("$name name cannot contain numbers", field)
            }

            val values = fields.map { it.first.text.trim().lowercase() }
            if (values.distinct().size < values.size) {
                return ValidationInfo("All names must be unique (case-insensitive)", repositoryField)
            }

            return null
        }

        override fun createCenterPanel(): JComponent {
            val panel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                minimumSize = Dimension(450, 320)
                border = BorderFactory.createEmptyBorder(15, 15, 15, 15)
            }

            fun addField(labelText: String, field: JTextField) {
                val label = JLabel(labelText).apply { alignmentX = JComponent.LEFT_ALIGNMENT }
                panel.add(label)
                panel.add(Box.createVerticalStrut(4))
                field.apply {
                    alignmentX = JComponent.LEFT_ALIGNMENT
                    maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                }
                panel.add(field)
                panel.add(Box.createVerticalStrut(10))
            }

            addField("Activity FileName:", activityField)
            addField("ViewModel name:  (e.g. UserVM)", viewModelField)
            addField("UseCase name:  (e.g. GetUserUseCase)", useCaseField)
            addField("Repository name  (e.g. UserRepository):", repositoryField)
            addField("Entity name (e.g. User):", entityField)
            addField("Database class name (e.g. UserDatabase):", dbField)

            return panel
        }

        fun getActivityName() = activityField.text.trim()
        fun getViewModelName() = viewModelField.text.trim()
        fun getUseCaseName() = useCaseField.text.trim()
        fun getRepositoryName() = repositoryField.text.trim()
        fun getEntityName() = entityField.text.trim()
        fun getDatabaseName() = dbField.text.trim()
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
                "File generated successfully!\n\nWill auto write these to your build.gradle file (please sync your build.gradle)"
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
}
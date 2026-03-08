// FileGenerator.kt
package org.samseptiano.demoplugin

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile

class FileGenerator(
    private val project: Project,
    private val activity: String,
    private val viewModel: String,
    private val useCase: String,
    private val repository: String,
    private val entity: String,      // Entity name, e.g. "User"
    private val database: String     // Database name, e.g. "AppDatabase"
) {

    private val modelName = "SampleModel"
    private val apiServiceName = "ApiService"
    private val retrofitInstance = "RetrofitInstance"

    fun execute() {
        val targetDir = getTargetDirectory(activity, project) ?: return
        val packageName = JavaDirectoryService.getInstance().getPackage(targetDir)?.qualifiedName ?: return

        // Generate folders (sudah OK, tapi pastikan nested benar)
        generatePackageFolder(project, targetDir, "model")
        generatePackageFolder(project, targetDir, "network")
        generatePackageFolder(project, targetDir, "view_model")
        generatePackageFolder(project, targetDir, "use_case")
        generatePackageFolder(project, targetDir, "repository")
        generatePackageFolder(project, targetDir, "data/local/entity")   // nested OK
        generatePackageFolder(project, targetDir, "data/local/dao")
        generatePackageFolder(project, targetDir, "data/local/db")

        // Ambil subdirectories dengan aman (jangan pakai findSubdirectory langsung untuk nested)
        val targetDirModel    = targetDir.findSubdirectory("model")
        val targetDirNetwork  = targetDir.findSubdirectory("network")
        val targetDirVm       = targetDir.findSubdirectory("view_model")
        val targetDirUc       = targetDir.findSubdirectory("use_case")
        val targetDirRepo     = targetDir.findSubdirectory("repository")

        // Untuk Room nested: ambil step by step
        val dataDir    = targetDir.findSubdirectory("data")
        val localDir   = dataDir?.findSubdirectory("local")
        val entityDir  = localDir?.findSubdirectory("entity")
        val daoDir     = localDir?.findSubdirectory("dao")
        val dbDir      = localDir?.findSubdirectory("db")

        // Generate files (non-Room tetap sama)
        targetDirModel?.let   { generateModelFile(modelName, "$packageName.model", it) }
        targetDirNetwork?.let { generateRetrofitFile(modelName, packageName, it) }
        targetDirRepo?.let {
            generateInterfaceRepositoryFile(repository, "$packageName.repository", modelName, it)
            generateRepositoryFile(repository, "$packageName.repository", modelName, it, apiServiceName)
        }
        targetDirUc?.let {
            generateInterfaceUseCaseFile(useCase, "$packageName.use_case", modelName, it)
            generateUseCaseFile(useCase, "$packageName.use_case", it, modelName, repository)
        }
        targetDirVm?.let { generateViewModelFile(viewModel, "$packageName.view_model", it, useCase, modelName) }

        // Generate Room files (pakai variable aman)
        entityDir?.let  { generateEntityFile(entity, "$packageName.data.local.entity", it) }
        daoDir?.let     { generateDaoFile(entity, "$packageName.data.local.dao", it) }
        dbDir?.let      { generateDatabaseFile(database, "$packageName.data.local.db", entity, it) }

        // Inject ViewModel tetap sama
        val psiFile = getActivityFile(activity, project) ?: return
        declareViewModelIntoActivity(psiFile, project, activity, packageName,
            "$packageName.network.$retrofitInstance",
            "$packageName.repository.${repository}Impl",
            "$packageName.use_case.${useCase}Impl",
            "$packageName.view_model.$viewModel")
    }

    private fun generateModelFile(modelName: String, packageName: String, targetDir: PsiDirectory) {
        WriteCommandAction.runWriteCommandAction(project) {
            val className = modelName

            val existingFile = targetDir.findFile("$className.kt")
            if (existingFile != null) return@runWriteCommandAction

            val content = """
                package $packageName
                
                data class $modelName(
                    val id: Int,
                    val name: String,
                    val listData: List<String>
                )
            """.trimIndent()

            val file = PsiFileFactory.getInstance(project)
                .createFileFromText("$className.kt", KotlinLanguage.INSTANCE, content) as KtFile

            CodeStyleManager.getInstance(project).reformat(file)
            targetDir.add(file)
        }
    }

    private fun generateRetrofitFile(modelName: String, packageName: String, targetDir: PsiDirectory) {
        // ApiService
        WriteCommandAction.runWriteCommandAction(project) {
            val existingFile = targetDir.findFile("$apiServiceName.kt")
            if (existingFile != null) return@runWriteCommandAction

            val content = """
                package $packageName.network
                
                import $packageName.model.$modelName
                import retrofit2.http.GET
                
                interface $apiServiceName {
                    @GET("api/data/first")
                    suspend fun getSingleData(): $modelName
                    
                    @GET("api/list")
                    suspend fun getListData(): List<$modelName>
                    
                    @GET("api/data/{id}")
                    suspend fun getDataById(id: Int): $modelName
                }
            """.trimIndent()

            val file = PsiFileFactory.getInstance(project)
                .createFileFromText("$apiServiceName.kt", KotlinLanguage.INSTANCE, content) as KtFile

            CodeStyleManager.getInstance(project).reformat(file)
            targetDir.add(file)
        }

        // RetrofitInstance
        WriteCommandAction.runWriteCommandAction(project) {
            val existingFile = targetDir.findFile("$retrofitInstance.kt")
            if (existingFile != null) return@runWriteCommandAction

            val content = """
                package $packageName.network
                
                import retrofit2.Retrofit
                import retrofit2.converter.gson.GsonConverterFactory
                
                object $retrofitInstance {
                    private const val BASE_URL = "YOUR_BASE_URL_HERE"
                
                    val api: $apiServiceName by lazy {
                        Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()
                            .create($apiServiceName::class.java)
                    }
                }
            """.trimIndent()

            val file = PsiFileFactory.getInstance(project)
                .createFileFromText("$retrofitInstance.kt", KotlinLanguage.INSTANCE, content) as KtFile

            CodeStyleManager.getInstance(project).reformat(file)
            targetDir.add(file)
        }
    }

    private fun generateRepositoryFile(fileName: String, packageName: String, modelName: String, targetDir: PsiDirectory, apiService: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            val className = "${fileName}Impl"

            val existingFile = targetDir.findFile("$className.kt")
            if (existingFile != null) return@runWriteCommandAction

            val content = """
                package $packageName
                
                import $packageName.model.$modelName
                import $packageName.network.$apiService
                
                class $className(private val api: $apiService) : $fileName {
                    override suspend fun getSingleData(): $modelName = api.getSingleData()
                    override suspend fun getListData(): List<$modelName> = api.getListData()
                    override suspend fun getDataById(id: Int): $modelName = api.getDataById(id)
                }
            """.trimIndent()

            val file = PsiFileFactory.getInstance(project)
                .createFileFromText("$className.kt", KotlinLanguage.INSTANCE, content) as KtFile

            CodeStyleManager.getInstance(project).reformat(file)
            targetDir.add(file)
        }
    }

    private fun generateInterfaceRepositoryFile(fileName: String, packageName: String, modelName: String, targetDir: PsiDirectory) {
        WriteCommandAction.runWriteCommandAction(project) {
            val existingFile = targetDir.findFile("$fileName.kt")
            if (existingFile != null) return@runWriteCommandAction

            val content = """
                package $packageName
                
                import $packageName.model.$modelName
                
                interface $fileName {
                    suspend fun getSingleData(): $modelName
                    suspend fun getListData(): List<$modelName>
                    suspend fun getDataById(id: Int): $modelName
                }
            """.trimIndent()

            val file = PsiFileFactory.getInstance(project)
                .createFileFromText("$fileName.kt", KotlinLanguage.INSTANCE, content) as KtFile

            CodeStyleManager.getInstance(project).reformat(file)
            targetDir.add(file)
        }
    }

    private fun generateUseCaseFile(fileName: String, packageName: String, targetDir: PsiDirectory, modelName: String, repository: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            val className = "${fileName}Impl"

            val existingFile = targetDir.findFile("$className.kt")
            if (existingFile != null) return@runWriteCommandAction

            val content = """
                package $packageName
                
                import $packageName.model.$modelName
                import $packageName.repository.$repository
                
                class $className(private val repository: $repository) : $fileName {
                    override suspend fun getSingleData(): $modelName = repository.getSingleData()
                    override suspend fun getListData(): List<$modelName> = repository.getListData()
                    override suspend fun getDataById(id: Int): $modelName = repository.getDataById(id)
                }
            """.trimIndent()

            val file = PsiFileFactory.getInstance(project)
                .createFileFromText("$className.kt", KotlinLanguage.INSTANCE, content) as KtFile

            CodeStyleManager.getInstance(project).reformat(file)
            targetDir.add(file)
        }
    }

    private fun generateInterfaceUseCaseFile(fileName: String, packageName: String, modelName: String, targetDir: PsiDirectory) {
        WriteCommandAction.runWriteCommandAction(project) {
            val existingFile = targetDir.findFile("$fileName.kt")
            if (existingFile != null) return@runWriteCommandAction

            val content = """
                package $packageName
                
                import $packageName.model.$modelName
                
                interface $fileName {
                    suspend fun getSingleData(): $modelName
                    suspend fun getListData(): List<$modelName>
                    suspend fun getDataById(id: Int): $modelName
                }
            """.trimIndent()

            val file = PsiFileFactory.getInstance(project)
                .createFileFromText("$fileName.kt", KotlinLanguage.INSTANCE, content) as KtFile

            CodeStyleManager.getInstance(project).reformat(file)
            targetDir.add(file)
        }
    }

    private fun generateViewModelFile(fileName: String, packageName: String, targetDir: PsiDirectory, useCase: String, modelName: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            val existingFile = targetDir.findFile("$fileName.kt")
            if (existingFile != null) return@runWriteCommandAction

            val content = """
                package $packageName
                
                import androidx.lifecycle.ViewModel
                import androidx.lifecycle.viewModelScope
                import androidx.lifecycle.MutableLiveData
                import androidx.lifecycle.LiveData
                import $packageName.model.$modelName
                import $packageName.use_case.$useCase
                import kotlinx.coroutines.launch
                
                class $fileName(private val useCase: $useCase) : ViewModel() {
                
                    private val _model = MutableLiveData<List<$modelName>>()
                    val model: LiveData<List<$modelName>> = _model
                
                    fun fetchListData() {
                        viewModelScope.launch {
                            _model.value = useCase.getListData()
                        }
                    }
                    
                    fun fetchSingleData() {
                        viewModelScope.launch {
                            _model.value = listOf(useCase.getSingleData())
                        }
                    }
                    
                    fun fetchSingleDataById(id: Int) {
                        viewModelScope.launch {
                            _model.value = listOf(useCase.getDataById(id))
                        }
                    }
                }
            """.trimIndent()

            val file = PsiFileFactory.getInstance(project)
                .createFileFromText("$fileName.kt", KotlinLanguage.INSTANCE, content) as KtFile

            CodeStyleManager.getInstance(project).reformat(file)
            targetDir.add(file)
        }
    }

    private fun generateEntityFile(entityName: String, packageName: String, targetDir: PsiDirectory) {
        WriteCommandAction.runWriteCommandAction(project) {
            val fileName = "$entityName.kt"
            val existingFile = targetDir.findFile(fileName)
            if (existingFile != null) return@runWriteCommandAction

            val content = """
                package $packageName
                
                import androidx.room.Entity
                import androidx.room.PrimaryKey
                
                @Entity(tableName = "${entityName.lowercase()}")
                data class $entityName(
                    @PrimaryKey(autoGenerate = true) val id: Int = 0,
                    val name: String = "",
                    val listData: List<String> = emptyList()
                )
            """.trimIndent()

            val file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, KotlinLanguage.INSTANCE, content) as KtFile

            CodeStyleManager.getInstance(project).reformat(file)
            targetDir.add(file)
        }
    }

    private fun generateDaoFile(entityName: String, packageName: String, targetDir: PsiDirectory) {
        WriteCommandAction.runWriteCommandAction(project) {
            val fileName = "${entityName}Dao.kt"
            val existingFile = targetDir.findFile(fileName)
            if (existingFile != null) return@runWriteCommandAction

            val content = """
                package $packageName
                
                import androidx.room.Dao
                import androidx.room.Insert
                import androidx.room.OnConflictStrategy
                import androidx.room.Query
                import androidx.room.Delete
                
                @Dao
                interface ${entityName}Dao {
                    @Query("SELECT * FROM ${entityName.lowercase()}")
                    suspend fun getAll(): List<$entityName>
                
                    @Insert(onConflict = OnConflictStrategy.REPLACE)
                    suspend fun insert(entity: $entityName)
                
                    @Delete
                    suspend fun delete(entity: $entityName)
                
                    @Query("SELECT * FROM ${entityName.lowercase()} WHERE id = :id")
                    suspend fun getById(id: Int): $entityName?
                }
            """.trimIndent()

            val file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, KotlinLanguage.INSTANCE, content) as KtFile

            CodeStyleManager.getInstance(project).reformat(file)
            targetDir.add(file)
        }
    }

    private fun generateDatabaseFile(dbName: String, packageName: String, entityName: String, targetDir: PsiDirectory) {
        WriteCommandAction.runWriteCommandAction(project) {
            val fileName = "$dbName.kt"
            val existingFile = targetDir.findFile(fileName)
            if (existingFile != null) return@runWriteCommandAction

            val content = """
                package $packageName
                
                import androidx.room.Database
                import androidx.room.Room
                import androidx.room.RoomDatabase
                import android.content.Context
                
                @Database(entities = [$entityName::class], version = 1, exportSchema = false)
                abstract class $dbName : RoomDatabase() {
                    abstract fun ${entityName.lowercase()}Dao(): ${entityName}Dao
                
                    companion object {
                        @Volatile
                        private var INSTANCE: $dbName? = null
                
                        fun getDatabase(context: Context): $dbName {
                            return INSTANCE ?: synchronized(this) {
                                val instance = Room.databaseBuilder(
                                    context.applicationContext,
                                    $dbName::class.java,
                                    "${dbName.lowercase()}"
                                ).build()
                                INSTANCE = instance
                                instance
                            }
                        }
                    }
                }
            """.trimIndent()

            val file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, KotlinLanguage.INSTANCE, content) as KtFile

            CodeStyleManager.getInstance(project).reformat(file)
            targetDir.add(file)
        }
    }

    private fun generatePackageFolder(project: Project, targetDir: PsiDirectory, newPackageName: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            val parts = newPackageName.split("/")
            var currentDir = targetDir
            for (part in parts) {
                val existing = currentDir.findSubdirectory(part)
                currentDir = existing ?: currentDir.createSubdirectory(part)
            }
        }
    }

    private fun getTargetDirectory(activityName: String, project: Project): PsiDirectory? {
        val classes = PsiShortNamesCache.getInstance(project).getClassesByName(activityName, GlobalSearchScope.projectScope(project))
        val mainActivity = classes.firstOrNull() ?: return null
        val file = mainActivity.containingFile ?: return null
        return file.containingDirectory
    }

    private fun getActivityFile(activityName: String, project: Project): PsiFile? {
        val classes = PsiShortNamesCache.getInstance(project).getClassesByName(activityName, GlobalSearchScope.projectScope(project))
        val activityClass = classes.firstOrNull() ?: return null
        val file = activityClass.containingFile ?: return null
        if (!file.name.endsWith(".kt")) return null
        return file
    }

    private fun declareViewModelIntoActivity(
        psiFile: PsiFile,
        project: Project,
        activityName: String,
        packageName: String,
        retrofitInstanceFullName: String,
        repositoryClassFullName: String,
        useCaseClassFullName: String,
        viewModelClassFullName: String
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return@runWriteCommandAction
            var text = document.text

            val classSignature = "class $activityName : AppCompatActivity()"
            val classIndex = text.indexOf(classSignature)
            if (classIndex == -1) return@runWriteCommandAction

            val retrofitImport = "import $retrofitInstanceFullName\n"
            if (!text.contains(retrofitImport.trim())) {
                document.insertString(classIndex, retrofitImport)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }

            val remainingImports = """
                import $repositoryClassFullName
                import $useCaseClassFullName
                import $viewModelClassFullName
            """.trimIndent()

            if (!text.contains(repositoryClassFullName)) {
                document.insertString(classIndex, "$remainingImports\n")
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }

            text = document.text  // Refresh text

            val updatedClassIndex = text.indexOf(classSignature)
            val braceIndex = text.indexOf("{", updatedClassIndex)
            if (braceIndex == -1) return@runWriteCommandAction

            val viewModelCode = """
                private val ${viewModelClassFullName.substringAfterLast(".").lowercase()} by lazy {
                    val repository = ${repositoryClassFullName.substringAfterLast(".")}(RetrofitInstance.api)
                    val useCase = ${useCaseClassFullName.substringAfterLast(".")}(repository)
                    ${viewModelClassFullName.substringAfterLast(".")}(useCase)
                }
            """.trimIndent()

            if (!text.contains(viewModelCode)) {
                document.insertString(braceIndex + 1, "\n$viewModelCode\n")
            }

            PsiDocumentManager.getInstance(project).commitDocument(document)
            CodeStyleManager.getInstance(project).reformat(psiFile)
        }
    }
}
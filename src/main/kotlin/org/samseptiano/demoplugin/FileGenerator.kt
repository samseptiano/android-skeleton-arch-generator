package org.samseptiano.demoplugin
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.PsiDocumentManager.getInstance
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache

class FileGenerator(
    private val project: Project,
    private val activity: String,
    private val viewModel: String,
    private val useCase: String,
    private val repository: String
) {

    private val modelName = "SampleModel"
    private val apiServiceName = "ApiService"
    private val retrofitInstance = "RetrofitInstance"

    fun execute() {
        val targetDir = getTargetDirectory(activity, project) ?: return
        val packageName = JavaDirectoryService
            .getInstance()
            .getPackage(targetDir)
            ?.qualifiedName ?: return

        // Generate folder
        generatePackageFolder(project, targetDir, "model")
        generatePackageFolder(project, targetDir, "network")
        generatePackageFolder(project, targetDir, "view_model")
        generatePackageFolder(project, targetDir, "use_case")
        generatePackageFolder(project, targetDir, "repository")

        // Generate file
        val targetDirMdl = targetDir.findSubdirectory("model")
        val targetDirNtwk = targetDir.findSubdirectory("network")
        val targetDirVm = targetDir.findSubdirectory("view_model")
        val targetDirUC = targetDir.findSubdirectory("use_case")
        val targetDirRp = targetDir.findSubdirectory("repository")

        if (targetDirMdl == null) return
        generateModelFile(modelName, "$packageName.model",  targetDirMdl)

        if (targetDirNtwk == null) return
        generateRetrofitFile(modelName, packageName,  targetDirNtwk)

        if (targetDirRp == null) return
        generateInterfaceRepositoryFile(repository, packageName,  modelName, targetDirRp)
        generateRepositoryFile(repository, packageName,  modelName, targetDirRp, apiServiceName)

        if (targetDirUC == null) return
        generateInterfaceUseCaseFile(useCase, packageName, modelName, targetDirUC)
        generateUseCaseFile(useCase, packageName, targetDirUC, modelName, repository)

        if (targetDirVm == null) return
        generateViewModelFile(viewModel, packageName,  targetDirVm, useCase, modelName)

        // declare view model into activity
        val psiFile = getActivityFile(activity, project) ?: return
        declareViewModelIntoActivity(psiFile, project, activity,
            packageName,
            "$packageName.network.$retrofitInstance",
            "$packageName.repository.${repository}Impl",
            "$packageName.use_case.${useCase}Impl" ,
            "$packageName.view_model.$viewModel")
    }

    fun generateModelFile(modelName: String, packageName: String, targetDir: PsiDirectory) {
        WriteCommandAction.runWriteCommandAction(project) {
            val className = modelName

            // Check if file already exists in targetDir
            val existingFile = targetDir.findFile("$className.kt")
            if (existingFile != null) {
                println("File $className already exists. Skipping generation.")
                return@runWriteCommandAction
            }

            val content = """
                package $packageName
                
                data class $modelName(
                    val id: Int,
                    val name: String,
                    val listData: List<String>
                )
            """.trimIndent()

            val file = PsiFileFactory.getInstance(project)
                .createFileFromText("$className.kt", content)

            targetDir.add(file)
        }
    }

    fun generateRetrofitFile(modelName: String, packageName: String, targetDir: PsiDirectory) {
        // ApiService
        WriteCommandAction.runWriteCommandAction(project) {
            // Check if file already exists in targetDir
            val existingFile = targetDir.findFile("$apiServiceName.kt")
            if (existingFile != null) {
                println("File $apiServiceName already exists. Skipping generation.")
                return@runWriteCommandAction
            }

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
                .createFileFromText("$apiServiceName.kt", content)

            targetDir.add(file)
        }

        // Retrofit Instance
        WriteCommandAction.runWriteCommandAction(project) {
            // Check if file already exists in targetDir
            val existingFile = targetDir.findFile("$retrofitInstance.kt")
            if (existingFile != null) {
                println("File $retrofitInstance already exists. Skipping generation.")
                return@runWriteCommandAction
            }

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
                .createFileFromText("$retrofitInstance.kt", content)

            targetDir.add(file)
        }
    }

    fun generateRepositoryFile(fileName: String, packageName: String, modelName: String, targetDir: PsiDirectory, apiservice: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            val className = fileName

            // Check if file already exists in targetDir
            val existingFile = targetDir.findFile("${className}Impl.kt")
            if (existingFile != null) {
                println("File $className already exists. Skipping generation.")
                return@runWriteCommandAction
            }

            val content = """
                package $packageName.repository
                import $packageName.model.$modelName
                import $packageName.network.$apiservice
                
                class ${className}Impl(
                    private val api: $apiservice
                ) : $className {
                    override suspend fun getSingleData() : $modelName {  
                        return api.getSingleData()
                    }
                    override suspend fun getListData() : List<$modelName> {
                        return api.getListData()
                    }
                    override suspend fun getDataById(id: Int) : $modelName {   
                        return api.getDataById(id)
                    }
                }
            """.trimIndent()

            val file = PsiFileFactory.getInstance(project)
                .createFileFromText("${className}Impl.kt", content)

            targetDir.add(file)
        }
    }

    fun generateInterfaceRepositoryFile(fileName: String, packageName: String, modelName: String, targetDir: PsiDirectory) {
        WriteCommandAction.runWriteCommandAction(project) {
            val interfaceName = fileName

            // Check if file already exists in targetDir
            val existingFile = targetDir.findFile("$interfaceName.kt")
            if (existingFile != null) {
                println("File $interfaceName already exists. Skipping generation.")
                return@runWriteCommandAction
            }

            val content = """
                package $packageName.repository
                import $packageName.model.$modelName
                
                interface $interfaceName {
                    suspend fun getSingleData() : $modelName
                    suspend fun getListData() : List<$modelName>
                    suspend fun getDataById(id: Int) : $modelName
                }
            """.trimIndent()

            val file = PsiFileFactory.getInstance(project)
                .createFileFromText("$interfaceName.kt", content)

            targetDir.add(file)
        }
    }


    fun generateInterfaceUseCaseFile(fileName: String, packageName: String, modelName: String, targetDir: PsiDirectory) {
        WriteCommandAction.runWriteCommandAction(project) {
            val interfaceName = fileName

            // Check if file already exists in targetDir
            val existingFile = targetDir.findFile("$interfaceName.kt")
            if (existingFile != null) {
                println("File $interfaceName already exists. Skipping generation.")
                return@runWriteCommandAction
            }

            val content = """
                package $packageName.use_case
                import $packageName.model.$modelName
                
                interface $interfaceName {
                    suspend fun getSingleData() : $modelName
                    suspend fun getListData() : List<$modelName>
                    suspend fun getDataById(id: Int) : $modelName
                }
            """.trimIndent()

            val file = PsiFileFactory.getInstance(project)
                .createFileFromText("$interfaceName.kt", content)

            targetDir.add(file)
        }
    }

    fun generateUseCaseFile(fileName: String, packageName: String, targetDir: PsiDirectory, modelName: String, repository: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            val className = fileName

            // Check if file already exists in targetDir
            val existingFile = targetDir.findFile("${className}Impl.kt")
            if (existingFile != null) {
                println("File $className already exists. Skipping generation.")
                return@runWriteCommandAction
            }

            val content = """
                package $packageName.use_case
                import $packageName.model.$modelName
                import $packageName.repository.$repository
                
                class ${className}Impl(
                   private val repository: $repository
                ) : $className {
                    override suspend fun getSingleData() : $modelName {  
                        return repository.getSingleData()
                    }
                    override suspend fun getListData() : List<$modelName> {
                        return repository.getListData()
                    }
                    override suspend fun getDataById(id: Int) : $modelName {   
                        return repository.getDataById(id)
                    }
                }
            """.trimIndent()

            val file = PsiFileFactory.getInstance(project)
                .createFileFromText("${className}Impl.kt", content)

            targetDir.add(file)
        }
    }

    fun generateViewModelFile(fileName: String, packageName: String, targetDir: PsiDirectory, useCase: String, modelName: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            val className = fileName

            // Check if file already exists in targetDir
            val existingFile = targetDir.findFile("$className.kt")
            if (existingFile != null) {
                println("File $className already exists. Skipping generation.")
                return@runWriteCommandAction
            }

            val content = """
                package $packageName.view_model
                
                import androidx.lifecycle.ViewModel
                import androidx.lifecycle.viewModelScope
                import androidx.lifecycle.MutableLiveData
                import androidx.lifecycle.LiveData
                import $packageName.model.$modelName
                import $packageName.use_case.$useCase
                import kotlinx.coroutines.launch

                class $className(private val useCase: $useCase) : ViewModel() {

                    private val _model = MutableLiveData<List<$modelName>>()
                    val model: LiveData<List<$modelName>> = _model

                    fun fetchListData() {
                        viewModelScope.launch {
                            _model.value = useCase.getListData()
                        }
                    }
                    
                    fun fetchSingleData() {
                        viewModelScope.launch {
                            _model.value =  listOf(useCase.getSingleData())
                        }
                    }
                    
                    fun fetchSingleDataById(id: Int) {
                        viewModelScope.launch {
                            _model.value =  listOf(useCase.getDataById(id))
                        }
                    }
                }
            """.trimIndent()

            val file = PsiFileFactory.getInstance(project)
                .createFileFromText("$className.kt", content)

            targetDir.add(file)
        }
    }

    fun generatePackageFolder(
        project: Project,
        targetDir: PsiDirectory,
        newPackageName: String
    ) {
        return WriteCommandAction.runWriteCommandAction(project) {
            // check if subfolder already exists
            val existing = targetDir.findSubdirectory(newPackageName)
            if (existing == null) {
                targetDir.createSubdirectory(newPackageName)
            }
        }
    }

    fun getTargetDirectory(activityName: String = "MainActivity", project: Project): PsiDirectory? {
        val classes = PsiShortNamesCache.getInstance(project)
            .getClassesByName(activityName, GlobalSearchScope.projectScope(project))
        val mainActivity: PsiClass = classes.firstOrNull() ?: return null

        val file = mainActivity.containingFile ?: return null
        return file.containingDirectory
    }

    fun getActivityFile(activityName: String, project: Project): PsiFile? {
        val classes = PsiShortNamesCache.getInstance(project)
            .getClassesByName(activityName, GlobalSearchScope.projectScope(project))

        val activityClass = classes.firstOrNull() ?: return null
        val file = activityClass.containingFile ?: return null
        if (!file.name.endsWith(".kt")) return null

        return file
    }

    fun declareViewModelIntoActivity(
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

            val repositoryClassName = repositoryClassFullName.substringAfterLast(".")
            val useCaseClassName = useCaseClassFullName.substringAfterLast(".")
            val viewModelClassName = viewModelClassFullName.substringAfterLast(".")

            val psiManager = getInstance(project)
            val document = psiManager.getDocument(psiFile) ?: return@runWriteCommandAction

            var text = document.text

            val classSignature = "class $activityName : AppCompatActivity()"
            val classIndex = text.indexOf(classSignature)

            if (classIndex == -1) return@runWriteCommandAction

            val retrofitImport = "import $retrofitInstanceFullName\n"

            if (!text.contains(retrofitImport.trim())) {
                document.insertString(classIndex, "$retrofitImport\n")
                psiManager.commitDocument(document)
            }

            val remainingImports = """
                import $repositoryClassFullName
                import $useCaseClassFullName
                import $viewModelClassFullName
        
            """.trimIndent()

            if (!text.contains(repositoryClassFullName)) {
                document.insertString(classIndex, "$remainingImports\n")
                psiManager.commitDocument(document)
            }

            // Re-read updated text
            text = document.text

            val updatedClassIndex = text.indexOf(classSignature)
            val braceIndex = text.indexOf("{", updatedClassIndex)
            if (braceIndex == -1) return@runWriteCommandAction

            val viewModelCode = """
    private val ${viewModelClassName.replaceFirstChar { it.lowercase() }} by lazy {
       val repository = $repositoryClassName($retrofitInstance.api)
       val useCase = $useCaseClassName(repository)
       $viewModelClassName(useCase)
    }"""

            if (!text.contains("private val ${viewModelClassName.replaceFirstChar { it.lowercase() }} by lazy")) {
                document.insertString(braceIndex + 1, "\n$viewModelCode")
            }

            psiManager.commitDocument(document)

            // Auto format code (important for indentation)
            CodeStyleManager.getInstance(project).reformat(psiFile)
        }
    }
}
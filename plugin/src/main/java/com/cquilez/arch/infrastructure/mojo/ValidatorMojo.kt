package com.cquilez.arch.infrastructure.mojo

import com.cquilez.arch.application.service.LayerFinderService
import com.cquilez.arch.application.usecase.AnalyzeProjectUseCase
import com.cquilez.arch.application.service.PatternMatcherService
import com.cquilez.arch.application.service.RuleEvaluatorService
import com.cquilez.arch.application.service.RuleValidatorService
import com.cquilez.arch.application.service.SourceParserService
import com.cquilez.arch.application.service.rule.RuleValidationExecutor
import com.cquilez.arch.domain.AnalysisConfig
import com.cquilez.arch.domain.Project
import com.cquilez.arch.infrastructure.adapter.FilesystemAdapter
import com.cquilez.arch.infrastructure.adapter.KotlinPsiSourceParser
import com.cquilez.arch.infrastructure.adapter.LogAdapter
import com.cquilez.arch.infrastructure.adapter.YamlParserAdapter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.IOException
import java.nio.file.Path
import javax.inject.Inject

@Mojo(name = "validate", defaultPhase = LifecyclePhase.VALIDATE)
class ValidatorMojo : AbstractMojo() {
    @Parameter(defaultValue = $$"${project}")
    private val project: MavenProject? = null

    /**
     * When true, the build fails if no rules file is found. When false, a warning is logged.
     */
    @Parameter(property = "arch.failIfNoRules", defaultValue = "false")
    private val failIfNoRules: Boolean = false

    /**
     * When true, test source roots are included in validation. When false, only main sources are used.
     */
    @Parameter(property = "arch.includeTests", defaultValue = "false")
    private val includeTests: Boolean = false

    @Inject
    private lateinit var logAdapter: LogAdapter

    @Inject
    private lateinit var filesystemAdapter: FilesystemAdapter

    @Throws(MojoExecutionException::class)
    override fun execute() {
        val currentProject = this.project!!
        val rulesFile: Path = currentProject.basedir.toPath().resolve(RULES_FILE_NAME)
        val analysisConfig = AnalysisConfig(failIfNoRules = failIfNoRules, includeTests = includeTests)

        try {
            logAdapter.bind(this.log)

            val allProjects: List<MavenProject> =
                currentProject.collectedProjects?.takeIf { it.isNotEmpty() }?.let { listOf(currentProject) + it }
                    ?: listOf(currentProject)

            val project = Project(
                compileSourceRoots = allProjects
                    .flatMap { it.compileSourceRoots }
                    .filterIsInstance<String>()
                    .distinct(),
                testCompileSourceRoots = allProjects
                    .flatMap { it.testCompileSourceRoots }
                    .filterIsInstance<String>()
                    .distinct()
            )
            executeUseCase(project, rulesFile, analysisConfig)
        } catch (e: IOException) {
            throw MojoExecutionException("Failed to read $RULES_FILE_NAME", e)
        } catch (e: IllegalStateException) {
            throw MojoExecutionException(e.message)
        } catch (e: Exception) {
            throw MojoExecutionException("An error occurred during the analysis", e)
        }
    }

    private fun executeUseCase(
        project: Project,
        rulesFile: Path,
        analysisConfig: AnalysisConfig
    ) {
        val ruleValidatorService = RuleValidatorService(filesystemAdapter)
        val layerFinderService = LayerFinderService()
        val patternMatcherService = PatternMatcherService()
        val kotlinPsiSourceParser = KotlinPsiSourceParser()
        val sourceParserService = SourceParserService(logAdapter, filesystemAdapter, kotlinPsiSourceParser)
        val ruleValidationExecutor = RuleValidationExecutor(patternMatcherService, layerFinderService)
        val ruleEvaluatorService =
            RuleEvaluatorService(logAdapter, filesystemAdapter, layerFinderService, patternMatcherService, ruleValidationExecutor, sourceParserService)
        val analyzeProjectUseCase = AnalyzeProjectUseCase(
            logAdapter,
            filesystemAdapter,
            YamlParserAdapter(),
            ruleValidatorService,
            ruleEvaluatorService
        )
        analyzeProjectUseCase.execute(project, rulesFile, analysisConfig)
    }

    companion object {
        private const val RULES_FILE_NAME = "arch-rules.yml"
    }
}

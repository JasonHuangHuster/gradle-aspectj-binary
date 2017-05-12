package com.github.sedovalx.gradle.aspectj

import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.TrueFileFilter
import org.aspectj.bridge.IMessage
import org.aspectj.tools.ajc.Main
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Paths

open class AjcTask : DefaultTask() {
    init {
        logging.captureStandardOutput(LogLevel.INFO)
    }

    lateinit var sourceSet: SourceSet
    lateinit var buildDir: File

    // Task properties
    var source: String = "1.7"
    var target: String = "1.7"

    @TaskAction
    fun compile() {
        logger.info("=".repeat(30))
        logger.info("=".repeat(30))
        logger.info("Running ajc on classpath: ${getClasspath()}")

        val tempDirectory = Paths.get(buildDir.toPath().toString(), "ajc").toFile()
        if (!tempDirectory.exists()) {
            logger.info("Created temp folder $tempDirectory")
            tempDirectory.mkdirs()
        }
        val logPath = Paths.get(buildDir.toPath().toString(), "ajc.log")

        val ajcParams = arrayOf(
                "-Xset:avoidFinal=true",
                "-Xlint:warning",
                "-inpath",
                sourceSet.output.classesDir.absolutePath,
                "-sourceroots",
                getSourceRoots(),
                "-d",
                tempDirectory.absolutePath,
                "-classpath",
                getClasspath(),
                "-aspectpath",
                getClasspath(),
                "-source",
                this.source,
                "-target",
                this.target,
                "-g:none",
                "-encoding",
                "UTF-8",
                "-time",
                "-log",
                logPath.toString(),
                "-showWeaveInfo",
                "-warn:constructorName",
                "-warn:packageDefaultMethod",
                "-warn:deprecation",
                "-warn:maskedCatchBlocks",
                "-warn:unusedLocals",
                "-warn:unusedArguments",
                "-warn:unusedImports",
                "-warn:syntheticAccess",
                "-warn:assertIdentifier"
        )
        logger.debug("About to run ajc with parameters: \n${ajcParams.toList().joinToString("\t\n")}")

        val currentClasspath = (Thread.currentThread().contextClassLoader as? URLClassLoader)?.urLs?.map { it.path }?.joinToString("\n")
        if (currentClasspath != null) {
            logger.debug("Task classpath:\n" + currentClasspath)
        }

        val msgHolder = try {
            val main = Main()
            MsgHolder(logger).apply {
                main.run(ajcParams, this)
            }
        } catch (ex: Exception) {
            throw GradleException("Error running task", ex)
        }

        try {
            logger.info("ajc completed, processing the temp")
            FileUtils.copyDirectory(tempDirectory, sourceSet.output.classesDir)
            FileUtils.cleanDirectory(tempDirectory)
        } catch (ex: IOException) {
            throw GradleException("Failed to copy files and clean temp", ex)
        }

        logger.info("ajc result: %d file(s) processed, %d pointcut(s) woven, %d error(s), %d warning(s)".format(
                files(sourceSet.output.classesDir).size,
               msgHolder.numMessages(IMessage.WEAVEINFO, false),
               msgHolder.numMessages(IMessage.ERROR, true),
               msgHolder.numMessages(IMessage.WARNING, false)
        ))

        if (msgHolder.hasAnyMessage(IMessage.ERROR, greater = true)) {
            throw GradleException("AJC failed, see messages above. You can run the task with --info or --debug " +
                    "parameters to get more detailed output. Ajc log is stored in the $logPath file.")
        }
    }

    private fun files(dir: File): Collection<File> {
        return FileUtils.listFiles(dir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE).filter { it.isFile }
    }

    /**
     * Comma separated absolute paths to folders with aspects
     */
    private fun getSourceRoots(): String = Files.createTempDirectory("aspects").toAbsolutePath().toString()

    private fun getClasspath(): String {
        return (sourceSet.compileClasspath + sourceSet.runtimeClasspath).asPath
    }

}
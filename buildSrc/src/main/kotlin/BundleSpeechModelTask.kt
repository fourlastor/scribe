import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Lays resolved model artifacts out as Android assets under stable names, so the app does not need
 * to know which model was bundled, and refuses any file whose checksum is not the pinned one.
 */
@DisableCachingByDefault(because = "Copying local model files is cheaper than a build cache round trip")
abstract class BundleSpeechModelTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val artifacts: ConfigurableFileCollection

    /** Resolved artifact file name to its path below the assets root. */
    @get:Input
    abstract val assetPaths: MapProperty<String, String>

    /** Resolved artifact file name to its expected SHA-256. */
    @get:Input
    abstract val checksums: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun bundle() {
        val root = outputDir.get().asFile
        root.deleteRecursively()
        val resolved = artifacts.files.associateBy { it.name }
        val expected = checksums.get()
        assetPaths.get().forEach { (name, assetPath) ->
            val source = resolved[name]
                ?: throw GradleException("Model artifact $name was not resolved. Got: ${resolved.keys}")
            val actual = sha256(source)
            if (actual != expected[name]) {
                throw GradleException(
                    "Checksum mismatch for $name: expected ${expected[name]} but was $actual. " +
                        "The upstream file changed, or the download is corrupt."
                )
            }
            val target = root.resolve(assetPath)
            target.parentFile.mkdirs()
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

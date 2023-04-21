package space.taran.arknavigator.mvp.model.repo.scores

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.taran.arklib.arkFolder
import space.taran.arklib.arkScores
import space.taran.arklib.ResourceId
import space.taran.arknavigator.utils.LogTags.SCORES_STORAGE
import space.taran.arknavigator.utils.Score
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.readLines

class PlainScoreStorage(
    val root: Path,
    val resources: Collection<ResourceId>
) : ScoreStorage {
    private val storageFile = root.arkFolder().arkScores()

    private var lastModified = FileTime.fromMillis(0L)

    private var scoreById: MutableMap<ResourceId, Score>

    init {
        val result = resources.associateWith {
            0
        }.toMutableMap()

        if (Files.exists(storageFile)) {
            lastModified = Files.getLastModifiedTime(storageFile)

            Log.d(
                SCORES_STORAGE,
                "file $storageFile exists, last modified at $lastModified"
            )
            result.putAll(readStorage())
        } else {
            Log.d(SCORES_STORAGE, "file $storageFile doesn't exists")
        }
        scoreById = result
    }

    override fun contains(id: ResourceId) = scoreById.containsKey(id)

    override fun setScore(id: ResourceId, score: Score) {
        if (!scoreById.containsKey(id))
            error("Storage isn't aware of this resource id")

        Log.d(SCORES_STORAGE, "new score for resource $id: $score")
        scoreById[id] = score
    }

    override fun getScore(id: ResourceId) = scoreById[id]!!

    override suspend fun persist() =
        withContext(Dispatchers.IO) {
            writeStorage()
            return@withContext
        }

    override suspend fun resetScores(resources: List<ResourceId>) {
        resources.map {
            if (scoreById.containsKey(it)) {
                scoreById[it] = 0
            }
        }
        persist()
        Log.d(SCORES_STORAGE, "${resources.size} score(s) erased")
    }

    private fun readStorage(): Map<ResourceId, Score> {
        // better to wrap with coroutine
        val lines = Files.readAllLines(storageFile, UTF_8)
        val version = lines.removeAt(0)
        verifyVersion(version)
        val result = lines.map {
            val parts = it.split(KEY_VALUE_SEPARATOR)
            val id = ResourceId.fromString(parts[0])
            val score = parts[1].toInt()

            if (score == 0)
                throw AssertionError(
                    "score storage must have contained un-scored resources"
                )
            id to score
        }.toMap()

        return result
    }

    private fun writeStorage() {
        val lines = mutableListOf<String>()

        lines.add("$STORAGE_VERSION_PREFIX$STORAGE_VERSION")

        val entries = scoreById.filterValues {
            it != 0
        }

        lines.addAll(
            entries.map { (id, score) ->
                "${id.dataSize}-${id.crc32}$KEY_VALUE_SEPARATOR$score"
            }
        )

        Files.write(storageFile, lines, UTF_8)
        lastModified = Files.getLastModifiedTime(storageFile)

        Log.d(SCORES_STORAGE, "${scoreById.size} entries have been added")
        storageFile.readLines().forEach {
            Log.d(SCORES_STORAGE, it)
        }
    }

    companion object {
        private const val STORAGE_VERSION = 2
        private const val STORAGE_VERSION_PREFIX = "version "

        const val KEY_VALUE_SEPARATOR = ':'

        private fun verifyVersion(header: String) {
            if (!header.startsWith(STORAGE_VERSION_PREFIX)) {
                throw IllegalStateException("Unknown storage version")
            }
            val version = header.removePrefix(STORAGE_VERSION_PREFIX).toInt()

            if (version > STORAGE_VERSION) {
                throw IllegalStateException("Storage format is newer than the app")
            }
            if (version < STORAGE_VERSION) {
                throw IllegalStateException("Storage format is older than the app")
            }
        }
    }
}

package moe.momokko.intellido.platform.instance

import moe.momokko.intellido.platform.identity.ReleaseChannel
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

sealed class InstanceAcquireResult {
    data class Acquired(val lock: HeldInstanceLock) : InstanceAcquireResult()
    data class AwakeExisting(val channel: ReleaseChannel, val targets: List<SupportedLaunchTarget>) : InstanceAcquireResult()
    data class OtherChannelRunning(val other: ReleaseChannel) : InstanceAcquireResult()
    data object Busy : InstanceAcquireResult()
}

class HeldInstanceLock(
    val channel: ReleaseChannel,
    val processId: Long,
    private val fileChannel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {
    override fun close() {
        runCatching { lock.release() }
        runCatching { fileChannel.close() }
    }
}

/**
 * One process per OS user across Stable and Nightly. Same-channel relaunches
 * forward supported CLI targets instead of starting a second process.
 *
 * The exclusive lock lives on a dedicated file so Windows can still read occupant
 * metadata from a separate state file.
 */
class ApplicationInstanceCoordinator(
    private val lockDirectory: Path,
    private val channel: ReleaseChannel,
    private val processId: Long,
    private val processAlive: (Long) -> Boolean = { pid ->
        ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
    },
) {
    fun tryAcquire(
        targets: List<SupportedLaunchTarget> = listOf(SupportedLaunchTarget.Focus),
        attempt: Int = 0,
    ): InstanceAcquireResult {
        Files.createDirectories(lockDirectory)
        val channelHandle = FileChannel.open(
            lockFile(),
            StandardOpenOption.CREATE,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
        )
        val lock = try {
            channelHandle.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        }
        if (lock == null) {
            channelHandle.close()
            val occupant = readOccupant()
            if (occupant != null && processAlive(occupant.processId)) {
                return if (occupant.channel == channel) {
                    writeHandoff(targets)
                    InstanceAcquireResult.AwakeExisting(channel, targets)
                } else {
                    InstanceAcquireResult.OtherChannelRunning(occupant.channel)
                }
            }
            if (attempt >= 1) {
                return InstanceAcquireResult.Busy
            }
            Files.deleteIfExists(occupantFile())
            return tryAcquire(targets, attempt + 1)
        }
        writeOccupant()
        return InstanceAcquireResult.Acquired(HeldInstanceLock(channel, processId, channelHandle, lock))
    }

    fun pollHandoff(): List<SupportedLaunchTarget> {
        val file = handoffFile()
        if (!Files.exists(file)) {
            return emptyList()
        }
        val lines = Files.readAllLines(file, StandardCharsets.UTF_8)
        Files.deleteIfExists(file)
        return LaunchTargets.parse(lines.filter { it.isNotBlank() })
    }

    fun writeHandoff(targets: List<SupportedLaunchTarget>) {
        val tokens = targets.map {
            when (it) {
                SupportedLaunchTarget.Focus -> "--focus"
                SupportedLaunchTarget.Home -> "--home"
            }
        }
        Files.write(handoffFile(), tokens, StandardCharsets.UTF_8)
    }

    private fun lockFile(): Path = lockDirectory.resolve("instance.lock")

    private fun occupantFile(): Path = lockDirectory.resolve("occupant.txt")

    private fun handoffFile(): Path = lockDirectory.resolve("handoff-${channel.name.lowercase()}.txt")

    private fun writeOccupant() {
        Files.writeString(occupantFile(), "channel=${channel.name}\npid=$processId\n", StandardCharsets.UTF_8)
    }

    private fun readOccupant(): Occupant? {
        val file = occupantFile()
        if (!Files.exists(file)) {
            return null
        }
        val values = runCatching {
            Files.readString(file, StandardCharsets.UTF_8)
                .lineSequence()
                .map { it.trim() }
                .filter { it.contains('=') }
                .associate { line ->
                    val (key, value) = line.split('=', limit = 2)
                    key to value
                }
        }.getOrNull() ?: return null
        val channelName = values["channel"] ?: return null
        val pid = values["pid"]?.toLongOrNull() ?: return null
        // A corrupt or forward-version occupant file must not abort startup:
        // treat it as "no occupant" so the stale lock is reclaimed instead.
        val occupantChannel = runCatching { ReleaseChannel.valueOf(channelName) }.getOrNull() ?: return null
        return Occupant(occupantChannel, pid)
    }

    private data class Occupant(val channel: ReleaseChannel, val processId: Long)
}

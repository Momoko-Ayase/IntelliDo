package moe.momokko.intellido.platform.instance

import moe.momokko.intellido.platform.identity.ReleaseChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ApplicationInstanceCoordinatorTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `first launch acquires the single-process lock`() {
        val coordinator = coordinator(ReleaseChannel.STABLE, pid = 11)
        val result = coordinator.tryAcquire()
        assertInstanceOf(InstanceAcquireResult.Acquired::class.java, result)
        (result as InstanceAcquireResult.Acquired).lock.close()
    }

    @Test
    fun `second launch of the same channel awakens the existing instance and forwards home`() {
        val first = coordinator(ReleaseChannel.STABLE, pid = 11)
        val acquired = first.tryAcquire() as InstanceAcquireResult.Acquired
        try {
            val second = coordinator(ReleaseChannel.STABLE, pid = 12)
            val result = second.tryAcquire(listOf(SupportedLaunchTarget.Home))
            val awake = assertInstanceOf(InstanceAcquireResult.AwakeExisting::class.java, result)
            assertEquals(listOf(SupportedLaunchTarget.Home), awake.targets)
            assertEquals(listOf(SupportedLaunchTarget.Home), first.pollHandoff())
        } finally {
            acquired.lock.close()
        }
    }

    @Test
    fun `stable and nightly cannot run at the same time`() {
        val stable = coordinator(ReleaseChannel.STABLE, pid = 11)
        val acquired = stable.tryAcquire() as InstanceAcquireResult.Acquired
        try {
            val nightly = coordinator(ReleaseChannel.NIGHTLY, pid = 12)
            val result = nightly.tryAcquire()
            val blocked = assertInstanceOf(InstanceAcquireResult.OtherChannelRunning::class.java, result)
            assertEquals(ReleaseChannel.STABLE, blocked.other)
        } finally {
            acquired.lock.close()
        }
    }

    @Test
    fun `stale occupant with a held lock does not recurse forever`() {
        val first = coordinator(ReleaseChannel.STABLE, pid = 11)
        val acquired = first.tryAcquire() as InstanceAcquireResult.Acquired
        try {
            val second = ApplicationInstanceCoordinator(
                lockDirectory = tempDir,
                channel = ReleaseChannel.STABLE,
                processId = 12,
                processAlive = { false },
            )
            val result = second.tryAcquire()
            assertInstanceOf(InstanceAcquireResult.Busy::class.java, result)
        } finally {
            acquired.lock.close()
        }
    }

    @Test
    fun `handoff watcher delivers Home to the running instance`() {
        val first = coordinator(ReleaseChannel.STABLE, pid = 11)
        val acquired = first.tryAcquire() as InstanceAcquireResult.Acquired
        try {
            val received = mutableListOf<SupportedLaunchTarget>()
            val watcher = InstanceHandoffWatcher(first) { received += it }
            val second = coordinator(ReleaseChannel.STABLE, pid = 12)
            second.tryAcquire(listOf(SupportedLaunchTarget.Home))
            watcher.pollOnce()
            assertEquals(listOf(SupportedLaunchTarget.Home), received)
        } finally {
            acquired.lock.close()
        }
    }

    @Test
    fun `unknown CLI args do not register a URL handler and only focus the app`() {
        val targets = LaunchTargets.parse(
            listOf("https://linux.do/t/1", "intellido://topic/1", "--not-a-handler"),
        )
        assertEquals(listOf(SupportedLaunchTarget.Focus), targets)
        assertTrue(LaunchTargets.parse(listOf("--home")).contains(SupportedLaunchTarget.Home))
    }

    @Test
    fun `a corrupt occupant file reclaims the lock instead of aborting startup`() {
        // A forward-version or truncated occupant file used to throw out of
        // tryAcquire, which left the app with no UI at all.
        java.nio.file.Files.writeString(
            tempDir.resolve("occupant.txt"),
            "channel=FROM_THE_FUTURE\npid=4242\n",
        )
        val result = coordinator(ReleaseChannel.STABLE, pid = 11).tryAcquire()
        val acquired = assertInstanceOf(InstanceAcquireResult.Acquired::class.java, result)
        acquired.lock.close()
    }

    @Test
    fun `an unparseable occupant file is treated as vacant`() {
        java.nio.file.Files.writeString(tempDir.resolve("occupant.txt"), "not-a-record")
        val result = coordinator(ReleaseChannel.STABLE, pid = 11).tryAcquire()
        val acquired = assertInstanceOf(InstanceAcquireResult.Acquired::class.java, result)
        acquired.lock.close()
    }

    private fun coordinator(channel: ReleaseChannel, pid: Long): ApplicationInstanceCoordinator =
        ApplicationInstanceCoordinator(
            lockDirectory = tempDir,
            channel = channel,
            processId = pid,
            processAlive = { true },
        )
}

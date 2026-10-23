package com.closingcount.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.local.ClosingCountDatabase
import com.closingcount.app.data.transfer.BackupJsonCodec
import com.closingcount.app.data.transfer.LocalBackupTransfer
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalBackupTransferInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: ClosingCountDatabase
    private lateinit var files: LocalTransferQaFiles
    private lateinit var transfer: LocalBackupTransfer
    private val initial = localTransferQaSnapshot("Menu sebelum restore")
    private val backup = localTransferQaSnapshot("Menu dari backup", true)
    @Before fun setup(): Unit = runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("ccsLocalTransferQa") == "true")
        db = Room.inMemoryDatabaseBuilder(context, ClosingCountDatabase::class.java).build()
        files = LocalTransferQaFiles(context)
        transfer = LocalBackupTransfer(context.contentResolver, db.backupDao())
        db.backupDao().restore(initial)
    }
    @After fun cleanup() { if (::files.isInitialized) files.close();if (::db.isInitialized) db.close() }

    @Test fun reviewedContentSurvivesProviderChangeAndCanOnlyBeAppliedOnce(): Unit = runBlocking {
        val (uri, name) = files.create("frozen", BackupJsonCodec.encode(backup, 123))
        val review = transfer.inspect(uri)
        assertEquals(name, review.filename);assertEquals(1, review.menus);assertEquals(2, review.ingredients);assertEquals(3, review.closings)
        assertEquals("2024-02-29", review.firstDate);assertEquals("2026-10-23", review.lastDate)
        assertEquals(initial, db.backupDao().createSnapshot())
        files.rewrite(uri, "source changed after review".toByteArray())
        val attempts = listOf(async(Dispatchers.Default) { runCatching { transfer.restore(review.id) } },
            async(Dispatchers.Default) { runCatching { transfer.restore(review.id) } }).awaitAll()
        assertEquals(1, attempts.count { it.isSuccess })
        assertEquals(backup, db.backupDao().createSnapshot())
    }

    @Test fun cancellationAndStaleReviewCannotReplaceCurrentData(): Unit = runBlocking {
        val (uri, _) = files.create("cancel", BackupJsonCodec.encode(backup, 123))
        val old = transfer.inspect(uri);transfer.discard(old.id)
        assertTrue(runCatching { transfer.restore(old.id) }.isFailure)
        val current = transfer.inspect(uri)
        transfer.discard(old.id)
        assertTrue(runCatching { transfer.restore(old.id) }.isFailure)
        assertEquals(initial, db.backupDao().createSnapshot())
        transfer.restore(current.id)
        assertEquals(backup, db.backupDao().createSnapshot())
    }

    @Test fun invalidInspectionInvalidatesEarlierReviewWithoutChangingDatabase(): Unit = runBlocking {
        val (valid, _) = files.create("valid", BackupJsonCodec.encode(backup, 123))
        val review = transfer.inspect(valid)
        val (invalid, _) = files.create("invalid", "not a backup".toByteArray())
        assertTrue(runCatching { transfer.inspect(invalid) }.isFailure)
        assertTrue(runCatching { transfer.restore(review.id) }.isFailure)
        assertEquals(initial, db.backupDao().createSnapshot())
    }

    @Test fun outputUsesRealProviderAndRoundTripsCurrentLocalSnapshot(): Unit = runBlocking {
        val (uri, _) = files.create("output", ByteArray(0))
        transfer.write(uri)
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
        assertEquals(initial, BackupJsonCodec.decode(bytes))
        assertEquals(initial, db.backupDao().createSnapshot())
    }
}

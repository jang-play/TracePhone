package com.dksystems.tracephone.data.backup

import android.content.Context
import android.net.Uri
import com.dksystems.tracephone.core.AppLog
import com.dksystems.tracephone.data.local.DailyAppUsageDao
import com.dksystems.tracephone.data.local.DailyAppUsageEntity
import com.dksystems.tracephone.di.ApplicationScope
import com.dksystems.tracephone.di.IoDispatcher
import com.dksystems.tracephone.domain.model.UsageBackupFailure
import com.dksystems.tracephone.domain.model.UsageBackupState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 한 번에 넣을 행 수.
 *
 * 전부를 한 트랜잭션에 넣으면 18,000행이 하나의 큰 쓰기가 되고, 중간에 실패하면 되돌아가는
 * 양도 그만큼 커진다. 나누면 각 덩어리가 독립적인 업서트가 된다 — 같은 행을 다시 써도
 * 결과가 같으므로(기본키 + REPLACE) 다시 시도해도 안전하다.
 */
private const val IMPORT_CHUNK_ROWS = 500

/**
 * 누적 기록을 파일로 꺼내고 되돌린다.
 *
 * ### 왜 DB 파일이 아니라 CSV인가
 * DB 파일을 그대로 주고받는 쪽이 코드는 짧다. 그런데 **되돌릴 때** Room이 열고 있는 파일을
 * 바꿔치기해야 하고, 그것은 이 기능이 지키려는 바로 그 데이터를 위험에 빠뜨린다.
 * 안전하게 하려면 DB를 닫고 프로세스를 재시작해야 한다.
 *
 * CSV로 가면 되돌리기가 **동기화가 이미 쓰는 [DailyAppUsageDao.upsertAll]** 을 지난다.
 * 복합 기본키에 `REPLACE`라 멱등이고 트랜잭션도 이미 걸려 있다. 살아 있는 DB에 새로운
 * 위험이 생기지 않는다.
 *
 * ### 되돌리기는 지우지 않는다
 * 업서트만 한다. 옛 백업을 새 DB에 넣으면 **합쳐지고**, 파일에 있는 (날짜·uid·회선) 행만
 * 덮는다. 통째로 날리는 일이 구조적으로 불가능하다. '파일 그대로 교체'를 원하면 삭제가
 * 필요하지만, 그 기능은 실수 한 번이 복구 불가능한 손실이 되므로 두지 않았다.
 *
 * ### 설정은 담지 않는다
 * 한도·테마·정렬은 DataStore에 있고 다시 입력하면 된다. 되돌릴 수 없는 것은 누적 기록뿐이다.
 */
@Singleton
class UsageBackupStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dailyAppUsageDao: DailyAppUsageDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    private val mutableState = MutableStateFlow<UsageBackupState>(UsageBackupState.Idle)
    val state: StateFlow<UsageBackupState> = mutableState.asStateFlow()

    /** 지금 파일을 읽거나 쓰고 있는가. 버튼을 잠그는 데 쓴다. */
    private val isBusy: Boolean
        get() = mutableState.value.let {
            it is UsageBackupState.Exporting || it is UsageBackupState.Importing
        }

    /** 사용자가 고른 자리에 모든 행을 쓴다. 이미 돌고 있으면 아무 일도 하지 않는다. */
    fun exportTo(target: Uri) {
        if (isBusy) return
        mutableState.value = UsageBackupState.Exporting

        applicationScope.launch {
            mutableState.value = runCatchingBackup { UsageBackupState.Exported(writeTo(target)) }
        }
    }

    /** 사용자가 고른 파일을 읽어 넣는다. 이미 돌고 있으면 아무 일도 하지 않는다. */
    fun importFrom(source: Uri) {
        if (isBusy) return
        mutableState.value = UsageBackupState.Importing

        applicationScope.launch {
            mutableState.value = runCatchingBackup { UsageBackupState.Imported(readFrom(source)) }
        }
    }

    private suspend fun writeTo(target: Uri): Int = withContext(ioDispatcher) {
        val entities = dailyAppUsageDao.readAllForBackup()

        val stream = context.contentResolver.openOutputStream(target)
            ?: throw IOException("내보낼 자리를 열지 못했다: $target")

        stream.bufferedWriter().use { writer ->
            writer.appendLine(USAGE_CSV_HEADER)
            entities.forEach { writer.appendLine(usageCsvLineOf(it)) }
        }

        AppLog.info("Usage backup exported: rows=${entities.size}")
        entities.size
    }

    /**
     * **전부 해석한 뒤에 쓴다.**
     *
     * 읽으면서 바로 넣으면, 파일 중간에 망가진 줄이 있을 때 앞부분만 들어간 상태로 멈춘다.
     * 무엇이 들어갔고 무엇이 안 들어갔는지 알 수 없게 되므로, 한 줄이라도 어긋나면
     * 아무것도 쓰지 않는다.
     */
    private suspend fun readFrom(source: Uri): Int = withContext(ioDispatcher) {
        val stream = context.contentResolver.openInputStream(source)
            ?: throw IOException("고른 파일을 열지 못했다: $source")

        val entities: List<DailyAppUsageEntity> = stream.bufferedReader().use { reader ->
            parseUsageCsv(reader.lineSequence())
        }

        entities.chunked(IMPORT_CHUNK_ROWS).forEach { dailyAppUsageDao.upsertAll(it) }

        AppLog.info("Usage backup imported: rows=${entities.size}")
        entities.size
    }

    /**
     * 실패를 사용자가 할 수 있는 일로 번역한다.
     *
     * 이 스코프에는 예외 처리기가 걸려 있지만([com.dksystems.tracephone.core.coroutineFailureLogger])
     * 그쪽은 앱을 살리는 그물일 뿐이고 화면에는 아무것도 알리지 않는다. 여기서 받아야
     * 사용자가 무엇을 다시 해야 하는지 알 수 있다.
     */
    private inline fun runCatchingBackup(
        block: () -> UsageBackupState
    ): UsageBackupState = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (malformed: UsageCsvFormatException) {
        AppLog.error("Usage backup file rejected", malformed)
        UsageBackupState.Failed(UsageBackupFailure.MALFORMED)
    } catch (storage: IOException) {
        AppLog.error("Usage backup could not read or write the file", storage)
        UsageBackupState.Failed(UsageBackupFailure.STORAGE)
    } catch (unexpected: Exception) {
        AppLog.error("Usage backup failed unexpectedly", unexpected)
        UsageBackupState.Failed(UsageBackupFailure.UNKNOWN)
    }
}

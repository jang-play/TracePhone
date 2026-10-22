package com.dksystems.tracephone.data.backup

import com.dksystems.tracephone.data.local.DailyAppUsageEntity
import com.dksystems.tracephone.domain.model.NetworkKind
import java.time.LocalDate

/**
 * 첫 줄에 적는 열 이름. **가져올 때 이 줄이 정확히 같아야 한다.**
 *
 * 따로 형식 번호를 두지 않는다. 열 구성이 바뀌면 이 줄도 바뀌므로, 이 줄을 비교하는 것이
 * 곧 형식 검사다. 번호를 따로 두면 번호만 올리고 열을 바꾸는 실수가 가능해진다.
 */
internal const val USAGE_CSV_HEADER =
    "usageDate,uid,networkKind,packageName,receivedBytes,transmittedBytes," +
        "foregroundReceivedBytes,foregroundTransmittedBytes,recordedAtEpochMillis"

private const val FIELD_SEPARATOR = ","

private const val FIELD_COUNT = 9

/**
 * 비어 있는 칸은 **0이 아니라 '모름'** 이다.
 *
 * 포그라운드 구분을 기록하기 전에 쌓인 행이 여기에 해당한다. 0으로 되살리면 그 기록이
 * 전부 백그라운드였던 것처럼 보인다. 이 구분이 왕복에서 살아남는지를 테스트로 고정한다.
 */
private const val NULL_FIELD = ""

/**
 * 오류 메시지에 적을 머리글의 최대 길이.
 *
 * 사용자가 바이너리 파일을 고르면 첫 '줄'이 수십만 바이트가 될 수 있다. 그것을 그대로
 * 메시지에 넣으면 로그가 그 크기만큼 불어난다. 무엇을 골랐는지 알아보는 데는 앞부분만
 * 있으면 된다.
 */
private const val HEADER_EXCERPT_LENGTH = 80

/** 파일이 TracePhone 백업이 아니거나 망가졌다. */
class UsageCsvFormatException(message: String) : Exception(message)

/** 한 행을 한 줄로. 열 순서는 [USAGE_CSV_HEADER]와 반드시 같다. */
internal fun usageCsvLineOf(entity: DailyAppUsageEntity): String = listOf(
    entity.usageDate.toString(),
    entity.uid.toString(),
    entity.networkKind.name,
    entity.packageName ?: NULL_FIELD,
    entity.receivedBytes.toString(),
    entity.transmittedBytes.toString(),
    entity.foregroundReceivedBytes?.toString() ?: NULL_FIELD,
    entity.foregroundTransmittedBytes?.toString() ?: NULL_FIELD,
    entity.recordedAtEpochMillis.toString()
).joinToString(FIELD_SEPARATOR)

/**
 * 파일 전체를 행 목록으로 바꾼다.
 *
 * **한 줄이라도 어긋나면 전체를 거부한다.** 쓰기를 시작한 뒤에 멈추면 무엇이 들어갔고
 * 무엇이 안 들어갔는지 알 수 없다. 사용자가 고른 파일은 신뢰 경계이므로, 조용히 건너뛰며
 * 넣는 것보다 아무것도 넣지 않고 왜 거부했는지 알려주는 쪽이 낫다.
 *
 * 값을 따옴표로 감싸지 않는다. 패키지명에는 쉼표가 들어갈 수 없고 나머지는 숫자·날짜·열거형
 * 이름이다. 그래도 **칸 수를 세어** 쉼표가 섞인 줄을 걸러낸다.
 *
 * @throws UsageCsvFormatException 머리글이 다르거나 어느 줄이든 해석할 수 없을 때
 */
internal fun parseUsageCsv(lines: Sequence<String>): List<DailyAppUsageEntity> {
    val iterator = lines.iterator()

    val header = if (iterator.hasNext()) iterator.next().trim() else null
    if (header != USAGE_CSV_HEADER) {
        throw UsageCsvFormatException(
            "TracePhone 백업 파일이 아니다. 첫 줄이 열 이름과 다르다: " +
                (header?.take(HEADER_EXCERPT_LENGTH) ?: "(빈 파일)")
        )
    }

    val entities = mutableListOf<DailyAppUsageEntity>()
    var lineNumber = 1

    for (line in iterator) {
        lineNumber++
        // 마지막 줄바꿈 뒤의 빈 줄은 흔하다. 그것까지 오류로 볼 이유는 없다.
        if (line.isBlank()) continue

        entities += entityFrom(line, lineNumber)
    }

    if (entities.isEmpty()) {
        throw UsageCsvFormatException("머리글만 있고 기록이 없다")
    }
    return entities
}

private fun entityFrom(line: String, lineNumber: Int): DailyAppUsageEntity {
    // 코틀린의 split은 끝의 빈 칸을 버리지 않는다(자바의 split과 다르다). 그래서 칸 수를
    // 세는 것만으로 마지막 칸이 비어 있는 줄도 걸러진다. limit을 넘기지 않는다 — 음수는
    // 코틀린에서 IllegalArgumentException이고, 0이 곧 '제한 없음'이다.
    val fields = line.trim().split(FIELD_SEPARATOR)
    if (fields.size != FIELD_COUNT) {
        throw UsageCsvFormatException(
            "${lineNumber}번째 줄의 칸이 ${FIELD_COUNT}개가 아니다(${fields.size}개)"
        )
    }

    return DailyAppUsageEntity(
        usageDate = fields[0].requireDate(lineNumber),
        uid = fields[1].requireInt(lineNumber, "uid"),
        networkKind = fields[2].requireNetworkKind(lineNumber),
        packageName = fields[3].takeIf { it.isNotEmpty() },
        receivedBytes = fields[4].requireLong(lineNumber, "receivedBytes"),
        transmittedBytes = fields[5].requireLong(lineNumber, "transmittedBytes"),
        foregroundReceivedBytes = fields[6].optionalLong(lineNumber, "foregroundReceivedBytes"),
        foregroundTransmittedBytes =
            fields[7].optionalLong(lineNumber, "foregroundTransmittedBytes"),
        recordedAtEpochMillis = fields[8].requireLong(lineNumber, "recordedAtEpochMillis")
    )
}

private fun String.requireDate(lineNumber: Int): LocalDate =
    runCatching { LocalDate.parse(this) }.getOrElse {
        throw UsageCsvFormatException("${lineNumber}번째 줄의 날짜를 읽을 수 없다: '$this'")
    }

private fun String.requireNetworkKind(lineNumber: Int): NetworkKind =
    NetworkKind.entries.firstOrNull { it.name == this }
        ?: throw UsageCsvFormatException("${lineNumber}번째 줄의 회선 종류를 모른다: '$this'")

private fun String.requireInt(lineNumber: Int, field: String): Int =
    toIntOrNull() ?: throw UsageCsvFormatException(
        "${lineNumber}번째 줄의 $field 가 정수가 아니다: '$this'"
    )

private fun String.requireLong(lineNumber: Int, field: String): Long =
    toLongOrNull() ?: throw UsageCsvFormatException(
        "${lineNumber}번째 줄의 $field 가 정수가 아니다: '$this'"
    )

/** 빈 칸은 '모름'이다. 값이 있는데 숫자가 아니면 오류다 — 그 둘을 뭉개지 않는다. */
private fun String.optionalLong(lineNumber: Int, field: String): Long? =
    if (isEmpty()) null else requireLong(lineNumber, field)

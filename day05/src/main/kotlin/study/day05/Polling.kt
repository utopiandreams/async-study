package study.day05

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
private var start = System.currentTimeMillis()

fun log(message: String) {
    val elapsed = System.currentTimeMillis() - start
    println("${LocalTime.now().format(TIME)} (+${elapsed}ms) [${Thread.currentThread().name}] $message")
}

sealed interface WithdrawResult {
    data class Done(val txId: String) : WithdrawResult
    data object Unknown : WithdrawResult
}

// "처리 중"이면 1초, 2초, 4초 ... 간격으로 다시 조회. 끝은 바깥의 타임아웃이 정한다.
suspend fun pollUntilDone(txId: String, check: suspend (String) -> Status) {
    var interval = 1000L
    while (true) {
        log("${interval}ms 대기")
        try {
            delay(interval)
        } catch (e: CancellationException) {
            log("delay 도중 취소됨 (${e::class.simpleName})")
            throw e
        }
        val status = check(txId)
        log("조회 결과 = $status")
        if (status == Status.DONE) return
        interval *= 2
    }
}

// 출금 요청 → 결과 폴링 → 30초 넘으면 Unknown
suspend fun withdrawAndWait(
    bank: FakeBank,
    amount: Int,
    timeout: Duration = 30.seconds,
    check: suspend (String) -> Status = { bank.status(it) },
): WithdrawResult {
    val (txId, _) = bank.withdraw(amount)
    val finished = withTimeoutOrNull(timeout) {
        pollUntilDone(txId, check)
        true
    }
    return if (finished == null) WithdrawResult.Unknown else WithdrawResult.Done(txId)
}

// 실행: ./gradlew :day05:run --args="done"
//   done        : 은행이 5초 뒤 완료
//   unknown     : 영원히 PENDING + withTimeoutOrNull → Unknown
//   timeout     : 영원히 PENDING + withTimeout → 예외
//   blocking    : 조회가 60초 블로킹 (Dispatchers.IO로 옮겨도)
//   interrupt   : 같은 블로킹을 runInterruptible로 감쌈
fun main(args: Array<String>) = runBlocking {
    val mode = args.firstOrNull() ?: "done"
    log("===== $mode =====")
    start = System.currentTimeMillis()
    when (mode) {
        "done" -> {
            val result = withdrawAndWait(FakeBank(completeAfterMillis = 5000), 1000)
            log("최종 = $result")
        }

        "unknown" -> {
            val result = withdrawAndWait(FakeBank(completeAfterMillis = null), 1000)
            log("최종 = $result")
        }

        "timeout" -> {
            val bank = FakeBank(completeAfterMillis = null)
            val (txId, _) = bank.withdraw(1000)
            try {
                withTimeout(30.seconds) { pollUntilDone(txId) { bank.status(it) } }
            } catch (e: CancellationException) {
                log("withTimeout이 던짐: $e")
            }
        }

        "blocking" -> {
            val bank = FakeBank(completeAfterMillis = null, hangMillis = 60_000)
            val result = withdrawAndWait(bank, 1000) { txId ->
                withContext(Dispatchers.IO) { bank.statusBlocking(txId) }
            }
            log("최종 = $result")
        }

        "interrupt" -> {
            val bank = FakeBank(completeAfterMillis = null, hangMillis = 60_000)
            val result = withdrawAndWait(bank, 1000) { txId ->
                runInterruptible(Dispatchers.IO) { bank.statusBlocking(txId) }
            }
            log("최종 = $result")
        }
    }
}

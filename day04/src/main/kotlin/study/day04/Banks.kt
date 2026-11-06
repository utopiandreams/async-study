package study.day04

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
private var start = System.currentTimeMillis()

fun log(message: String) {
    val elapsed = System.currentTimeMillis() - start
    println("${LocalTime.now().format(TIME)} (+${elapsed}ms) [${Thread.currentThread().name}] $message")
}

// 은행 잔액 조회 흉내. 취소되면 delay에서 CancellationException이 튀어나온다.
suspend fun fetchBalance(bank: String, millis: Long, fail: Boolean = false): Int {
    log("$bank 조회 시작")
    try {
        delay(millis)
    } catch (e: CancellationException) {
        log("$bank 취소됨 (${e::class.simpleName})")
        throw e // 반드시 다시 던진다
    }
    if (fail) {
        log("$bank 예외 던짐!")
        throw RuntimeException("$bank 장애")
    }
    log("$bank 완료")
    return millis.toInt() / 10
}

// 실행: ./gradlew :day04:run --args="fail"
//   fail | timeout | cancel | sup-fail | sup-cancel | swallow
fun main(args: Array<String>) = runBlocking {
    val mode = args.firstOrNull() ?: "swallow"
    log("===== $mode =====")
    start = System.currentTimeMillis()
    when (mode) {
        "fail" -> try {
            coroutineScope {
                val a = async { fetchBalance("A", 1000) }
                val b = async { fetchBalance("B", 500, fail = true) }
                val c = async { fetchBalance("C", 3000) }
                log("합계 = ${a.await() + b.await() + c.await()}")
            }
        } catch (e: Exception) {
            log("coroutineScope 밖으로 나온 예외: $e")
        }

        "timeout" -> try {
            withTimeout(1500) {
                coroutineScope {
                    val a = async { fetchBalance("A", 1000) }
                    val b = async { fetchBalance("B", 500) }
                    val c = async { fetchBalance("C", 3000) }
                    log("합계 = ${a.await() + b.await() + c.await()}")
                }
            }
        } catch (e: Exception) {
            log("withTimeout 밖으로 나온 예외: $e")
        }

        "cancel" -> {
            val parent = launch {
                coroutineScope {
                    val a = async { fetchBalance("A", 1000) }
                    val b = async { fetchBalance("B", 500) }
                    val c = async { fetchBalance("C", 3000) }
                    log("합계 = ${a.await() + b.await() + c.await()}")
                }
            }
            delay(700)
            log("부모 취소!")
            parent.cancel()
            parent.join()
            log("부모 종료 확인")
        }

        "sup-fail" -> supervisorScope {
            val a = async { fetchBalance("A", 1000) }
            val b = async { fetchBalance("B", 500, fail = true) }
            val c = async { fetchBalance("C", 3000) }
            log("A = ${a.await()}")
            try {
                log("B = ${b.await()}")
            } catch (e: Exception) {
                log("b.await()에서 터짐: $e")
            }
            log("C = ${c.await()}")
        }

        "sup-cancel" -> {
            val parent = launch {
                supervisorScope {
                    val a = async { fetchBalance("A", 1000) }
                    val b = async { fetchBalance("B", 500) }
                    val c = async { fetchBalance("C", 3000) }
                    log("합계 = ${a.await() + b.await() + c.await()}")
                }
            }
            delay(700)
            log("부모 취소!")
            parent.cancel()
            parent.join()
            log("부모 종료 확인")
        }

        "swallow" -> {
            val parent = launch {
                coroutineScope {
                    async {
                        try {
                            delay(3000)
                        } catch (e: Exception) { // CancellationException까지 잡아서 삼킴
                            log("C: 에러 무시 (${e::class.simpleName})")
                        }
                        log("C: 계속 진행 → 출금 실행!")
                        delay(100)
                        log("C: 출금 확정")
                    }
                }
            }
            delay(500)
            log("부모 취소!")
            parent.cancel()
            parent.join()
            log("부모 종료 확인")
        }
    }
}

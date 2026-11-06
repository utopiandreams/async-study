package study.day03

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
private val START = System.currentTimeMillis()

// 시각 + 시작 후 경과 ms + 스레드 이름
fun log(message: String) {
    val elapsed = System.currentTimeMillis() - START
    println("${LocalTime.now().format(TIME)} (+${elapsed}ms) [${Thread.currentThread().name}] $message")
}

fun main() = runBlocking {
    log("① runBlocking")

    launch {
        log("② launch (디스패처 안 줌)")
    }

    val deferred = async(Dispatchers.Default) {
        log("③ async(Default)")
        withContext(Dispatchers.IO) {
            log("④ withContext(IO)")
        }
        log("⑤ IO 끝나고 돌아옴")
        42
    }
    log("await 결과 = ${deferred.await()}")
}

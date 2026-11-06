    package study.day03

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

// 실행: ./gradlew :day03:run -Pmain=study.day03.BlockingBugKt --args="default"
//   default : Default에서 Thread.sleep 8개 → hello가 밀린다 (버그)
//   io      : 같은 sleep을 IO로 옮김 → hello가 바로 찍힌다
//   io100   : IO에서 sleep 100개 → 몇 초 걸리나
//   delay / delay1000 : Default에서 delay 8개 / 1000개
fun main(args: Array<String>) = runBlocking {
    log("CPU 코어 수 = ${Runtime.getRuntime().availableProcessors()}")
    when (args.firstOrNull() ?: "delay1000") {
        "default" -> blockThenHello(Dispatchers.Default)
        "io" -> blockThenHello(Dispatchers.IO)
        "io100" -> io100()
        "delay" -> delayThenHello(8)
        "delay1000" -> delayThenHello(1000)
    }
}

suspend fun blockThenHello(blockOn: CoroutineDispatcher) = coroutineScope {
    val blockers = List(8) { i ->
        launch(blockOn) {
            log("sleep 시작 #$i")
            Thread.sleep(2000) // 외부 호출 흉내: 스레드를 붙잡고 잔다
            log("sleep 끝   #$i")
        }
    }
    Thread.sleep(100) // blocker들이 먼저 스레드를 잡도록 잠깐 기다림
    launch(Dispatchers.Default) {
        log("hello  ← Default의 아주 짧은 작업")
    }
    blockers.joinAll()
}

suspend fun io100() = coroutineScope {
    val start = System.currentTimeMillis()
    val threads = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    List(100) {
        launch(Dispatchers.IO.limitedParallelism(100)) {
            threads += Thread.currentThread().name
            Thread.sleep(1000)
        }
    }.joinAll()
    log("100개 끝: ${System.currentTimeMillis() - start}ms, 사용된 스레드 수 = ${threads.size}")
}

// delay 버전: 스레드를 붙잡지 않고 놓아준다
suspend fun delayThenHello(count: Int) = coroutineScope {
    val start = System.currentTimeMillis()
    val threads = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    val waiters = List(count) { i ->
        launch {
            threads += Thread.currentThread().name
            if (i < 3) log("delay 시작 #$i")
            delay(2000) // 메모(Continuation)를 남기고 스레드를 반납
            if (i < 3) log("delay 끝   #$i")
        }
    }
    delay(100)
    launch(Dispatchers.Default) {
        log("hello  ← Default의 아주 짧은 작업")
    }
    waiters.joinAll()
    log("$count 개 끝: ${System.currentTimeMillis() - start}ms, 사용된 스레드 수 = ${threads.size}")
}

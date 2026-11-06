package study.day02.handmade

import study.day02.log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// suspend fun pay(amount: Int): Int {
//     val balance = fetchBalance()
//     val paid = withdraw(amount)
//     return balance - paid
// }
// 를 suspend 없이 손으로 옮긴 버전

val SUSPENDED = Any() // "멈췄다"는 표시. 실제 이름은 COROUTINE_SUSPENDED

private val timer = Executors.newSingleThreadScheduledExecutor { task ->
    Thread(task, "timer").apply { isDaemon = true }
}

fun interface Continuation {
    fun resume(value: Any?)
}

// 함수 하나의 "이어서 할 일". 실제 이름은 BaseContinuationImpl
abstract class Frame(private val caller: Continuation) : Continuation {
    var label = 0

    abstract fun step(value: Any?): Any?

    override fun resume(value: Any?) {
        val outcome = step(value)
        if (outcome === SUSPENDED) return // 또 멈춤. 이 스레드는 여기서 떠남
        caller.resume(outcome) // 끝났으면 나를 부른 함수를 재개
    }
}

// 맨 아래 함수. 컴파일러가 아니라 사람이 짜는 부분
fun delay(millis: Long, continuation: Continuation): Any {
    timer.schedule({ continuation.resume(null) }, millis, TimeUnit.MILLISECONDS)
    log("      delay: 재개 예약, SUSPENDED 리턴")
    return SUSPENDED
}

// suspend fun fetchBalance(): Int { delay(1000); return 10_000 }
fun fetchBalance(caller: Continuation): Any? = FetchBalanceFrame(caller).step(null)

class FetchBalanceFrame(caller: Continuation) : Frame(caller) {
    override fun step(value: Any?): Any? {
        log("    fetchBalance 실행 label=$label")
        if (label == 0) {
            label = 1
            if (delay(1000, this) === SUSPENDED) return SUSPENDED
        }
        log("    fetchBalance: 10000 반환")
        return 10_000
    }
}

// suspend fun withdraw(amount: Int): Int { delay(1000); return amount }
fun withdraw(amount: Int, caller: Continuation): Any? = WithdrawFrame(amount, caller).step(null)

class WithdrawFrame(private val amount: Int, caller: Continuation) : Frame(caller) {
    override fun step(value: Any?): Any? {
        log("    withdraw 실행 label=$label")
        if (label == 0) {
            label = 1
            if (delay(1000, this) === SUSPENDED) return SUSPENDED
        }
        log("    withdraw: $amount 반환")
        return amount
    }
}

fun pay(amount: Int, caller: Continuation): Any? = PayFrame(amount, caller).step(null)

class PayFrame(private val amount: Int, caller: Continuation) : Frame(caller) {
    private var balance = 0

    override fun step(value: Any?): Any? {
        log("  pay 실행 label=$label")
        var result = value
        // when은 다음 분기로 넘어가지 않아서, 멈추지 않은 경우를 while로 이어간다
        while (true) {
            when (label) {
                0 -> {
                    label = 1
                    result = fetchBalance(this)
                    if (result === SUSPENDED) return SUSPENDED
                }
                1 -> {
                    balance = result as Int
                    log("  pay: balance=$balance 필드에 저장")
                    label = 2
                    result = withdraw(amount, this)
                    if (result === SUSPENDED) return SUSPENDED
                }
                2 -> {
                    val paid = result as Int
                    log("  pay: $balance - $paid 반환")
                    return balance - paid
                }
                else -> error("잘못된 label=$label")
            }
        }
    }
}

fun main() {
    val done = CountDownLatch(1)
    val finish = Continuation { result ->
        log("최종 결과=$result")
        done.countDown()
    }
    val outcome = pay(100, finish)
    log("main: pay가 리턴함, SUSPENDED인가? ${outcome === SUSPENDED}")
    done.await()
}

package study.day02

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend fun BankClient.fetchBalanceSuspend(): Int = suspendCoroutine { continuation ->
    log("fetchBalance 콜백 등록")
    fetchBalance { balance ->
        log("콜백 도착, resume 호출")
        continuation.resume(balance)
        log("두 번째 resume 호출")              // 추가
        try {
            continuation.resume(balance)        // 추가
        } catch (e: Throwable) {
            log("예외: $e")
        }
    }
}

suspend fun BankClient.withdrawSuspend(amount: Int): Int = suspendCoroutine { continuation ->
    log("withdraw 콜백 등록")
    withdraw(amount) { paid ->
        log("콜백 도착, resume 호출")
        continuation.resume(paid)
    }
}

suspend fun payWrapped(bank: BankClient, amount: Int): Int {
    log("pay 시작 amount=$amount")
    val balance = bank.fetchBalanceSuspend()
    log("balance=$balance")
    val paid = bank.withdrawSuspend(amount)
    log("paid=$paid")
    return balance - paid
}

fun main() = runBlocking(Dispatchers.Default) {
    log("결과=${payWrapped(BankClient(), 100)}")
}

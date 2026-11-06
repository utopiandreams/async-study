package study.day02

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

fun log(message: String) {
    println("${LocalTime.now().format(TIME)} [${Thread.currentThread().name}] $message")
}

fun logStack(message: String) {
    log(message)
    Thread.currentThread().stackTrace.drop(2).forEach { println("    at $it") }
}

suspend fun fetchBalance(): Int {
    delay(1000) // 잔액 조회를 흉내 냄
    return 10_000
}

suspend fun withdraw(amount: Int): Int {
    delay(1000) // 출금을 흉내 냄
    return amount
}

suspend fun pay(amount: Int): Int {
    logStack("pay 시작 amount=$amount")
    val balance = fetchBalance()
    logStack("balance=$balance")
    val paid = withdraw(amount)
    log("paid=$paid")
    return balance - paid
}

fun main() = runBlocking(Dispatchers.Default) {
    log("결과=${pay(100)}")
}

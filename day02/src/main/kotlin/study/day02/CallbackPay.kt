package study.day02

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BankClient {
    private val scheduler = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "bank-callback").apply { isDaemon = true }
    }

    fun fetchBalance(onResult: (Int) -> Unit) {
        scheduler.schedule({ onResult(10_000) }, 1, TimeUnit.SECONDS)
    }

    fun withdraw(amount: Int, onResult: (Int) -> Unit) {
        scheduler.schedule({ onResult(amount) }, 1, TimeUnit.SECONDS)
    }
}

fun pay(bank: BankClient, amount: Int, onResult: (Int) -> Unit) {
    log("pay 시작 amount=$amount")
    bank.fetchBalance { balance ->
        log("balance=$balance")
        bank.withdraw(amount) { paid ->
            log("paid=$paid")
            onResult(balance - paid)
        }
    }
    log("pay 리턴")
}

fun main() {
    val done = CountDownLatch(1)
    pay(BankClient(), 100) { result ->
        log("결과=$result")
        done.countDown()
    }
    done.await()
}

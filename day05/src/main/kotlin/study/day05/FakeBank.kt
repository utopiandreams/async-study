package study.day05

import kotlinx.coroutines.delay
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class Status { PENDING, DONE }

/**
 * 인메모리 가짜 은행.
 * completeAfterMillis: 출금 요청 후 몇 ms 뒤에 DONE이 되는지 (null이면 영원히 PENDING)
 * hangMillis: 조회 API가 먹통일 때 스레드를 붙잡고 자는 시간 (블로킹 조회에서만 사용)
 */
class FakeBank(
    private val completeAfterMillis: Long?,
    private val hangMillis: Long = 0,
) {
    private val completeAt = ConcurrentHashMap<String, Long>()

    suspend fun withdraw(amount: Int): Pair<String, Status> {
        delay(50) // 네트워크 흉내
        val txId = UUID.randomUUID().toString().take(8)
        completeAfterMillis?.let { completeAt[txId] = System.currentTimeMillis() + it }
        log("은행: 출금 $amount 접수 tx=$txId → PENDING")
        return txId to Status.PENDING
    }

    // 논블로킹 조회: delay로 기다림 → 취소에 반응
    suspend fun status(txId: String): Status {
        delay(50)
        return currentStatus(txId)
    }

    // 블로킹 조회: 은행이 먹통이라 스레드를 붙잡고 잠 → 취소를 무시
    fun statusBlocking(txId: String): Status {
        Thread.sleep(hangMillis)
        return currentStatus(txId)
    }

    private fun currentStatus(txId: String): Status {
        val at = completeAt[txId] ?: return Status.PENDING
        return if (System.currentTimeMillis() >= at) Status.DONE else Status.PENDING
    }
}

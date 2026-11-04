package study.day01

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.atomic.AtomicInteger

@SpringBootApplication
class Day01Application

fun main(args: Array<String>) {
    runApplication<Day01Application>(*args)
}

@RestController
class SlowController {
    private val log = LoggerFactory.getLogger(javaClass)
    private val inFlight = AtomicInteger()

    @GetMapping("/slow")
    fun slow(): String {
        log.info("start inFlight={}", inFlight.incrementAndGet())
        Thread.sleep(2000) // 외부 은행 호출을 흉내 낸 블로킹
        log.info("end   inFlight={}", inFlight.decrementAndGet())
        return "ok"
    }
}

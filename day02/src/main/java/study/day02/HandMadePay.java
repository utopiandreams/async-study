package study.day02;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HandMadePay {
    static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "timer");
        thread.setDaemon(true);
        return thread;
    });
    static final CountDownLatch DONE = new CountDownLatch(1);

    static void log(String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        System.out.println(time + " [" + Thread.currentThread().getName() + "] " + message);
    }

    // "끝나면 이걸 불러줘"
    interface Continuation {
        void resume(int value);
    }

    static void fetchBalance(Continuation next) {
        TIMER.schedule(() -> next.resume(10_000), 1, TimeUnit.SECONDS);
    }

    static void withdraw(int amount, Continuation next) {
        TIMER.schedule(() -> next.resume(amount), 1, TimeUnit.SECONDS);
    }

    // pay 함수의 "이어서 할 일"을 담은 객체
    static class PayContinuation implements Continuation {
        int label = 0;   // 어디까지 했는지
        int amount;      // 기억해 둘 변수
        int balance;

        PayContinuation(int amount) {
            this.amount = amount;
        }

        @Override
        public void resume(int value) {
            log("resume 호출, label=" + label);
            switch (label) {
                case 0:
                    label = 1;
                    fetchBalance(this);
                    return;
                case 1:
                    balance = value;
                    log("balance=" + balance);
                    label = 2;
                    withdraw(amount, this);
                    return;
                case 2:
                    log("결과=" + (balance - value));
                    DONE.countDown();
                    return;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        new PayContinuation(100).resume(0);
        log("resume이 리턴함. 이 스레드는 이제 자유");
        DONE.await();
    }
}

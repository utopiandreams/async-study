package study.day02;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * suspend fun pay(amount) { val balance = fetchBalance(); val paid = withdraw(amount); return balance - paid }
 * 를 컴파일러 없이 손으로 옮긴 버전.
 */
public class HandMadeSuspend {
    static final Object SUSPENDED = new Object(); // "멈췄다"는 표시. 실제 이름은 COROUTINE_SUSPENDED
    static final CountDownLatch DONE = new CountDownLatch(1);
    static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "timer");
        thread.setDaemon(true);
        return thread;
    });

    static void log(String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        System.out.println(time + " [" + Thread.currentThread().getName() + "] " + message);
    }

    interface Continuation {
        void resume(Object result);
    }

    // 함수 하나의 "이어서 할 일". 실제 이름은 BaseContinuationImpl
    abstract static class Frame implements Continuation {
        int label = 0;
        final Continuation caller; // 나를 부른 함수의 Continuation. 실제 이름은 completion

        Frame(Continuation caller) {
            this.caller = caller;
        }

        abstract Object step(Object result);

        @Override
        public void resume(Object result) {
            Object outcome = step(result);
            if (outcome == SUSPENDED) {
                return; // 또 멈춤. 이 스레드는 여기서 떠남
            }
            caller.resume(outcome); // 끝났으면 나를 부른 함수를 재개
        }
    }

    // 맨 아래 함수. 컴파일러가 아니라 사람이 짜는 부분
    static Object delay(long millis, Continuation continuation) {
        TIMER.schedule(() -> continuation.resume(null), millis, TimeUnit.MILLISECONDS);
        log("      delay: 1초 뒤 재개 예약, SUSPENDED 리턴");
        return SUSPENDED;
    }

    // suspend fun fetchBalance(): Int { delay(1000); return 10_000 }
    static Object fetchBalance(Continuation caller) {
        return new FetchBalanceFrame(caller).step(null);
    }

    static class FetchBalanceFrame extends Frame {
        FetchBalanceFrame(Continuation caller) {
            super(caller);
        }

        @Override
        Object step(Object result) {
            log("    fetchBalance 실행 label=" + label);
            switch (label) {
                case 0:
                    label = 1;
                    if (delay(1000, this) == SUSPENDED) {
                        log("    fetchBalance: SUSPENDED 받음, 그대로 리턴");
                        return SUSPENDED;
                    }
                case 1:
                    log("    fetchBalance: 10000 반환");
                    return 10_000;
                default:
                    throw new IllegalStateException();
            }
        }
    }

    // suspend fun withdraw(amount: Int): Int { delay(1000); return amount }
    static Object withdraw(int amount, Continuation caller) {
        return new WithdrawFrame(amount, caller).step(null);
    }

    static class WithdrawFrame extends Frame {
        int amount;

        WithdrawFrame(int amount, Continuation caller) {
            super(caller);
            this.amount = amount;
        }

        @Override
        Object step(Object result) {
            log("    withdraw 실행 label=" + label);
            switch (label) {
                case 0:
                    label = 1;
                    if (delay(1000, this) == SUSPENDED) {
                        log("    withdraw: SUSPENDED 받음, 그대로 리턴");
                        return SUSPENDED;
                    }
                case 1:
                    log("    withdraw: " + amount + " 반환");
                    return amount;
                default:
                    throw new IllegalStateException();
            }
        }
    }

    // suspend fun pay(amount: Int): Int
    static Object pay(int amount, Continuation caller) {
        return new PayFrame(amount, caller).step(null);
    }

    static class PayFrame extends Frame {
        int amount;
        int balance;

        PayFrame(int amount, Continuation caller) {
            super(caller);
            this.amount = amount;
        }

        @Override
        Object step(Object result) {
            log("  pay 실행 label=" + label);
            switch (label) {
                case 0:
                    label = 1;
                    result = fetchBalance(this);
                    if (result == SUSPENDED) {
                        log("  pay: SUSPENDED 받음, 그대로 리턴");
                        return SUSPENDED;
                    }
                case 1:
                    balance = (Integer) result;
                    log("  pay: balance=" + balance + " 필드에 저장");
                    label = 2;
                    result = withdraw(amount, this);
                    if (result == SUSPENDED) {
                        log("  pay: SUSPENDED 받음, 그대로 리턴");
                        return SUSPENDED;
                    }
                case 2:
                    int paid = (Integer) result;
                    log("  pay: " + balance + " - " + paid + " 반환");
                    return balance - paid;
                default:
                    throw new IllegalStateException();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Continuation finish = result -> {
            log("최종 결과=" + result);
            DONE.countDown();
        };
        Object outcome = pay(100, finish);
        log("main: pay가 리턴함, SUSPENDED인가? " + (outcome == SUSPENDED));
        DONE.await();
    }
}

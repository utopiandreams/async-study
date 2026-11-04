# day01: 블로킹 MVC와 톰캣 스레드

`/slow`가 2초 블로킹하는 Spring MVC 앱에 k6로 동시 50명 부하를 주고, 스레드 관점에서 관찰했다.

## 핵심 정리

- 응답시간은 "처리 시간 + 스레드를 기다린 시간"이다. 처리 시간은 2초로 고정이고, 스레드가 모자라면 대기 시간이 붙는다.
- 스레드는 `sleep` 중에 CPU를 거의 쓰지 않으면서 자리만 차지한다. CPU가 남아도 처리량은 스레드 수에 묶인다.
- 타임아웃은 클라이언트만 포기하는 것이다. 서버의 일은 줄지 않는다.

## 실행 방법

```bash
brew install k6
./gradlew :day01:bootRun      # 터미널 1
k6 run day01/k6/slow.js       # 터미널 2

# 부하 중 관찰 (터미널 3)
netstat -an -p tcp | grep '\.8081 '              # 소켓 상태와 Recv-Q
kill -3 $(lsof -ti tcp:8081 -sTCP:LISTEN)        # 앱 콘솔에 스레드 덤프 출력
```

## 실험 결과

조건: VU 50명, 30초, 응답을 받으면 쉬지 않고 재요청. 각 조건은 한 번씩만 측정했다.

| `threads.max` | k6 timeout | 평균 응답시간 | RPS | 실패 | 15초 시점 exec 스레드 |
|---|---|---|---|---|---|
| 10 | 없음 | 9.07초 | 4.93 | 0건 | 10개 |
| 50 | 없음 | 2.00초 | 24.86 | 0건 | 50개 |
| 200 | 없음 | 2.00초 | 24.90 | 0건 | 50개 |
| 10 | 5초 | 4.87초 | 9.40 | 93.75% (320건 중 300건) | 측정 안 함 |

### 내 예측과 비교

| 질문 | 내 예측 | 실제 |
|---|---|---|
| 스레드 10개의 평균 응답시간 | 처음 5초, 수정 후 약 10초 | 9.07초 (첫 묶음 이후는 약 10초로 추정) |
| 스레드 10개의 RPS | 초당 5개 | 4.93 |
| 나머지 40개 요청의 위치 | TCP 레벨? 로드밸런서? | 톰캣 작업 큐 + 커널 수신 버퍼 |
| 실패하는 요청 | 계속 기다릴 수도 있음 | 대기만 하고 실패는 0건 |
| 스레드 50개 / 200개 | 2초 / 더 낮아지지 않음 | 2.00초 / 2.00초 |
| 타임아웃 5초의 실패율 | 약 60% | 93.75% |
| 포기한 요청의 `sleep` | 실행되지만 아예 실행 못 하는 요청도 있음 | 실행됨, 못 하는 요청은 없는 것으로 보임 |

## 왜 이런 숫자가 나오나

### 스레드 10개: 평균 약 10초

- 첫 묶음은 2, 4, 6, 8, 10초가 10명씩 나온다. 이 평균은 6초다.
- 이후에는 응답을 받은 VU가 큐 맨 뒤에 다시 선다. 앞에 40명이 있으니 8초를 기다리고 2초 처리되어 약 10초가 걸린다.
- 리틀의 법칙 `동시 요청 수 = 처리량 × 응답시간`으로도 `50 = 5 × W`, W는 10초다.
- 최대 12.18초가 나온 이유는 확인하지 못했다. 묶음 경계에서 한 사이클 밀린 요청으로 추정한다.

### 스레드를 늘렸을 때

- RPS는 `min(스레드 수, 동시 사용자 수) ÷ 2초`다.
- 50개면 대기가 사라져 2초가 된다. 은행 호출이 빨라진 건 아니라서 그 아래로는 못 내려간다.
- `threads.max`는 상한이다. 200으로 올려도 동시 요청이 50개면 스레드는 50개까지만 늘어난다.

### 타임아웃 5초: 실패율 94%

- 포기한 요청은 큐에서 빠지지 않는다. 60%라는 예측은 빠진다고 가정한 계산이었다.
- 들어오는 속도는 초당 10건(VU 50명이 5초마다 재요청), 처리 속도는 초당 5건이라 큐가 계속 늘어난다.
- 성공한 20건은 첫 두 묶음(2초, 4초)뿐이다.

## 스레드 관점에서 본 내부

### 15초 시점 스냅샷 (스레드 10개)

```
http-nio-8081-exec-1 ~ 10   10개 전부 TIMED_WAITING (sleeping)
exec-1: cpu=30.89ms, elapsed=17.83s
소켓: ESTABLISHED 50개
  Recv-Q = 0   10개  ← 요청을 읽고 처리 중
  Recv-Q = 74  40개  ← 요청 바이트를 아직 아무도 안 읽음
```

### 타임아웃을 줬을 때 서버 상태

| 시각 | k6 | 서버 상태 |
|---|---|---|
| 15초 시점 | 실행 중 | `CLOSE_WAIT` 50개, `ESTABLISHED` 50개 |
| 04:33:04 | 종료 | `CLOSE_WAIT` 150개 |
| 04:33:27 | 없음 | exec 10개 모두 sleeping, 소켓 40개 남음 |
| 04:33:38 | 없음 | exec 10개 모두 대기 상태, 소켓 없음 |

- `CLOSE_WAIT`는 상대방이 연결을 끊었는데 내 쪽이 아직 닫지 않은 상태다.
- 정확히는 서버 전체가 모르는 건 아니다. 커널은 끊긴 걸 알고 있고, 내 컨트롤러 코드까지 그 사실이 전달되지 않는다.
- 포기한 요청의 바이트(`Recv-Q=74`)가 버퍼에 남아 있어서, 차례가 온 exec 스레드는 이를 정상 요청으로 읽고 `sleep`까지 실행한다.
- k6가 끝난 뒤에도 최소 23초 동안 서버는 아무도 기다리지 않는 일을 했다.
- 모든 요청이 실행됐는지 로그로 세지는 못했다. 초당 5건으로 약 321건이면 약 64초이고, 큐가 빈 시점(시작 후 58~69초)과 맞는다.

### 실무에서의 의미

- 재시도까지 붙으면 부하가 더 늘어나고, 부하가 끝나도 서버가 바로 회복되지 않는다.
- 타임아웃이 실패를 뜻하지 않는다. `/slow`가 실제 이체였다면 클라이언트는 실패로 봤는데 이체는 실행된 상황이 된다. 그래서 멱등성 키가 필요하다.
- 포기 신호가 일에 전달되지 않는 이 문제는 코루틴의 취소와 Reactor의 구독 취소로 이어진다.

## 톰캣이란

톰캣은 스레드가 아니라, 내 JVM 프로세스 안에서 돌아가는 Java 프로그램(라이브러리)이다. 스레드 여러 개를 직접 만들어 굴린다.

### 내가 잘못 알고 있던 것

| 이전 그림 | 실제 |
|---|---|
| 톰캣은 큰 OS 스레드 하나 | Java 코드 묶음이고, 스레드를 여러 개 쓴다 |
| 서블릿마다 스레드가 하나 | 요청마다 스레드가 하나 붙고, 서블릿 객체는 스레드들이 공유한다 |
| 요청마다 스레드를 만들고 소멸 | 미리 만든 스레드를 재사용한다 (스레드 풀) |

### 전체 구조

```
OS
└─ JVM 프로세스 1개
   ├─ Spring 객체들: SlowController 등
   ├─ 톰캣 객체들: DispatcherServlet 등
   └─ 스레드들 (아래는 톰캣 관련)
      ├─ http-nio-8081-Acceptor     새 연결을 받음
      ├─ http-nio-8081-Poller       데이터가 도착한 소켓을 감시
      └─ http-nio-8081-exec-1~10    요청을 실제로 처리
```

- Java 스레드는 OS 스레드와 1:1이다. OS 입장에서는 java 프로세스의 스레드들만 보인다.
- 내장 톰캣에서는 내 `main()`이 톰캣을 띄운다. 예전에는 톰캣을 먼저 띄우고 그 위에 war를 올렸다.
- TCP 연결 자체와 수신 버퍼는 톰캣이 아니라 OS 커널이 맡는다.

### 서블릿과 컨테이너

| 대상 | 역할 |
|---|---|
| Jakarta Servlet 표준 | 규칙을 정한다 (`jakarta.servlet.Servlet`, 과거 `javax.servlet`) |
| 톰캣 | 그 규칙대로 서블릿을 담아 실행하는 컨테이너 |
| `DispatcherServlet` | 톰캣에 담기는 서블릿, `Servlet` 인터페이스를 구현한 쪽 |
| `SlowController` | 서블릿이 아니라 Spring 컨테이너에 담기는 빈 |

- 톰캣은 `Servlet` 인터페이스의 구현체가 아니라, 표준에서 "실행 환경" 쪽을 구현한 제품이다.
- 컨테이너라고 부르는 이유는 객체를 담아 생애주기(`init`, `service`, `destroy`)를 대신 관리하기 때문이다. 대상이 빈이면 Spring 컨테이너다.
- `SlowController` 객체 하나를 스레드들이 공유하므로, `inFlight`는 `AtomicInteger`여야 한다.

### 요청 처리 흐름

```
1. Acceptor  연결 수락           Acceptor.run()
2. Poller    "데이터 왔다" 감지   NioEndpoint.Poller.run()
3. 큐        작업 등록            ← 스레드가 모자라면 여기서 대기
4. exec-N    요청을 읽고 실행     SocketProcessor.doRun() 이후
```

Acceptor와 Poller는 exec 스레드가 꽉 차도 계속 돈다. 그래서 일꾼이 10개인데 연결은 50개 맺어졌다.

### 실제 호출 스택 (아래에서 위로)

```
Thread.sleep                              ← 2초 동안 여기 멈춰 있음
SlowController.slow                       내 코드
DispatcherServlet.doDispatch              Spring MVC
HttpServlet.service                       서블릿 표준
WsFilter.doFilter                         톰캣 웹소켓 필터
RequestContextFilter.doFilterInternal     Spring
FormContentFilter.doFilterInternal        Spring
CharacterEncodingFilter.doFilterInternal  Spring
StandardWrapperValve.invoke               체인 시작
CoyoteAdapter.service                     HTTP 처리부 → 서블릿 처리부로 연결
Http11Processor.service                   HTTP 파싱
NioEndpoint$SocketProcessor.doRun         큐에서 꺼낸 작업
ThreadPoolExecutor.runWorker              스레드 풀
Thread.run                                exec 스레드의 시작
```

스레드 하나가 이 전체를 혼자 내려간다. 맨 위에서 멈추면 스레드가 통째로 묶인다.

### 스레드 재사용과 ThreadLocal

약 190건을 처리했는데 로그의 스레드 이름은 10개뿐이었다. `ThreadPoolExecutor.runWorker`가 큐에서 작업을 꺼내 실행하는 반복을 돈다.

스레드를 재사용하므로 `ThreadLocal`을 지우지 않으면 값이 다음 요청으로 넘어간다. `MDC`나 `SecurityContextHolder`를 요청이 끝날 때 정리하는 이유다.

### 필터

- 필터도 Jakarta Servlet 표준이다 (`jakarta.servlet.Filter`). 내가 `Filter`를 구현하고, `FilterChain` 구현체는 톰캣의 `ApplicationFilterChain`이다.
- `StandardWrapperValve.invoke()`가 `ApplicationFilterFactory.createFilterChain()`으로 요청마다 체인을 조립한다.
- `ApplicationFilterChain.internalDoFilter()`가 남은 필터를 호출하고, 다 끝나면 `servlet.service()`를 호출한다.
- 반복문이 아니라 중첩 호출이다. 그래서 `chain.doFilter()` 앞뒤로 전후 처리를 할 수 있고, 호출하지 않으면 요청을 막을 수 있다.
- 처음부터 끝까지 같은 스레드다.

| | 필터 | 인터셉터 |
|---|---|---|
| 규격 | Jakarta Servlet 표준 | Spring MVC |
| 호출하는 쪽 | 톰캣 | `DispatcherServlet` |
| 위치 | 서블릿 바깥 | 서블릿 안, 컨트롤러 앞뒤 |

## 톰캣 설정값

| 설정 | 무엇의 상한인가 | 기본값 | 관리 주체 | 소스 위치 |
|---|---|---|---|---|
| `threads.max` | 동시에 처리하는 스레드 수 | 200 | 톰캣 | `AbstractEndpoint.createExecutor()` |
| `threads.max-queue-capacity` | 작업 큐 길이 | 2147483647 | 톰캣 | `AbstractEndpoint.createExecutor()` |
| `max-connections` | 톰캣이 받아들여 유지하는 연결 수 | 8192 | 톰캣 | `Acceptor.run()`의 `countUpOrAwaitConnection()` |
| `accept-count` | 톰캣이 아직 안 받아간 연결의 대기 줄 | 100 | OS 커널 | `NioEndpoint`의 `serverSock.bind(addr, getAcceptCount())` |

요청이 늘어나면 차는 순서:

1. 스레드가 먼저 찬다. 이후 요청은 작업 큐에서 기다린다.
2. 연결이 8192개에 닿으면 Acceptor가 새 연결을 받지 않는다.
3. 그다음부터 커널이 `accept-count`만큼 줄을 세운다. 이 줄까지 차면 새 연결 시도는 지연되거나 실패할 수 있다.

- 이번 실험은 연결이 50~150개 수준이라 1단계에만 머물렀다.
- Spring Boot의 `accept-count` 설명은 "모든 처리 스레드가 사용 중일 때의 대기 줄"이라고 되어 있지만, 실험에서 스레드가 꽉 찼을 때 요청이 기다린 곳은 작업 큐였다.
- `accept-count`는 OS 상한에 잘릴 수 있다. 이 Mac의 `kern.ipc.somaxconn`은 128이다.

## 근거 수준

- 스레드 덤프로 확인: `Acceptor.run`, `Poller.run`, `SocketProcessor.doRun`, `Http11Processor.service`, 필터 호출 경로
- 톰캣 10.1.55 소스로 확인: `ApplicationFilterChain`, `ApplicationFilterFactory`, `StandardWrapperValve`, 설정값이 쓰이는 위치
- 확인 못 함 (기억 기준): 스레드가 찼을 때 작업을 큐에 넣는 `TaskQueue.offer()`, Recv-Q를 비우는 `Http11InputBuffer.parseRequestLine()`

## 남은 질문

- 그냥 스레드를 아주 많이 두면 되지 않나? 코루틴과 WebFlux가 왜 필요한지로 이어진다.
- `exec-1`이 `sleep` 중일 때 11번째 요청이 오면, 어느 스레드들이 일을 하고 어디서 멈출까?

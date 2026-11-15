# Practical 9 – Publish/Subscribe using Web Service, ESB and JMS

**Aim:** implement the publish/subscribe paradigm using a web service, an ESB and JMS.
Maven project. Needs **internet** (Maven downloads ActiveMQ the first time) and any JDK 8+.

## Architecture (same design as the manual, simplified parts)
```
Publisher ──HTTP POST──> Web service + ESB (EsbService, port 8080) ──JMS──> ActiveMQ broker (61616)
  (keyboard)              content-based routing:                              topic NEWS ──> Subscriber NEWS (×N)
                          "URGENT" -> ALERTS, else -> NEWS                    topic ALERTS ─> Subscriber ALERTS
```
| Component | File | Manual used | We use (why) |
|---|---|---|---|
| JMS provider | `Broker.java` | ActiveMQ | ActiveMQ, embedded in a Java program (no separate install) |
| Subscribers | `Subscriber.java` | JMS MessageListener | same |
| ESB | `EsbService.route()` | Apache Camel route | plain Java doing the ESB's job: transform + content-based routing (Camel = big framework, too much to write in an exam) |
| Web service | `EsbService` HTTP endpoint | JAX-WS SOAP service | HTTP web service using the JDK's built-in HttpServer (JAX-WS was **removed from JDK 11+**) |
| Publisher | `Publisher.java` | web service client | HTTP client (`HttpURLConnection`) |

## Setup (WSL Ubuntu / Linux)
```
sudo apt install -y maven        # plus any JDK: openjdk-17-jdk or openjdk-8-jdk
mvn -v
```
Project layout (Maven requires it):
```
9/
├── pom.xml
└── src/main/java/   Broker.java  Subscriber.java  EsbService.java  Publisher.java
```

## Run (5 terminals, all inside folder 9/; order matters)
```
mvn -q compile                                                      # once (downloads ActiveMQ, ~1 min)
mvn -q exec:java -Dexec.mainClass=Broker                            # T1  broker first
mvn -q exec:java -Dexec.mainClass=Subscriber -Dexec.args="NEWS"     # T2  subscriber 1
mvn -q exec:java -Dexec.mainClass=Subscriber -Dexec.args="NEWS"     # T3  subscriber 2 (shows pub/sub: both get it)
mvn -q exec:java -Dexec.mainClass=Subscriber -Dexec.args="ALERTS"   # T4  alerts subscriber
mvn -q exec:java -Dexec.mainClass=EsbService                        # T5  web service + ESB
mvn -q exec:java -Dexec.mainClass=Publisher                         # T6  type messages
```
(EsbService and Publisher can share a terminal if you put EsbService in the background with `&`.)
Instead of the Publisher you can also call the web service directly: `curl -d "URGENT disk full" localhost:8080/publish`
(Windows 10+: `curl.exe -d "..." http://localhost:8080/publish`).

**Expected:** type `Exam starts at 8 am` → both NEWS subscribers print it, ALERTS prints nothing.
Type `URGENT server down` → only the ALERTS subscriber prints it. Publisher shows `Service: Published to NEWS/ALERTS`.

**If `mvn exec:java` is awkward**, run plain java with the Maven classpath:
```
mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp target/classes:$(cat cp.txt) Broker          # Windows: use ; instead of :
```
**If compile says "Source option 8 is no longer supported"** (very new JDK): in pom.xml change `1.8` → `11` (both lines).

## Concepts from zero

### Point-to-point vs publish/subscribe
Up to now (Practicals 1, 2, 8) a client talked **directly** to one known server.
- **Point-to-point (Queue):** a message is put in a queue and **exactly one** consumer takes it (like a work queue).
- **Publish/subscribe (Topic):** a publisher sends a message to a named channel, the **topic** ("NEWS").
  **Every** subscriber of that topic gets **its own copy**. The publisher doesn't know who or how many subscribers exist.
  Like a YouTube channel: upload once, all subscribers notified.

**Decoupling** (the main advantage):
- **Space:** publisher and subscribers don't know each other's addresses; they only know the topic name.
- **Time:** they don't have to run at the same time (with durable subscriptions, a subscriber gets messages sent while it was offline).
- **Synchronisation:** the publisher doesn't wait for subscribers to process; it just publishes and continues.

### The broker / JMS provider (ActiveMQ)
The middleman that **holds topics and queues** and delivers messages. Publishers and subscribers only connect to the
broker (tcp://localhost:61616), never to each other. ActiveMQ is one implementation; others: RabbitMQ, Kafka.

### JMS (Java Message Service)
The standard **Java API** for talking to a broker (like JDBC is the standard API for databases).
The objects, in the order you use them (same in Subscriber and EsbService):
```java
ConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:61616"); // which broker
Connection connection = factory.createConnection();                                  // connect
Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);         // context for sending/receiving
Topic topic = session.createTopic("NEWS");                                           // destination
MessageProducer producer = session.createProducer(topic);                            // to SEND
producer.send(session.createTextMessage("hello"));
MessageConsumer consumer = session.createConsumer(topic);                            // to RECEIVE
consumer.setMessageListener(listener);   connection.start();                         // async receive
```
Memory trick: **Factory → Connection → Session → Topic → Producer/Consumer → Message.**
- `false` in createSession = no transactions; `AUTO_ACKNOWLEDGE` = the message is confirmed as received automatically.
- **MessageListener.onMessage()** = called automatically each time a message arrives (asynchronous; like an event handler).
  The alternative is `consumer.receive()` which blocks and waits (synchronous).
- `connection.start()` = start delivering incoming messages. Forget it → subscriber receives nothing.
- Message types: TextMessage (we use), MapMessage, ObjectMessage, BytesMessage, StreamMessage.

### Web service
A service that other programs call **over HTTP** (language-independent: any language can send HTTP).
Two styles: **SOAP** (XML envelopes, interface described by **WSDL**; the manual's JAX-WS) and **REST** (plain HTTP
requests, e.g. POST /publish). Ours is a simple HTTP (REST-style) service: POST the message to `/publish`, get back
"Published to NEWS".
In `EsbService`: `HttpServer.create(new InetSocketAddress(8080), 0)` creates the server; `createContext("/publish", handler)`
says which code handles that URL; `handle()` reads the request body (the message) and writes the reply.

### ESB (Enterprise Service Bus)
Middleware that **connects different applications that speak different protocols/formats**, and in between can
**validate, transform and route** messages. Here it connects HTTP (publisher side) to JMS (subscriber side) and does:
- **Transformation:** adds a timestamp to the message.
- **Content-based routing:** looks at the content: contains "URGENT" → topic ALERTS, else → topic NEWS.
Real ESB products: Mule ESB, WSO2 ESB, Apache ServiceMix; Apache Camel is the routing engine used inside many of them.
The publisher never knows which topic its message ends up in; the ESB decides. That's the point of an ESB.

### One message traced
1. You type `URGENT server down` in Publisher → HTTP POST to localhost:8080/publish.
2. EsbService `handle()` reads it → `route()`: contains URGENT → topic **ALERTS**; adds timestamp.
3. Creates a producer for ALERTS and sends a TextMessage to the broker; replies "Published to ALERTS".
4. Broker delivers a copy to **every** subscriber of ALERTS → their `onMessage()` prints it.
   NEWS subscribers get nothing.

## Viva questions (manual has no FAQ list; these are the likely ones)
- *Pub/sub vs point-to-point?* Topic: every subscriber gets a copy; queue: exactly one consumer gets each message.
- *What is JMS? Is it a product?* No, an API/specification. ActiveMQ is a provider (implementation) of it.
- *Main JMS objects?* ConnectionFactory, Connection, Session, Destination (Topic/Queue), MessageProducer, MessageConsumer, Message.
- *Synchronous vs asynchronous receive?* `receive()` blocks; `MessageListener.onMessage()` is called on arrival.
- *Durable subscription?* The broker keeps messages for a subscriber while it's offline and delivers them when it reconnects.
  (Ours are non-durable: a subscriber started after a message was sent won't see it.)
- *What does the ESB do here?* Bridges HTTP → JMS, transforms (timestamp), content-based routing (URGENT → ALERTS).
- *SOAP vs REST?* SOAP: XML envelope, WSDL contract, heavier, strict. REST: plain HTTP verbs/URLs, usually JSON/text, lighter.
- *What is WSDL?* Web Services Description Language: XML file describing a SOAP service's operations, messages, address.
- *Why is a broker needed?* Decoupling: senders and receivers don't need to know each other or be online simultaneously;
  the broker stores and forwards.
- *Examples of message brokers?* ActiveMQ, RabbitMQ, Apache Kafka, IBM MQ.
- *Why did you not use JAX-WS / Camel?* JAX-WS was removed from the JDK in Java 11; Camel is a large framework.
  The same roles (web service, ESB routing) are implemented with the JDK's HttpServer and a routing method.

---

## Detailed walkthrough (for beginners, in execution order)

This is the biggest practical: **four different programs** and up to **six terminals** running at once. Unlike the earlier practicals, the sender and the receivers **never talk to each other directly**. Everything passes through middlemen. The side-by-side tables below follow one message as it hops from program to program.

### Part A: The basics you need first

#### 1. The problem: direct connections don't scale

In Practicals 1, 2, and 3, the client **knew exactly which server** to call (its address and port, or its name) and talked to it directly. That's fine for one client and one server. But imagine a college notice system:
- the exam office wants to send a notice to **every student who cares**
- it doesn't know who those students are, or how many
- new students join and leave all the time

If the exam office had to keep a list of every student's address and send to each one, it would be a mess. **Publish/subscribe** fixes that.

#### 2. Publish/subscribe: the YouTube model

- A **publisher** sends a message to a named channel called a **topic** (for example "NEWS").
- A **subscriber** says "I'm interested in topic NEWS."
- **Every** subscriber of that topic gets **its own copy** of every message sent to it.
- The publisher **doesn't know who the subscribers are**, or even how many there are. It only knows the topic name.

**Analogy:** a YouTube channel. The creator uploads once, and every subscriber is notified. The creator doesn't send the video to each person.

The opposite style is **point-to-point**, using a **queue**: each message goes to **exactly one** receiver, like a ticket counter where each customer is served by just one clerk.

| | Topic (pub/sub) | Queue (point-to-point) |
|---|---|---|
| Who gets a message | **Every** subscriber gets a copy | **Exactly one** receiver |
| Analogy | YouTube channel, radio broadcast | Ticket counter |

The big benefit is **decoupling** (the two sides are not tied together):
- **Space:** the publisher and subscribers don't know each other's addresses, only the topic name.
- **Synchronisation:** the publisher doesn't wait for subscribers to finish reading; it publishes and moves on.

#### 3. The broker: the post office in the middle

If publishers and subscribers never talk directly, someone in the middle must **hold the topics and deliver messages**. That's the **message broker**. Here it's **ActiveMQ**, listening at `tcp://localhost:61616`.

Every program connects **only to the broker**, never to each other.

**Analogy:** a post office. You drop a letter addressed to "NEWS," and the post office delivers a copy to everyone who has a NEWS subscription.

#### 4. JMS: the standard way Java talks to a broker

**JMS** (Java Message Service) is a **standard set of Java classes** for sending and receiving messages through a broker. It's not a product, just the rules. ActiveMQ is one product that follows those rules.

You always use the JMS objects **in the same order**:

```
ConnectionFactory → Connection → Session → Topic → Producer (to send) or Consumer (to receive) → Message
```

| JMS object | Meaning | Analogy |
|---|---|---|
| `ConnectionFactory` | Knows **which broker** to connect to | The post office's address |
| `Connection` | An open connection to the broker | Walking into the post office |
| `Session` | A working context for sending and receiving | Standing at a counter |
| `Topic` | The named channel ("NEWS" or "ALERTS") | The subscription name |
| `MessageProducer` | Used to **send** to a topic | The outgoing-mail slot |
| `MessageConsumer` | Used to **receive** from a topic | Your letterbox |
| `TextMessage` | A message containing text | The letter |

#### 5. Web service: a program you call over HTTP

A **web service** is a program that other programs call **over HTTP**, the same protocol your browser uses. Any language can send HTTP, so a web service is language-neutral (like CORBA, but much simpler).

An HTTP conversation is always **one request, then one response**:
- The **request** goes to a **URL**, such as `http://localhost:8080/publish` (machine `localhost`, port `8080`, path `/publish`).
- It has a **method**. **POST** means "here is some data for you," and the data travels in the request **body**.
- The **response** has a **status code** (`200` means OK) and its own body (here, `"Published to NEWS"`).

The manual uses a **SOAP** web service (XML messages, described by a WSDL file). This code uses a simpler plain-HTTP (REST-style) service, because the SOAP library was removed from newer Java versions.

#### 6. ESB: the smart mail-sorting room

An **ESB** (Enterprise Service Bus) sits between applications that "speak" differently and can **check, transform, and route** messages on the way through.

Here the ESB does three jobs:
1. **Bridging:** it receives messages over **HTTP** (from the publisher) and sends them out over **JMS** (to the broker). The publisher doesn't need to know JMS at all.
2. **Transformation:** it adds a timestamp to the message.
3. **Content-based routing:** it **reads the message** and decides where it goes:
   - contains "URGENT" → topic **ALERTS**
   - anything else → topic **NEWS**

**Analogy:** a mailroom clerk who stamps the date on every letter, reads it, and puts urgent ones in the red tray and everything else in the normal tray.

The publisher **never chooses the topic**. The ESB decides. Real ESB products include Mule, WSO2, and Apache Camel; here a single Java method, `route()`, does the job.

#### 7. Maven: the tool that fetches libraries

ActiveMQ and JMS are **not built into Java**; they're external **libraries** (ready-made code by other people). **Maven** is a build tool that reads [pom.xml](pom.xml), **downloads the libraries listed there** from the internet, and compiles your code with them. That's why the first `mvn compile` needs internet access.

Maven also insists on a fixed folder layout, which is why the code lives in `src/main/java/` instead of directly in the `9/` folder.

#### 8. The new Java pieces you'll see

| Code | Meaning |
|---|---|
| `args.length > 0 ? args[0] : "NEWS"` | `args` holds the extra words typed after the program name. This reads: "if a word was given, use it as the topic; otherwise use NEWS." |
| `new MessageListener() { public void onMessage(Message m) { ... } }` | An **anonymous class**: a one-off object written on the spot, with no class name. It says "here's the code to run **whenever a message arrives**." This is called a **callback**: you hand over code now, and the library calls it later. |
| `new HttpHandler() { public void handle(HttpExchange ex) { ... } }` | Same idea: "here's the code to run **whenever an HTTP request arrives** at `/publish`." |
| `(TextMessage) m` | A **cast**: "I know this message is a text message; treat it as one so I can call `getText()`." |
| `Thread.currentThread().join()` | "Wait for **myself** to finish." Since a thread can't finish while it's waiting for itself, this means **wait forever**, keeping the program alive until Ctrl+C. |
| `try { ... } catch (JMSException e) { ... }` | Try the risky code. If a JMS error happens, run the `catch` part instead of crashing. |
| `static synchronized String route(...)` | `synchronized` means **only one thread at a time** may run this method. A JMS `Session` must not be used by two threads at once, so this is a safety lock. |
| `byte[]` | An array of raw bytes. HTTP sends bytes, not Java text, so `reply.getBytes()` converts the text first. |
| `&&` | "and": both conditions must be true. |

---

### Part B: The execution, step by step

**Example:** you type two messages in the publisher: `Exam starts at 8 am`, then `URGENT server room on fire`.

#### Step 0: Compile with Maven

```
mvn -q compile
```

| What happens | Meaning |
|---|---|
| Maven reads `pom.xml` | Sees that ActiveMQ (`activemq-broker`) and a logging silencer (`slf4j-nop`) are needed |
| Downloads them (first time only) | Saves them in `~/.m2/` for next time |
| Compiles the 4 files in `src/main/java/` | Output goes to `target/classes/` |

Each program is then started with `mvn -q exec:java -Dexec.mainClass=<Name>`, which means "run the `main` of this class, with the downloaded libraries available."

**The start order matters:** broker first (everyone else connects to it), then subscribers (they must be listening **before** a message is published), then the ESB, and finally the publisher.

#### Step 1: Start the broker (terminal 1)

| Code in [Broker.java](src/main/java/Broker.java) | Meaning |
|---|---|
| `new BrokerService()` | Creates an ActiveMQ broker **inside this Java program** (no separate install needed) |
| `setPersistent(false)` | Keep messages in **memory** only, not on disk |
| `setUseJmx(false)` | Turn off JMX (a monitoring feature you don't need) |
| `addConnector("tcp://localhost:61616")` | Listen for programs connecting on port **61616** |
| `broker.start()` | Start it |
| Prints | `ActiveMQ broker running on tcp://localhost:61616` |
| `Thread.currentThread().join()` | ⏸ Wait forever and keep the broker alive |

The post office is open. It has no topics and no subscribers yet.

#### Step 2: Start the subscribers (terminals 2, 3, 4)

Three copies of the same program run, with different `args`:

| Terminal | Command ends with | `topicName` |
|---|---|---|
| T2 | `-Dexec.args="NEWS"` | NEWS |
| T3 | `-Dexec.args="NEWS"` | NEWS |
| T4 | `-Dexec.args="ALERTS"` | ALERTS |

Each one runs through [Subscriber.java](src/main/java/Subscriber.java) in order:

| Code | Meaning |
|---|---|
| `new ActiveMQConnectionFactory("tcp://localhost:61616")` | "The broker is at this address." |
| `factory.createConnection()` | Connects to the broker. |
| `connection.createSession(false, Session.AUTO_ACKNOWLEDGE)` | Opens a session. `false` means no transactions; `AUTO_ACKNOWLEDGE` means "tell the broker I got each message automatically." |
| `session.createTopic(topicName)` | Names the topic, NEWS or ALERTS. |
| `session.createConsumer(topic)` | Creates a letterbox for that topic, so **the broker now knows this subscriber exists**. |
| `consumer.setMessageListener(new MessageListener() { ... })` | Registers the callback: "when a message arrives, run `onMessage`." **It doesn't run yet.** |
| `connection.start()` | "Start delivering messages to me now." **Forget this line and the subscriber receives nothing.** |
| Prints | `Subscribed to topic NEWS, waiting...` (or ALERTS) |
| `Thread.currentThread().join()` | ⏸ Wait forever |

The broker now has:
```
topic NEWS   → subscribers: T2, T3
topic ALERTS → subscribers: T4
```

The main code of each subscriber is frozen in `join()`. When a message arrives, a **background thread inside the JMS library** calls `onMessage`, so the program can react even though `main` is waiting.

#### Step 3: Start the web service and ESB (terminal 5)

| Code in [EsbService.java](src/main/java/EsbService.java) | Meaning |
|---|---|
| Factory → `createConnection()` → `connection.start()` → `createSession(...)` | Connects to the broker, the same as a subscriber. This time it will **send**, not receive. |
| `session = ...` (a `static` variable) | Saved so that `route()` can use it later. |
| `HttpServer.create(new InetSocketAddress(8080), 0)` | Creates a small web server on port **8080**. (`0` means use the default request waiting-list size.) |
| `server.createContext("/publish", new HttpHandler() { ... })` | "When a request arrives at `/publish`, run this `handle` code." **It doesn't run yet.** |
| `server.start()` | The web server starts listening. |
| Prints | `Web service (ESB) on http://localhost:8080/publish` |

`main` ends here, but the **program keeps running**, because the web server has its own background thread waiting for requests. (This is the same trick as RMI in Practical 2.)

#### Step 4: Start the publisher (terminal 6)

| Code in [Publisher.java](src/main/java/Publisher.java) | Meaning |
|---|---|
| `keyboard = new BufferedReader(new InputStreamReader(System.in))` | Keyboard reader (the same as Practical 1) |
| Prints | `Type messages (include URGENT for alerts), 'exit' to quit:` |
| `while ((msg = keyboard.readLine()) != null && !msg.equals("exit"))` | ⏸ Waits for you to type. It loops as long as there's input **and** it isn't `exit`. |

Everything is now running:
```
T6 Publisher ──HTTP──▶ T5 EsbService ──JMS──▶ T1 Broker ──▶ T2, T3 (NEWS)
                                                       └──▶ T4 (ALERTS)
```

---

#### Step 5: You type `Exam starts at 8 am`

Follow the message from left to right across the programs.

##### 5a. The publisher sends an HTTP request

| Publisher code | Meaning |
|---|---|
| `msg = "Exam starts at 8 am"` | Not `exit`, so it enters the loop. |
| `new URL("http://localhost:8080/publish").openConnection()` | Prepares an HTTP connection to the web service. |
| `con.setRequestMethod("POST")` | "I'm sending data." |
| `con.setDoOutput(true)` | "This request has a body." |
| `out.println(msg)` then `out.close()` | Writes the message into the request body. |
| `con.getInputStream()` | **Sends the request** and ⏸ waits for the response. |

The publisher only knows **one URL**. It has no idea about topics, the broker, or subscribers.

##### 5b. The ESB receives it, decides the topic, and transforms it

| EsbService code | Result |
|---|---|
| The web server calls `handle(ex)` | |
| `in.readLine()` on the request body | `msg = "Exam starts at 8 am"` |
| Calls `route(msg)` | |
| `msg.toUpperCase().contains("URGENT")` | `"EXAM STARTS AT 8 AM"` doesn't contain URGENT, so this is **false** |
| `topicName = ... ? "ALERTS" : "NEWS"` | **`NEWS`** (content-based routing) |
| `transformed = "[" + new java.util.Date() + "] " + msg` | `"[Sun Nov 15 23:41:54 IST 2026] Exam starts at 8 am"` (transformation: timestamp added) |

`toUpperCase()` first makes `urgent`, `Urgent`, and `URGENT` all match.

##### 5c. The ESB publishes to the broker

| EsbService code | Meaning |
|---|---|
| `session.createProducer(session.createTopic("NEWS"))` | Gets an outgoing-mail slot for topic NEWS. |
| `producer.send(session.createTextMessage(transformed))` | Wraps the text in a `TextMessage` and **sends it to the broker**. |
| `producer.close()` | Done with this slot. |
| Prints | `ESB routed "Exam starts at 8 am" -> topic NEWS` |
| `return "Published to NEWS"` | Back in `handle`, this becomes the HTTP reply. |

##### 5d. The broker delivers copies

| Broker | T2 (NEWS) | T3 (NEWS) | T4 (ALERTS) |
|---|---|---|---|
| Receives a message on topic NEWS | | | |
| Looks up NEWS's subscribers: T2 and T3 | | | |
| Sends **a copy to each** | `onMessage(m)` runs | `onMessage(m)` runs | *(nothing; not subscribed to NEWS)* |
| | `((TextMessage) m).getText()` reads the text | same | |
| | Prints `[NEWS] received: [Sun Nov 15 23:41:54 IST 2026] Exam starts at 8 am` | prints the same line | |

**This is pub/sub in action:** one publish, and **both** NEWS subscribers get it. If ten NEWS subscribers were running, all ten would get it, and the publisher's code wouldn't change at all.

##### 5e. The HTTP reply goes back to the publisher

| EsbService (inside `handle`) | Publisher |
|---|---|
| `bytes = "Published to NEWS".getBytes()` | |
| `ex.sendResponseHeaders(200, bytes.length)` sends status **200 (OK)** and the reply length | |
| `ex.getResponseBody().write(bytes)` then `ex.close()` | |
| | `in.readLine()` gives `"Published to NEWS"` |
| | Prints **`Service: Published to NEWS`** |
| | `con.disconnect()`, then back to the top of the loop ⏸ waiting for the next line |

Steps 5d and 5e happen at nearly the same moment, so which terminal prints first can vary.

#### Step 6: You type `URGENT server room on fire`

Exactly the same path, but the routing decision is different:

| Program | What happens | Printed |
|---|---|---|
| Publisher | POSTs the text to `/publish` | |
| ESB | `"URGENT SERVER ROOM ON FIRE".contains("URGENT")` is **true**, so the topic is **ALERTS**; it adds a timestamp and sends | `ESB routed "URGENT server room on fire" -> topic ALERTS` |
| Broker | Topic ALERTS has one subscriber, T4, so it sends T4 a copy | |
| T4 (ALERTS) | `onMessage` runs | `[ALERTS] received: [Sun Nov 15 23:41:54 IST 2026] URGENT server room on fire` |
| T2, T3 (NEWS) | Nothing, because they're not subscribed to ALERTS | |
| Publisher | Gets the HTTP reply | `Service: Published to ALERTS` |

The publisher did **nothing different**. It just typed a different message, and the **ESB** sent it somewhere else. That's the point of an ESB: routing decisions live in the middle, not in the sender.

#### Step 7: You type `exit`

| Program | What happens |
|---|---|
| Publisher | `!msg.equals("exit")` is false, so the loop ends and the publisher program exits |
| Broker, subscribers, ESB | **Keep running**; stop each one with Ctrl+C |

**Non-durable subscriptions:** if you start a new NEWS subscriber *now*, it will **not** see the two old messages. These subscriptions only receive messages sent **while they are connected**. (A **durable** subscription would make the broker save messages for an offline subscriber.)

---

### Summary

#### The whole thing on one page

```
  T6 Publisher            T5 EsbService (web service + ESB)          T1 Broker (ActiveMQ)        Subscribers
  ────────────            ─────────────────────────────────          ────────────────────        ───────────
  type "Exam ... 8 am"
  HTTP POST /publish ───▶ handle(): read body
                          route(): no "URGENT" → NEWS
                                   add timestamp
                          producer.send ──────── JMS ──────────────▶ topic NEWS ──────────────▶ T2 onMessage → print
                                                                                  └───────────▶ T3 onMessage → print
  "Service: Published  ◀── HTTP 200 "Published to NEWS"                                         (T4 ALERTS: nothing)
   to NEWS"

  type "URGENT ..."    ───▶ contains "URGENT" → ALERTS ─── JMS ────▶ topic ALERTS ────────────▶ T4 onMessage → print
```

#### Mapping code to concepts

| Concept | Code |
|---|---|
| The broker (JMS provider) | `BrokerService` + `addConnector("tcp://localhost:61616")` in [Broker.java](src/main/java/Broker.java) |
| Connect to the broker | `ActiveMQConnectionFactory` → `createConnection()` → `createSession(...)` |
| The topic | `session.createTopic("NEWS")` / `"ALERTS"` |
| Subscribe | `createConsumer(topic)` + `setMessageListener(...)` + `connection.start()` in [Subscriber.java](src/main/java/Subscriber.java) |
| Receive (asynchronously) | `onMessage(Message m)`, called automatically for every message |
| Web service | `HttpServer` on 8080 + `createContext("/publish", ...)` in [EsbService.java](src/main/java/EsbService.java) |
| ESB: content-based routing | `contains("URGENT") ? "ALERTS" : "NEWS"` in `route()` |
| ESB: transformation | Adding `new java.util.Date()` to the text |
| Publish | `createProducer(topic)` + `producer.send(createTextMessage(...))` |
| Publisher (web service client) | `HttpURLConnection` with POST in [Publisher.java](src/main/java/Publisher.java) |
| Keep a program alive | `Thread.currentThread().join()` (broker, subscribers) or the HTTP server's thread (ESB) |

**Compared with the earlier practicals:** Practicals 1–3 were **request/reply** between one client and one known server. Practical 9 is **publish/subscribe**: the sender never knows the receivers, a broker delivers one message to many subscribers, and an ESB in the middle transforms and routes messages so the sender doesn't have to decide where they go.

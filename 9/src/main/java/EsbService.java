import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import javax.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;

// 2+3. Web service + ESB.
// Web service: HTTP endpoint POST http://localhost:8080/publish (body = the message).
// ESB: transforms the message and does CONTENT-BASED ROUTING:
//      contains "URGENT" -> topic ALERTS, otherwise -> topic NEWS.
public class EsbService {
    static Session session;

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        Connection connection = factory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/publish", new HttpHandler() {
            public void handle(HttpExchange ex) throws IOException {
                BufferedReader in = new BufferedReader(new InputStreamReader(ex.getRequestBody()));
                String msg = in.readLine();                              // message from publisher
                String reply;
                try {
                    reply = route(msg);
                } catch (JMSException e) {
                    reply = "ERROR " + e.getMessage();
                }
                byte[] bytes = reply.getBytes();
                ex.sendResponseHeaders(200, bytes.length);               // HTTP reply to publisher
                ex.getResponseBody().write(bytes);
                ex.close();
            }
        });
        server.start();
        System.out.println("Web service (ESB) on http://localhost:8080/publish");
    }

    // the ESB part: transform + route to the right JMS topic
    static synchronized String route(String msg) throws JMSException {
        String topicName = msg.toUpperCase().contains("URGENT") ? "ALERTS" : "NEWS";
        String transformed = "[" + new java.util.Date() + "] " + msg;   // transformation: add timestamp
        MessageProducer producer = session.createProducer(session.createTopic(topicName));
        producer.send(session.createTextMessage(transformed));
        producer.close();
        System.out.println("ESB routed \"" + msg + "\" -> topic " + topicName);
        return "Published to " + topicName;
    }
}

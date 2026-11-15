import javax.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;

// 4. Subscriber: listens on a topic (NEWS or ALERTS). Run several to see every one get a copy.
public class Subscriber {
    public static void main(String[] args) throws Exception {
        String topicName = args.length > 0 ? args[0] : "NEWS";

        ConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        Connection connection = factory.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(topicName);
        MessageConsumer consumer = session.createConsumer(topic);

        consumer.setMessageListener(new MessageListener() {     // called for every message
            public void onMessage(Message m) {
                try {
                    System.out.println("[" + topicName + "] received: " + ((TextMessage) m).getText());
                } catch (JMSException e) { e.printStackTrace(); }
            }
        });
        connection.start();                                      // start delivering messages
        System.out.println("Subscribed to topic " + topicName + ", waiting...");
        Thread.currentThread().join();
    }
}

import org.apache.activemq.broker.BrokerService;

// 1. JMS provider: an ActiveMQ broker that holds the topics. Start this FIRST.
public class Broker {
    public static void main(String[] args) throws Exception {
        BrokerService broker = new BrokerService();
        broker.setPersistent(false);                    // keep messages in memory only
        broker.setUseJmx(false);
        broker.addConnector("tcp://localhost:61616");   // clients connect here
        broker.start();
        System.out.println("ActiveMQ broker running on tcp://localhost:61616");
        Thread.currentThread().join();                  // keep running until Ctrl+C
    }
}

package at.ffesternberg.libwas;

import at.ffesternberg.libwas.entity.Order;
import at.ffesternberg.libwas.entity.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: huwa
 * Date: 06.01.14
 * Time: 14:32
 * To change this template use File | Settings | File Templates.
 */
public class WASClientRunner implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(WASClientRunner.class);
    private static final int UPDATE_INTERVAL_MS = 5000;
    private static final int SOCKET_TIMEOUT_MS = UPDATE_INTERVAL_MS * 2;
    private final InetSocketAddress address;
    private final WASClient wasClient;
    private final DocumentBuilder docBuilder;
    private final SimpleDateFormat wasDateFormat;
    private Socket sock = null;
    private Writer w = null;
    private BufferedReader r = null;

    public WASClientRunner(InetSocketAddress address, WASClient wasClient) {
        this.address = address;
        this.wasClient = wasClient;

        try {
            docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException pce) {
            throw new IllegalStateException("Error while initialisation of XML Parser", pce);
        }
        wasDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    }

    public void run() {
        //Error recovery loop
        while (!wasClient.isStop()) {
            try {
                // Operational loop
                while (!wasClient.isStop()) {
                    try {
                        initalizeSockets();

                        sendCommand();

                        String response = readResponse();

                        Collection<Order> orderCollection = parseResponse(response);
                        if (!wasClient.isStop())
                            Thread.sleep(UPDATE_INTERVAL_MS);
                    } catch (InterruptedException ex) {
                        // Maybe we are terminated by WasClient, check in loop header
                    }
                }
            } catch (IOException e) {
                log.error("IOException in WASClient!", e);
                wasClient.setState(WASStatus.ERROR);
            } finally {
                tearDownSockets();
            }
        }
        wasClient.setState(WASStatus.STOPPED);
    }

    private void tearDownSockets() {
        try {
            if (w != null)
                w.close();
            if (r != null)
                r.close();
            if (sock != null)
                sock.close();
        } catch (IOException e) {
            log.error("IOException during Socket tearDown - ignoreing", e);
        } finally {
            resetSocket();
        }

    }

    private Set<Order> parseResponse(String response) throws IOException {
        Document doc = loadDoc(response);

        Set<Order> orderSet = new HashSet<Order>();

        NodeList orders = doc.getElementsByTagName("order");

        // No orders
        if (orders.getLength() == 0) {
            return orderSet;
        }

        for (int i = 0; i < orders.getLength(); i++) {
            Node order = orders.item(i);
            orderSet.add(parseOrder(order));
        }

        return orderSet;
    }

    private Order parseOrder(Node node) {
        Order order = new Order();

        order.setKey(Long.parseLong(getTextNodeValue(node, "key").substring(2), 12));
        order.setOrigin(getTextNodeValue(node, "origin"));
        order.setReceived(parseWasDate(getTextNodeValue(node, "receive-tad")));
        order.setOperationId(getTextNodeValue(node, "operation-id"));
        order.setAlarmlevel(Integer.parseInt(getTextNodeValue(node, "level")));
        order.setName(getTextNodeValue(node, "name"));
        order.setCaller(getTextNodeValue(node, "caller"));
        order.setLocation(getTextNodeValue(node, "location"));
        order.setInfo(getTextNodeValue(node, "info"));
        order.setStatus(parseStatus(getTextNodeValue(node, "status")));
        order.setWatchout(parseWasDate(getTextNodeValue(node, "watch-out-tad")));
        order.setFinished(parseWasDate(getTextNodeValue(node, "finished-tad")));
        order.setFireDepartments(parseFireDepartments(node));

        return order;
    }

    private OrderStatus parseStatus(String status) {
        if (status.equalsIgnoreCase("AUSGERÜCKT")) {
            return OrderStatus.OUT;
        }
        if (status.equalsIgnoreCase("BEENDET")) {//TODO: Check if "BEENDET" is the right status!"
            return OrderStatus.END;
        }
        return OrderStatus.ALERT;
    }

    private List<String> parseFireDepartments(Node node) {
        Element element = (Element) node;
        List<String> fireDepartments = new ArrayList<String>();
        NodeList destinations = element.getElementsByTagName("destination");

        for (int i = 0; i < destinations.getLength(); i++) {
            Node destination = destinations.item(i);
            fireDepartments.add(destination.getTextContent() + " (" + destination.getAttributes().getNamedItem("id") + ")");
        }
        return fireDepartments;
    }

    private Date parseWasDate(String nodeValue) {
        try {
            return nodeValue == null ? null : wasDateFormat.parse(nodeValue);
        } catch (ParseException e) {
            throw new IllegalStateException("Can't parse " + nodeValue + " to WAS Date!", e);
        }
    }

    private String getTextNodeValue(Node node, String key) {
        if (node.getNodeType() == 1) {
            Element nodeElement = (Element) node;
            NodeList nodeElementLst = nodeElement.getElementsByTagName(key);
            if (nodeElementLst.getLength() != 1) {
                throw new IllegalStateException("Can't read TextNode -> found " + nodeElementLst.getLength() + " Elements in with key " + key);
            }
            return nodeElementLst.item(0).getTextContent();
        }else{
            throw new IllegalStateException("Can't read TextNode! Parent node type="+node.getNodeType());
        }
    }

    private Document loadDoc(String response) throws IOException {
        InputSource inSource = new InputSource(new StringReader(response));

        try {
            Document doc = docBuilder.parse(inSource);
            doc.normalize();
            return doc;
        } catch (SAXException e) {
            throw new IOException("Error while reading XML!", e);
        }
    }

    private String readResponse() throws IOException {
        String line;
        StringBuilder sb = new StringBuilder();
        // Read till termination mark is found
        while ((line = r.readLine()) != null
                && !line.endsWith("</pdu>")
                && !line.equals("<pdu/>")) {
            sb.append(line);
        }
        // Add Termination Mark or fail if EOF before XML Termination
        if (line != null) {
            if (line.endsWith("</pdu>")) {
                sb.append("</pdu>");
            }
            if (line.equals("<pdu/>")) {
                sb.append("<pdu/>");
            }
        } else {
            throw new IOException("EOF before XML termination!\nAlready read:\n" + sb.toString());
        }
        return sb.toString();
    }

    private void sendCommand() throws IOException {
        w.write("GET ALARMS\n");
        w.flush();
    }

    private void initalizeSockets() throws SocketException, IOException {
        if (sock == null || sock.isClosed()) {
            resetSocket();
            wasClient.setState(WASStatus.DISCONNECTED);
            sock = new Socket();
            sock.setSoTimeout(SOCKET_TIMEOUT_MS);

        }
        if (!sock.isConnected()) {
            sock.connect(address, SOCKET_TIMEOUT_MS);
        }
        if (sock.isConnected()) {
            if (w == null)
                w = new OutputStreamWriter(sock.getOutputStream());
            if (r == null)
                r = new BufferedReader(new InputStreamReader(sock.getInputStream(), "ISO-8859-15"));
        }
        wasClient.setState(WASStatus.CONNECTED);
    }

    private void resetSocket() {
        sock = null;
        w = null;
        r = null;
    }


}

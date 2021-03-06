package at.ffesternberg.libwas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import at.ffesternberg.libwas.entity.Order;
import at.ffesternberg.libwas.entity.OrderStatus;
import at.ffesternberg.libwas.entity.WASResponse;

public class WASClientRunner implements Runnable {
	private static final Logger log = LoggerFactory.getLogger(WASClientRunner.class);
	private static final int UPDATE_INTERVAL_MS = 5000;
	private static final int SOCKET_READ_TIMEOUT_MS = UPDATE_INTERVAL_MS * 2;
	private static final int SOCKET_CONNECT_TIMOUT_MS = 1000;
	private final InetSocketAddress address;
	private final WASClient wasClient;
	private final DocumentBuilder docBuilder;
	private final SimpleDateFormat wasDateFormat;
	private Socket sock = null;
	private Writer w = null;
	private BufferedReader r = null;
	private WASResponse response;
	private long loopCounter = 0;

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
		// Error recovery loop
		while (!wasClient.isStop()) {
			try {
				// Operational loop
				while (!wasClient.isStop()) {
					try {
						incrementLoopCounter();
						if (log.isTraceEnabled()) {
							log.trace("Loop <" + loopCounter + "> started");
						}
						initalizeSockets();

						sendCommand();

						readResponse();

						Set<Order> orderCollection = parseResponse();
						wasClient.fireOrderUpdate(orderCollection);

						if (log.isTraceEnabled()) {
							log.trace("Loop <" + loopCounter + "> ended ");
						}
						if (!wasClient.isStop())
							Thread.sleep(UPDATE_INTERVAL_MS);

					} catch (InterruptedException ex) {
						// Maybe we are terminated by WasClient, check in loop
						// header
					}
				}
			} catch (ConnectException conEx) {
				wasClient.setState(WASStatus.DISCONNECTED);
				log.error("Can't connect to WAS! - Retry in 30sec", conEx);
				try {
					Thread.sleep(30000);
				} catch (InterruptedException e) {
				}
			} catch (SocketTimeoutException sto) {
				log.debug("Socket timed out - reconnecting");
			} catch (IOException e) {
				log.error("IOException in WASClient!", e);
				wasClient.setState(WASStatus.ERROR);
			} finally {
				tearDownSockets();
			}
		}
		wasClient.setState(WASStatus.STOPPED);
	}

	private void incrementLoopCounter() {
		loopCounter++;
		// Handle overflow!
		if (loopCounter < 0)
			loopCounter = 0;
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

	/**
	 * Parses the RAW XML Data from the WASResponse Object
	 * 
	 * @return a set of parsed orders
	 * @throws IOException
	 */
	private Set<Order> parseResponse() throws IOException {
		Document doc = loadDoc(response.getResponse());

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

		// order.setKey(Long.parseLong(getTextNodeValue(node,
		// "key").substring(2), 12)); //TODO: Fix numberformat exception!
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
		if (status.equalsIgnoreCase("BEENDET")) {// TODO: Check if "BEENDET" is
													// the right status!"
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
			fireDepartments
					.add(destination.getTextContent() + " (" + destination.getAttributes().getNamedItem("id") + ")");
		}
		return fireDepartments;
	}

	private Date parseWasDate(String nodeValue) {
		try {
			return nodeValue == null || nodeValue.isEmpty() ? null : wasDateFormat.parse(nodeValue);
		} catch (ParseException e) {
			throw new IllegalStateException("Can't parse " + nodeValue + " to WAS Date!", e);
		}
	}

	private String getTextNodeValue(Node node, String key) {
		if (node.getNodeType() == 1) {
			Element nodeElement = (Element) node;
			NodeList nodeElementLst = nodeElement.getElementsByTagName(key);
			if (nodeElementLst.getLength() != 1) {
				throw new IllegalStateException(
						"Can't read TextNode -> found " + nodeElementLst.getLength() + " Elements in with key " + key);
			}
			return nodeElementLst.item(0).getTextContent();
		} else {
			throw new IllegalStateException("Can't read TextNode! Parent node type=" + node.getNodeType());
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

	/**
	 * Reads the raw data from the reader and saves it in a new WASResponse
	 * object
	 * 
	 * @throws IOException
	 */
	private void readResponse() throws IOException {
		String line;
		StringBuilder sb = new StringBuilder();
		// Read till termination mark is found
		while ((line = r.readLine()) != null && !line.endsWith("</pdu>") && !line.equals("<pdu/>")) {
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
		response = new WASResponse(sb.toString());
	}

	private void sendCommand() throws IOException {
		w.write("GET ALARMS\n");
		w.flush();
	}

	private void initalizeSockets() throws SocketException, IOException {
		if (sock == null || sock.isClosed()) {
			resetSocket();
			// wasClient.setState(WASStatus.DISCONNECTED);
			sock = new Socket();
			sock.setSoTimeout(SOCKET_READ_TIMEOUT_MS);

		}
		try {
			if (!sock.isConnected()) {
				sock.connect(address, SOCKET_CONNECT_TIMOUT_MS);
			}
		} catch (SocketTimeoutException sto) {
			throw new ConnectException("Timeout during initial connect");
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

	public WASResponse getResponse() {
		return response;
	}

	public void setResponse(WASResponse response) {
		this.response = response;
	}
}

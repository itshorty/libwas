/**
 *
 */
package at.ffesternberg.libwas;

import at.ffesternberg.libwas.entity.Order;
import at.ffesternberg.libwas.entity.WASResponse;
import at.ffesternberg.libwas.exception.IllegalWasClientState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

/**
 * WASClient
 *
 * @author Florian Huber (admin@ff-esternberg.at)
 */
public class WASClient {
	private static Logger log = LoggerFactory.getLogger(WASClient.class);

	private Set<WeakReference<WASOrderListener>> orderListeners = new HashSet<WeakReference<WASOrderListener>>();
	private Set<WeakReference<WASStatusListener>> statusListeners = new HashSet<WeakReference<WASStatusListener>>();
	private WASStatus state = WASStatus.STOPPED;

	private WASClientRunner clientRunner = null;
	private Thread clientThread = null;
	private InetSocketAddress address;
	private boolean stop;

	public WASClient() {
		this("192.168.130.100", 47000);
	}

	public WASClient(String host, int port) {
		this(new InetSocketAddress(host, port));
	}

	public WASClient(InetSocketAddress address) {
		if (address == null) {
			throw new IllegalArgumentException("WAS Socket address was null!");
		}
		this.address = address;
		if (log.isDebugEnabled()) {
			log.debug("New WASClient for endpoint: " + address.toString());
		}
	}

	public void start() throws IllegalWasClientState {
		if (state == WASStatus.STOPPED) {
			stop = false;
			setState(WASStatus.DISCONNECTED);
			clientRunner = new WASClientRunner(address, this);
			clientThread = new Thread(clientRunner);
			clientThread.setName("wasClientThread");
			clientThread.setDaemon(true);
			clientThread.start();
		} else {
			throw new IllegalWasClientState("start", this.state);
		}
	}

	public void stop() {
		if (state != WASStatus.STOPPED && clientThread != null && clientThread.isAlive()) {
			stop = true;
			clientThread.interrupt();
		}
	}

	public WASStatus getState() {
		return state;
	}

	void setState(WASStatus state) {
		if (this.state != state) {
			WASStatus oldState = this.state;
			this.state = state;
			if (log.isDebugEnabled())
				log.debug("New state: " + state);
			fireStateUpdate(oldState, state);
		}
	}

	public WASResponse getLastRAWData() {
		if (clientRunner != null && state == WASStatus.CONNECTED) {
			return clientRunner.getResponse();
		}
		throw new IllegalStateException();
	}

	boolean isStop() {
		return stop;
	}

	void fireStateUpdate(WASStatus oldState, WASStatus newState) {
		HashSet<WeakReference<WASStatusListener>> toRemove = new HashSet<WeakReference<WASStatusListener>>();
		for (WeakReference<WASStatusListener> reference : statusListeners) {
			WASStatusListener listener = reference.get();
			if (listener == null)
				toRemove.add(reference);
			else
				listener.statusUpdated(oldState, newState);
		}
		statusListeners.removeAll(toRemove);
	}

	void fireOrderUpdate(Set<Order> orders) {
		HashSet<WeakReference<WASOrderListener>> toRemove = new HashSet<WeakReference<WASOrderListener>>();
		for (WeakReference<WASOrderListener> reference : orderListeners) {
			WASOrderListener listener = reference.get();
			if (listener == null)
				toRemove.add(reference);
			else
				listener.updateOrders(orders);
		}
		orderListeners.removeAll(toRemove);
	}

	public void addWasStatusListener(WASStatusListener listener) {
		statusListeners.add(new WeakReference<WASStatusListener>(listener));
	}

	public void addWasOrderListener(WASOrderListener listener) {
		orderListeners.add(new WeakReference<WASOrderListener>(listener));
	}
}

/**
 *
 */
package at.ffesternberg.libwas;

import at.ffesternberg.libwas.entity.Order;

import java.util.Set;

/**
 * WASOrderListener
 *
 * @author Florian Huber (admin@ff-esternberg.at)
 */
public interface WASOrderListener {
    /**
     * Called if the endpoint has a updated list of orders
     *
     * @param orders the new list of orders
     */
    public void updateOrders(Set<Order> orders);
}

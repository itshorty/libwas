/**
 *
 */
package at.ffesternberg.libwas;

import java.util.List;

import at.ffesternberg.libwas.entity.Order;


/**
 * WASOrderListener
 *
 * @author Florian Huber <admin@ff-esternberg.at>
 */
public interface WASOrderListener {
    /**
     * Called if the endpoint has a updated list of orders
     *
     * @param orders the new list of orders
     */
    public void updateOrders(List<Order> orders);
}

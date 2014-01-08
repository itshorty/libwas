/**
 *
 */
package at.ffesternberg.libwas;


/**
 * WASStatusListener
 *
 * @author Florian Huber <admin@ff-esternberg.at>
 */
public interface WASStatusListener {
    /**
     * Called if the WASClient changes it's status
     *
     * @param oldStatus the oldStatus
     * @param newStatus the newStatus
     */
    public void statusUpdated(WASStatus oldStatus, WASStatus newStatus);
}

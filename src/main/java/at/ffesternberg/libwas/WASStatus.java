package at.ffesternberg.libwas;

public enum WASStatus {
	/**
	 * Connected to the WAS Device
	 */
    CONNECTED, 
    /**
     * No network connection to the WAS Device
     */
    DISCONNECTED,
    /**
     * Client not running 
     */
    STOPPED,
    /**
     * General failure, connection could be still alive!
     */
    ERROR

}

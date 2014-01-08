package at.ffesternberg.libwas.exception;

import at.ffesternberg.libwas.WASStatus;

public class IllegalWasClientState extends Exception {

    public IllegalWasClientState(String string, WASStatus state) {
        super("Illegal WAS Client state during " + string + ": " + state);
    }

}

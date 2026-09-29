package net.packet;

/** Signals malformed packet data that cannot be safely decoded. */
public final class InvalidPacketException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidPacketException(String message) {
        super(message);
    }

    public InvalidPacketException(String message, Throwable cause) {
        super(message, cause);
    }
}

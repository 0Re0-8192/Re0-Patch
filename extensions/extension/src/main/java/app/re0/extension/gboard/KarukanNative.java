package app.re0.extension.gboard;

/**
 * JNI surface of libkarukan_jni.so, the karukan engine with its Mozc protocol bridge.
 * The native side is in karukan-im/android of the karukan repository.
 */
final class KarukanNative {
    private KarukanNative() {
    }

    /**
     * Points the engine at its files and starts loading the model in the background.
     */
    static native void init(String dataDir, String configDir);

    /**
     * The serialized Mozc Command that is about to be sent to Mozc.
     *
     * @return the reply when karukan owns the input, or null to let Mozc handle the command.
     */
    static native byte[] handle(byte[] command);

    /**
     * Reports what Mozc answered to the command.
     *
     * @return a reply amended by the bridge, or null to keep Mozc's reply.
     */
    static native byte[] observe(byte[] command, byte[] mozcReply);

    /**
     * Hybrid: after {@link #handle} answered the command, the command to give Mozc in the background
     * (the same key, or a revert when the karukan composition has ended).
     *
     * @return the command for Mozc, or null when there is nothing to send.
     */
    static native byte[] shadowCommand(byte[] command);

    /**
     * Hybrid: Mozc's answer to the command of {@link #shadowCommand}.
     *
     * @return the karukan reply with the candidates of Mozc added, or null to keep it.
     */
    static native byte[] shadowReply(byte[] mozcReply);

    /**
     * Hybrid: called before a command that karukan did not take goes to Mozc.
     *
     * @return a revert command to send to Mozc first, or null.
     */
    static native byte[] shadowRevert(byte[] command);

    /** Turns the Mozc candidates on or off. */
    static native void setHybrid(boolean on);
}

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
}

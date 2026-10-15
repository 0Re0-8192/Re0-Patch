package app.re0.extension.gboard;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * Makes digits typed in Japanese input half width right away.
 * <p>
 * Mozc, the Japanese engine of Gboard, converts typed characters according to character form rules.
 * Gboard sends a config without any rules, so Mozc uses its default rules, which turn digits
 * into full width while typing. The patch passes every command to the engine through
 * {@link #adjustCommand(byte[])}. The config command gets the rules appended, with the rule for
 * digits set to half width.
 * <p>
 * Appending works because a protobuf message that appears twice is merged by the parser.
 * Mozc uses its default rules only when the config has none, so all default rules are added here,
 * and only the rule for digits is different. Full width digits stay in the candidates.
 */
@SuppressWarnings("unused")
public final class JapaneseDigitsPatch {
    private static final String TAG = "GboardDigits";

    /**
     * Key of the switch added to the Gboard settings.
     */
    public static final String PREFERENCE_KEY = "re0_half_width_digits";

    // Mozc commands::Command.
    private static final int COMMAND_INPUT = 1;
    // Mozc commands::Input.
    private static final int INPUT_TYPE = 1;
    private static final int INPUT_CONFIG = 5;
    private static final int INPUT_TYPE_SET_CONFIG = 7;
    // Mozc config::Config.
    private static final int CONFIG_CHARACTER_FORM_RULES = 54;
    // Mozc config::Config::CharacterForm.
    private static final int HALF_WIDTH = 0;
    private static final int FULL_WIDTH = 1;
    private static final int LAST_FORM = 2;

    private static final int WIRE_VARINT = 0;
    private static final int WIRE_64_BIT = 1;
    private static final int WIRE_LENGTH_DELIMITED = 2;
    private static final int WIRE_32_BIT = 5;

    /**
     * The default rules of Mozc, from CreateDefaultConfig in config_handler.cc,
     * except that the rule of ".," is half width.
     * Each is the group, the form while typing, and the form of the conversion result.
     * The rule of the digits, group "0", is set separately.
     */
    private static final Object[][] DEFAULT_RULES = {
            {"ア", FULL_WIDTH, FULL_WIDTH},
            {"A", FULL_WIDTH, LAST_FORM},
            {"(){}[]", FULL_WIDTH, LAST_FORM},
            // Also half width, which makes "。" and "、" typed after a digit "." and ",".
            // Mozc turns them into the full width "．" and "，" in that case, and they are in this group.
            {".,", HALF_WIDTH, HALF_WIDTH},
            {"。、", FULL_WIDTH, FULL_WIDTH},
            {"・「」", FULL_WIDTH, FULL_WIDTH},
            {"\"'", FULL_WIDTH, LAST_FORM},
            {":;", FULL_WIDTH, LAST_FORM},
            {"#%&@$^_|`\\", FULL_WIDTH, LAST_FORM},
            {"~", FULL_WIDTH, LAST_FORM},
            {"<>=+-/*", FULL_WIDTH, LAST_FORM},
            {"?!", FULL_WIDTH, LAST_FORM},
    };

    private static Method currentApplicationMethod;

    private JapaneseDigitsPatch() {
    }

    /**
     * Injection point. Called with the serialized command before it is sent to the engine.
     *
     * @param command A serialized Mozc {@code commands::Command}.
     * @return The command to send instead.
     */
    public static byte[] adjustCommand(byte[] command) {
        try {
            if (command == null || !isSetConfigCommand(command) || !isEnabled()) {
                return command;
            }

            byte[] configRules = buildRules();
            // Command { input { config { rules } } }, merged with the input and config already in the command.
            byte[] config = field(INPUT_CONFIG, configRules);
            byte[] input = field(COMMAND_INPUT, config);

            byte[] adjusted = new byte[command.length + input.length];
            System.arraycopy(command, 0, adjusted, 0, command.length);
            System.arraycopy(input, 0, adjusted, command.length, input.length);

            Log.i(TAG, "Added character form rules to the config");
            return adjusted;
        } catch (Exception ex) {
            Log.e(TAG, "adjustCommand failure", ex);
            return command;
        }
    }

    private static boolean isEnabled() {
        Application application = currentApplication();
        if (application == null) {
            return true;
        }

        try {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(application);
            return preferences.getBoolean(PREFERENCE_KEY, true);
        } catch (Exception ex) {
            Log.w(TAG, "Could not read the setting, using the default");
            return true;
        }
    }

    /**
     * @return Whether the command is Command.input with type SET_CONFIG.
     */
    static boolean isSetConfigCommand(byte[] command) {
        int[] position = {0};
        while (position[0] < command.length) {
            long tag = readVarint(command, position);
            int fieldNumber = (int) (tag >>> 3);
            int wireType = (int) (tag & 7);

            if (fieldNumber == COMMAND_INPUT && wireType == WIRE_LENGTH_DELIMITED) {
                int length = (int) readVarint(command, position);
                return hasSetConfigType(command, position[0], position[0] + length);
            }
            skip(command, position, wireType);
        }
        return false;
    }

    private static boolean hasSetConfigType(byte[] message, int start, int end) {
        int[] position = {start};
        while (position[0] < end) {
            long tag = readVarint(message, position);
            int fieldNumber = (int) (tag >>> 3);
            int wireType = (int) (tag & 7);

            if (fieldNumber == INPUT_TYPE && wireType == WIRE_VARINT) {
                return readVarint(message, position) == INPUT_TYPE_SET_CONFIG;
            }
            skip(message, position, wireType);
        }
        return false;
    }

    private static byte[] buildRules() {
        ByteArrayOutputStream rules = new ByteArrayOutputStream();

        // Digits are half width while typing, and in the conversion result.
        writeRule(rules, "0", HALF_WIDTH, HALF_WIDTH);
        for (Object[] rule : DEFAULT_RULES) {
            writeRule(rules, (String) rule[0], (Integer) rule[1], (Integer) rule[2]);
        }
        return rules.toByteArray();
    }

    /**
     * Writes a Config.character_form_rules entry:
     * CharacterFormRule { group = 1; preedit_character_form = 2; conversion_character_form = 3 }.
     */
    private static void writeRule(ByteArrayOutputStream output, String group, int preeditForm, int conversionForm) {
        ByteArrayOutputStream rule = new ByteArrayOutputStream();
        writeBytes(rule, field(1, group.getBytes(StandardCharsets.UTF_8)));
        writeBytes(rule, varintField(2, preeditForm));
        writeBytes(rule, varintField(3, conversionForm));

        writeBytes(output, field(CONFIG_CHARACTER_FORM_RULES, rule.toByteArray()));
    }

    /**
     * @return A length delimited protobuf field.
     */
    static byte[] field(int fieldNumber, byte[] value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeVarint(output, ((long) fieldNumber << 3) | WIRE_LENGTH_DELIMITED);
        writeVarint(output, value.length);
        writeBytes(output, value);
        return output.toByteArray();
    }

    private static byte[] varintField(int fieldNumber, long value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeVarint(output, ((long) fieldNumber << 3) | WIRE_VARINT);
        writeVarint(output, value);
        return output.toByteArray();
    }

    private static void writeBytes(ByteArrayOutputStream output, byte[] bytes) {
        output.write(bytes, 0, bytes.length);
    }

    private static void writeVarint(ByteArrayOutputStream output, long value) {
        while ((value & ~0x7FL) != 0) {
            output.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        output.write((int) value);
    }

    private static long readVarint(byte[] data, int[] position) {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            if (position[0] >= data.length) {
                throw new IllegalArgumentException("Truncated varint");
            }
            byte b = data[position[0]++];
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new IllegalArgumentException("Varint is too long");
    }

    private static void skip(byte[] data, int[] position, int wireType) {
        switch (wireType) {
            case WIRE_VARINT:
                readVarint(data, position);
                break;
            case WIRE_64_BIT:
                position[0] += 8;
                break;
            case WIRE_LENGTH_DELIMITED:
                // Read the length first. Compound assignment would use the position from before reading it.
                int length = (int) readVarint(data, position);
                position[0] += length;
                break;
            case WIRE_32_BIT:
                position[0] += 4;
                break;
            default:
                throw new IllegalArgumentException("Unsupported wire type: " + wireType);
        }
    }

    private static Application currentApplication() {
        try {
            if (currentApplicationMethod == null) {
                currentApplicationMethod = Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication");
            }
            return (Application) currentApplicationMethod.invoke(null);
        } catch (Exception ex) {
            return null;
        }
    }
}

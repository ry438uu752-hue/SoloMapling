package constants.string;

import java.nio.charset.CharsetEncoder;
import java.util.regex.Pattern;

/** Validation helpers for names sent in legacy fixed-width MapleStory fields. */
public final class Big5NameUtils {
    public static final int CHARACTER_NAME_FIELD_BYTES = 13;
    public static final int CHARACTER_NAME_MAX_BYTES = CHARACTER_NAME_FIELD_BYTES - 1;

    private static final Pattern CHARACTER_NAME_PATTERN = Pattern.compile("[a-zA-Z0-9\\p{IsHan}]{3,12}");

    private Big5NameUtils() {
    }

    /**
     * Character names retain the existing 3-12 character rule while also fitting
     * in the 12 Big5 data bytes available in a 13-byte client name field.
     */
    public static boolean isValidCharacterName(String value) {
        return value != null
                && CHARACTER_NAME_PATTERN.matcher(value).matches()
                && fitsBig5Bytes(value, CHARACTER_NAME_MAX_BYTES);
    }

    /** Checks whether a value is representable in Big5 and fits before the field's NUL padding. */
    public static boolean fitsFixedField(String value, int fieldBytes) {
        if (value == null || value.isEmpty() || fieldBytes < 1
                || value.codePoints().anyMatch(java.lang.Character::isISOControl)) {
            return false;
        }

        int firstCodePoint = value.codePointAt(0);
        int lastCodePoint = value.codePointBefore(value.length());
        if (isWhitespace(firstCodePoint) || isWhitespace(lastCodePoint)) {
            return false;
        }

        return fitsBig5Bytes(value, fieldBytes - 1);
    }

    private static boolean isWhitespace(int codePoint) {
        return java.lang.Character.isWhitespace(codePoint) || java.lang.Character.isSpaceChar(codePoint);
    }

    private static boolean fitsBig5Bytes(String value, int maxBytes) {
        CharsetEncoder encoder = CharsetConstants.CHARSET.newEncoder();
        return encoder.canEncode(value)
                && value.getBytes(CharsetConstants.CHARSET).length <= maxBytes;
    }
}

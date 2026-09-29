package constants.string;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Big5NameUtilsTest {
    @Test
    void encodedLengthCountsBig5BytesRatherThanJavaCharacters() {
        assertEquals(5, CharsetConstants.encodedLength("hello"));
        assertEquals(4, CharsetConstants.encodedLength("\u6E2C\u8A66"));
    }

    @Test
    void acceptsTraditionalChineseWithinTheThirteenByteNameField() {
        assertTrue(Big5NameUtils.isValidCharacterName("王小明"));
        assertTrue(Big5NameUtils.isValidCharacterName("abc王小"));
    }

    @Test
    void rejectsNamesThatExceedTheBig5ByteLimitOrCannotBeEncoded() {
        assertFalse(Big5NameUtils.isValidCharacterName("王小明王小明王"));
        assertFalse(Big5NameUtils.isValidCharacterName("abc\uD83D\uDE00"));
    }

    @Test
    void fixedFieldsReserveOneByteForNulPadding() {
        assertTrue(Big5NameUtils.fitsFixedField("王小明王小明", 13));
        assertFalse(Big5NameUtils.fitsFixedField("王小明王小明王", 13));
    }

    @Test
    void fixedFieldsRejectEmptyOrWhitespacePaddedValues() {
        assertFalse(Big5NameUtils.fitsFixedField("", 13));
        assertFalse(Big5NameUtils.fitsFixedField("   ", 13));
        assertFalse(Big5NameUtils.fitsFixedField("\u3000寵物", 13));
        assertFalse(Big5NameUtils.fitsFixedField("寵物\u3000", 13));
        assertTrue(Big5NameUtils.fitsFixedField("小 白", 13));
    }
}

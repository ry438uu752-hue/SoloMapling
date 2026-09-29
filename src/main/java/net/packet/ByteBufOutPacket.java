package net.packet;

import constants.string.CharsetConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.jcip.annotations.NotThreadSafe;
import net.opcodes.SendOpcode;

import java.awt.*;
import java.nio.charset.CharsetEncoder;

@NotThreadSafe
public class ByteBufOutPacket implements OutPacket {
    private final ByteBuf byteBuf;

    public ByteBufOutPacket() {
        this.byteBuf = Unpooled.buffer();
    }

    public ByteBufOutPacket(SendOpcode op) {
        ByteBuf byteBuf = Unpooled.buffer();
        byteBuf.writeShortLE((short) op.getValue());
        this.byteBuf = byteBuf;
    }

    public ByteBufOutPacket(SendOpcode op, int initialCapacity) {
        ByteBuf byteBuf = Unpooled.buffer(initialCapacity);
        byteBuf.writeShortLE((short) op.getValue());
        this.byteBuf = byteBuf;
    }

    @Override
    public byte[] getBytes() {
        return ByteBufUtil.getBytes(byteBuf);
    }

    @Override
    public void writeByte(byte value) {
        byteBuf.writeByte(value);
    }

    @Override
    public void writeByte(int value) {
        writeByte((byte) value);
    }

    @Override
    public void writeBytes(byte[] value) {
        byteBuf.writeBytes(value);
    }

    @Override
    public void writeShort(int value) {
        byteBuf.writeShortLE(value);
    }

    @Override
    public void writeInt(int value) {
        byteBuf.writeIntLE(value);
    }

    @Override
    public void writeLong(long value) {
        byteBuf.writeLongLE(value);
    }

    @Override
    public void writeBool(boolean value) {
        byteBuf.writeByte(value ? 1 : 0);
    }

    @Override
    public void writeString(String value) {
        byte[] bytes = value.getBytes(CharsetConstants.CHARSET);
        writeShort(bytes.length);
        writeBytes(bytes);
    }

    @Override
    public void writeFixedString(String value) {
        writeBytes(value.getBytes(CharsetConstants.CHARSET));
    }

    @Override
    public void writeFixedString(String value, int byteLength) {
        if (byteLength < 0) {
            throw new IllegalArgumentException("Fixed string byte length must not be negative");
        }

        byte[] field = new byte[byteLength];
        if (value == null || byteLength == 0) {
            writeBytes(field);
            return;
        }

        int offset = 0;
        CharsetEncoder encoder = CharsetConstants.CHARSET.newEncoder();
        for (int charOffset = 0; charOffset < value.length();) {
            int codePoint = value.codePointAt(charOffset);
            int charCount = java.lang.Character.charCount(codePoint);
            String character = value.substring(charOffset, charOffset + charCount);
            byte[] encoded = encoder.canEncode(character)
                    ? character.getBytes(CharsetConstants.CHARSET)
                    : new byte[]{'?'};
            if (offset + encoded.length > byteLength) {
                break;
            }

            System.arraycopy(encoded, 0, field, offset, encoded.length);
            offset += encoded.length;
            charOffset += charCount;
        }

        writeBytes(field);
    }

    @Override
    public void writePos(Point value) {
        writeShort((short) value.getX());
        writeShort((short) value.getY());
    }

    @Override
    public void skip(int numberOfBytes) {
        writeBytes(new byte[numberOfBytes]);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ByteBufOutPacket other && byteBuf.equals(other.byteBuf);
    }
}

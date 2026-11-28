package net.sourceforge.opencamera.cameracontroller;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.media.Image;
import android.os.Build;
import android.util.Rational;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/** Writes the PJD110's native binned Bayer buffer when Camera2 reports only the full-size geometry. */
final class BinnedDng {
    static final int WIDTH = 4096, HEIGHT = 3072;
    private static final int BYTE = 1, ASCII = 2, SHORT = 3, LONG = 4, RATIONAL = 5, UNDEFINED = 7, SRATIONAL = 10;
    private final CameraCharacteristics characteristics;
    private final CaptureResult result;
    private final int orientation;

    BinnedDng(CameraCharacteristics characteristics, CaptureResult result, int orientation) {
        this.characteristics = characteristics;
        this.result = result;
        this.orientation = orientation;
    }

    private static ByteBuffer le(int size) { return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN); }

    private static final class Ifd extends TreeMap<Integer, Tag> {
        void bytes(int tag, int type, byte... values) { put(tag, new Tag(type, values.length, values)); }
        void ascii(int tag, String text) { bytes(tag, ASCII, (text + '\0').getBytes(StandardCharsets.US_ASCII)); }
        void shorts(int tag, int... values) {
            ByteBuffer b = le(2 * values.length);
            for (int value : values) b.putShort((short) value);
            put(tag, new Tag(SHORT, values.length, b.array()));
        }
        void longs(int tag, int... values) {
            ByteBuffer b = le(4 * values.length);
            for (int value : values) b.putInt(value);
            put(tag, new Tag(LONG, values.length, b.array()));
        }
        void ratios(int tag, int type, Rational... values) {
            ByteBuffer b = le(8 * values.length);
            for (Rational v : values) b.putInt(v.getNumerator()).putInt(v.getDenominator());
            put(tag, new Tag(type, values.length, b.array()));
        }
        int tableSize() { return 6 + 12 * size(); }
        int dataSize() {
            int size = 0;
            for (Tag entry : values()) if (entry.data.length > 4) size += (entry.data.length + 1) & ~1;
            return size;
        }
        int write(ByteBuffer b, int offset, int data) {
            b.position(offset);
            b.putShort((short) size());
            for (Map.Entry<Integer, Tag> pair : entrySet()) {
                Tag e = pair.getValue();
                b.putShort((short) (int) pair.getKey()).putShort((short) e.type).putInt(e.count);
                if (e.data.length <= 4) {
                    int end = b.position() + 4;
                    b.put(e.data).position(end);
                } else {
                    b.putInt(data);
                    int next = b.position();
                    b.position(data);
                    b.put(e.data).position(next);
                    data += (e.data.length + 1) & ~1;
                }
            }
            b.putInt(0);
            return data;
        }
    }

    private static final class Tag {
        final int type, count;
        final byte[] data;
        Tag(int type, int count, byte[] data) { this.type = type; this.count = count; this.data = data; }
    }

    private void calibration(Ifd raw, int index, CameraCharacteristics.Key<? extends Number> lightKey,
                             CameraCharacteristics.Key<ColorSpaceTransform> matrixKey) throws IOException {
        Number light = characteristics.get(lightKey);
        ColorSpaceTransform matrix = characteristics.get(matrixKey);
        if (light == null || matrix == null) {
            if (index == 0) throw new IOException("Missing RAW color calibration");
            return;
        }
        Rational[] values = new Rational[9];
        for (int i = 0; i < values.length; i++) values[i] = matrix.getElement(i % 3, i / 3);
        raw.shorts(50778 + index, light.intValue());
        raw.ratios(50721 + index, SRATIONAL, values);
    }

    void write(OutputStream stream, Image image) throws IOException {
        if (image.getWidth() != WIDTH || image.getHeight() != HEIGHT || image.getPlanes().length != 1)
            throw new IOException("Unexpected binned RAW buffer");
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer pixels = plane.getBuffer().duplicate();
        int start = pixels.position(), stride = plane.getRowStride(), rowBytes = WIDTH * 2;
        if (plane.getPixelStride() != 2 || stride < rowBytes || (long) start + (long) stride * (HEIGHT - 1) + rowBytes > pixels.limit())
            throw new IOException("Unexpected binned RAW stride");

        Ifd raw = new Ifd();
        raw.longs(256, WIDTH); raw.longs(257, HEIGHT); raw.shorts(258, 16);
        raw.shorts(259, 1); raw.shorts(262, 32803); raw.longs(273, 0);
        raw.shorts(274, orientation >= 1 && orientation <= 8 ? orientation : 1);
        raw.shorts(277, 1); raw.longs(278, HEIGHT); raw.longs(279, rowBytes * HEIGHT);
        raw.shorts(284, 1);
        raw.ascii(271, Build.MANUFACTURER); raw.ascii(272, Build.MODEL);
        raw.shorts(33421, 2, 2);
        Integer cfa = characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        byte[][] patterns = {{0,1,1,2}, {1,0,2,1}, {1,2,0,1}, {2,1,1,0}};
        if (cfa == null || cfa < 0 || cfa >= patterns.length) throw new IOException("Unsupported Bayer pattern");
        raw.bytes(33422, BYTE, patterns[cfa]);
        raw.bytes(50706, BYTE, (byte) 1, (byte) 4, (byte) 0, (byte) 0);
        raw.bytes(50707, BYTE, (byte) 1, (byte) 1, (byte) 0, (byte) 0);
        raw.ascii(50708, Build.MANUFACTURER + " " + Build.MODEL);
        raw.bytes(50710, BYTE, (byte) 0, (byte) 1, (byte) 2); raw.shorts(50711, 1);
        raw.shorts(50713, 2, 2);
        float[] dynamicBlack = result.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL);
        BlackLevelPattern black = characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
        if (black == null && (dynamicBlack == null || dynamicBlack.length != 4)) throw new IOException("Missing RAW black level");
        Rational[] levels = new Rational[4];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = dynamicBlack != null && dynamicBlack.length == 4
                    ? new Rational(Math.round(dynamicBlack[i] * 1024), 1024)
                    : new Rational(black.getOffsetForIndex(i % 2, i / 2), 1);
        }
        raw.ratios(50714, RATIONAL, levels);
        Integer white = result.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL);
        if (white == null) white = characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
        if (white == null || white <= 0 || white > 65535) throw new IOException("Missing RAW white level");
        raw.longs(50717, white);
        raw.longs(50719, 0, 0); raw.longs(50720, WIDTH, HEIGHT);
        raw.longs(50829, 0, 0, HEIGHT, WIDTH);
        calibration(raw, 0, CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1, CameraCharacteristics.SENSOR_COLOR_TRANSFORM1);
        calibration(raw, 1, CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2, CameraCharacteristics.SENSOR_COLOR_TRANSFORM2);
        Rational[] neutral = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
        if (neutral != null && neutral.length == 3) raw.ratios(50728, RATIONAL, neutral);

        Ifd exif = new Ifd();
        exif.bytes(36864, UNDEFINED, (byte) '0', (byte) '2', (byte) '3', (byte) '1');
        Long exposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        if (exposure != null && exposure > 0 && exposure <= Integer.MAX_VALUE)
            exif.ratios(33434, RATIONAL, new Rational(exposure.intValue(), 1_000_000_000));
        Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
        if (iso != null && iso > 0 && iso <= 65535) exif.shorts(34855, iso);
        raw.longs(34665, 0);
        int exifOffset = 8 + raw.tableSize();
        int data = exifOffset + exif.tableSize();
        int pixelOffset = data + raw.dataSize() + exif.dataSize();
        raw.longs(34665, exifOffset); raw.longs(273, pixelOffset);
        ByteBuffer header = le(pixelOffset);
        header.putShort((short) 0x4949).putShort((short) 42).putInt(8);
        exif.write(header, exifOffset, raw.write(header, 8, data));
        stream.write(header.array());
        byte[] row = new byte[rowBytes];
        for (int y = 0; y < HEIGHT; y++) {
            pixels.position(start + stride * y);
            pixels.get(row);
            stream.write(row);
        }
    }
}

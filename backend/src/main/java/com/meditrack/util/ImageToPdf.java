package com.meditrack.util;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

/**
 * Wraps one raster image (JPG / PNG, anything the JDK's ImageIO can read) in a single-page PDF.
 * Pure JDK - no PDF library. The source file is only read, never modified; the PDF exists only in memory.
 * The image is scaled to fit an A4 page (portrait or landscape, whichever suits it) with a small margin.
 * JPEG EXIF rotation (phone photos) is honoured.
 */
public final class ImageToPdf {

    private static final long MAX_PIXELS = 40_000_000L;
    private static final double MARGIN = 24;

    private ImageToPdf() {}

    public static byte[] convert(byte[] imageBytes) throws IOException {
        BufferedImage source = readImage(imageBytes);
        source = applyExifOrientation(source, exifOrientation(imageBytes));
        int w = source.getWidth();
        int h = source.getHeight();

        // Flatten onto white (drops alpha, normalises any colour model to plain RGB).
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(source, 0, 0, null);
        g.dispose();

        ByteArrayOutputStream deflated = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(deflated)) {
            byte[] row = new byte[w * 3];
            int[] px = new int[w];
            for (int y = 0; y < h; y++) {
                rgb.getRGB(0, y, w, 1, px, 0, w);
                for (int x = 0; x < w; x++) {
                    row[x * 3] = (byte) (px[x] >> 16);
                    row[x * 3 + 1] = (byte) (px[x] >> 8);
                    row[x * 3 + 2] = (byte) px[x];
                }
                out.write(row);
            }
        }

        boolean landscape = w > h;
        double pageW = landscape ? 842 : 595;
        double pageH = landscape ? 595 : 842;
        double scale = Math.min((pageW - 2 * MARGIN) / w, (pageH - 2 * MARGIN) / h);
        double drawW = w * scale;
        double drawH = h * scale;
        double x = (pageW - drawW) / 2;
        double y = (pageH - drawH) / 2;
        String content = String.format(java.util.Locale.ROOT, "q %.2f 0 0 %.2f %.2f %.2f cm /Im0 Do Q", drawW, drawH, x, y);

        PdfWriter pdf = new PdfWriter();
        pdf.object("<< /Type /Catalog /Pages 2 0 R >>");
        pdf.object("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        pdf.object(String.format(java.util.Locale.ROOT,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.0f %.0f] /Resources << /XObject << /Im0 5 0 R >> >> /Contents 4 0 R >>",
                pageW, pageH));
        pdf.stream("", content.getBytes(StandardCharsets.US_ASCII));
        pdf.stream("/Type /XObject /Subtype /Image /Width " + w + " /Height " + h
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode", deflated.toByteArray());
        return pdf.finish();
    }

    public static byte[] convert(InputStream in) throws IOException {
        return convert(in.readAllBytes());
    }

    // ------------------------------------------------------------------ image decoding

    private static BufferedImage readImage(byte[] bytes) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new IOException("Unsupported or corrupt image");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw new IOException("Image is too large to convert");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    // ------------------------------------------------------------------ EXIF orientation (JPEG only)

    /** Returns the EXIF orientation tag (1-8), or 1 if absent / not a JPEG. */
    static int exifOrientation(byte[] d) {
        try {
            if (d.length < 4 || (d[0] & 0xFF) != 0xFF || (d[1] & 0xFF) != 0xD8) return 1;
            int pos = 2;
            while (pos + 4 < d.length && (d[pos] & 0xFF) == 0xFF) {
                int marker = d[pos + 1] & 0xFF;
                int len = ((d[pos + 2] & 0xFF) << 8) | (d[pos + 3] & 0xFF);
                if (marker == 0xE1 && len >= 16 && d[pos + 4] == 'E' && d[pos + 5] == 'x' && d[pos + 6] == 'i' && d[pos + 7] == 'f') {
                    int tiff = pos + 10;
                    boolean big = d[tiff] == 'M';
                    int ifd = tiff + (int) u32(d, tiff + 4, big);
                    int n = u16(d, ifd, big);
                    for (int i = 0; i < n; i++) {
                        int entry = ifd + 2 + i * 12;
                        if (u16(d, entry, big) == 0x0112) {
                            int v = u16(d, entry + 8, big);
                            return v >= 1 && v <= 8 ? v : 1;
                        }
                    }
                    return 1;
                }
                if (marker == 0xDA) break; // start of scan - no more headers
                pos += 2 + len;
            }
        } catch (RuntimeException ignored) {
            // malformed EXIF - just don't rotate
        }
        return 1;
    }

    private static int u16(byte[] d, int p, boolean big) {
        return big ? ((d[p] & 0xFF) << 8) | (d[p + 1] & 0xFF) : ((d[p + 1] & 0xFF) << 8) | (d[p] & 0xFF);
    }

    private static long u32(byte[] d, int p, boolean big) {
        return big
                ? ((long) u16(d, p, true) << 16) | u16(d, p + 2, true)
                : ((long) u16(d, p + 2, false) << 16) | u16(d, p, false);
    }

    private static BufferedImage applyExifOrientation(BufferedImage img, int orientation) {
        int w = img.getWidth();
        int h = img.getHeight();
        AffineTransform t = new AffineTransform();
        boolean swap = false;
        switch (orientation) {
            case 2 -> { t.scale(-1, 1); t.translate(-w, 0); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.scale(1, -1); t.translate(0, -h); }
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); swap = true; }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); swap = true; }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); swap = true; }
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); swap = true; }
            default -> { return img; }
        }
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, t, null);
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------------ minimal PDF writer

    private static final class PdfWriter {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final List<Integer> offsets = new ArrayList<>();

        PdfWriter() {
            write("%PDF-1.4\n");
            out.write(0x25); out.write(0xE2); out.write(0xE3); out.write(0xCF); out.write(0xD3); out.write('\n');
        }

        void object(String body) {
            offsets.add(out.size());
            write((offsets.size()) + " 0 obj\n" + body + "\nendobj\n");
        }

        void stream(String dictEntries, byte[] data) {
            offsets.add(out.size());
            write(offsets.size() + " 0 obj\n<< " + dictEntries + " /Length " + data.length + " >>\nstream\n");
            out.write(data, 0, data.length);
            write("\nendstream\nendobj\n");
        }

        byte[] finish() {
            int xref = out.size();
            StringBuilder sb = new StringBuilder("xref\n0 " + (offsets.size() + 1) + "\n0000000000 65535 f \n");
            for (int off : offsets) sb.append(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", off));
            sb.append("trailer\n<< /Size ").append(offsets.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
            write(sb.toString());
            return out.toByteArray();
        }

        private void write(String s) {
            byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
            out.write(b, 0, b.length);
        }
    }
}

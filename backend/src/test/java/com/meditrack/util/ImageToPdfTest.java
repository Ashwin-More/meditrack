package com.meditrack.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ImageToPdfTest {

    private byte[] image(int w, int h, String format) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    @Test
    void png_becomesAValidSinglePagePdf() throws IOException {
        byte[] source = image(400, 250, "png");
        byte[] copy = source.clone();

        byte[] pdf = ImageToPdf.convert(source);

        String text = new String(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("%PDF-1.4"));
        assertTrue(text.contains("/Subtype /Image") && text.contains("/Count 1"));
        assertTrue(text.trim().endsWith("%%EOF"));
        assertTrue(text.contains("/MediaBox [0 0 842 595]"), "wide image -> landscape A4");
        assertArrayEquals(copy, source, "the original image bytes must be untouched");
    }

    @Test
    void jpg_tallImage_getsAPortraitPage() throws IOException {
        String text = new String(ImageToPdf.convert(image(300, 500, "jpg")), StandardCharsets.ISO_8859_1);
        assertTrue(text.contains("/MediaBox [0 0 595 842]"));
    }

    @Test
    void notAnImage_isRejected() {
        assertThrows(IOException.class, () -> ImageToPdf.convert("definitely not an image".getBytes(StandardCharsets.UTF_8)));
    }
}

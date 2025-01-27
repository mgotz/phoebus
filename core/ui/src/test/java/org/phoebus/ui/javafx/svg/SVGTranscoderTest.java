/*
 * Copyright (C) 2020 European Spallation Source ERIC.
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.phoebus.ui.javafx.svg;

import javafx.scene.image.Image;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SVGTranscoderTest {

    /**
     * Verifies that the SVG file can be loaded and that the created {@link Image} has
     * non-zero width and height.
     */
    @Test
    public void testSVGHelper(){
        try {
            Image image = SVGTranscoder.loadSVG(getInputStream("/interlock.svg"), 400d, 400d);
            assertTrue(image.getHeight() > 0);
            assertTrue(image.getWidth() > 0);
        } catch (Exception e) {
           fail(e.getMessage());
        }
    }

    /**
     * Verifies that {@link Exception} is thrown when loading png file.
     */
    @Test(expected = Exception.class)
    public void testSVGHelperPngFile() throws Exception{
        assertNull(SVGTranscoder.loadSVG(getInputStream("/interlock.png"), 400d, 400d));
    }

    /**
     * Verifies that {@link Exception} is thrown when loading jpg file.
     */
    @Test(expected = Exception.class)
    public void testSVGHelperJpgFile() throws Exception{
        SVGTranscoder.loadSVG(getInputStream("/interlock.jpg"), 400d, 400d);
    }

    /**
     * Verifies that {@link Exception} is thrown when loading gif file.
     */
    @Test(expected = Exception.class)
    public void testSVGHelperGifFile() throws Exception{
        assertNull(SVGTranscoder.loadSVG(getInputStream("/interlock.gif"), 400d, 400d));
    }

    /**
     * Verifies that {@link Exception} is thrown when loading tiff file.
     */
    @Test(expected = Exception.class)
    public void testSVGHelperTiffFile() throws Exception{
        assertNull(SVGTranscoder.loadSVG(getInputStream("/interlock.tiff"), 400d, 400d));
    }

    private InputStream getInputStream(String resource) throws Exception{
        return getClass().getResourceAsStream(resource);
    }
}

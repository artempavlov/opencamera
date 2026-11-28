package net.sourceforge.opencamera.cameracontroller;

import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import net.sourceforge.opencamera.MyDebug;

import java.io.IOException;
import java.io.OutputStream;

/** Wrapper class to store DngCreator and Image.
 */
public class RawImage {
    private static final String TAG = "RawImage";

    private final DngCreator dngCreator;
    private final BinnedDng binnedDng;
    private final Image image;
    private ReaderLifetime owner;

    // An acquired Image is invalidated by ImageReader.close(). The saver can
    // outlive the session, so give it a reference to the reader, not the camera.
    static final class ReaderLifetime {
        private final ImageReader reader;
        private int references = 1; // camera controller's reference

        ReaderLifetime(ImageReader reader) { this.reader = reader; }

        synchronized RawImage handOff(RawImage image) {
            if( references == 0 || image.owner != null ) throw new IllegalStateException();
            references++;
            image.owner = this;
            return image;
        }

        synchronized void release() {
            if( --references == 0 ) reader.close();
        }
    }

    public RawImage(DngCreator dngCreator, Image image) {
        this.dngCreator = dngCreator;
        this.binnedDng = null;
        this.image = image;
    }

    RawImage(BinnedDng binnedDng, Image image) {
        this.dngCreator = null;
        this.binnedDng = binnedDng;
        this.image = image;
    }

    /** Writes the dng file to the supplied output.
     */
    public void writeImage(OutputStream dngOutput) throws IOException {
        if( MyDebug.LOG )
            Log.d(TAG, "writeImage");
        try {
            if( binnedDng != null ) binnedDng.write(dngOutput, image);
            else dngCreator.writeImage(dngOutput, image);
        }
        catch(AssertionError e) {
            // have had AssertionError from OnePlus 5 on Google Play; rethrow as an IOException so it's handled
            // in the same way
            MyDebug.logStackTrace(TAG, "failed to write SNG image", e);
            throw new IOException();
        }
        catch(IllegalStateException e) {
            // have had IllegalStateException from Galaxy Note 8 on Google Play; rethrow as an IOException so it's handled
            // in the same way
            MyDebug.logStackTrace(TAG, "failed to write SNG image", e);
            throw new IOException();
        }
    }

    /** Closes the image. Must be called to free up resources when no longer needed. After calling
     *  this method, this object should not be used.
     */
    public void close() {
        if( MyDebug.LOG )
            Log.d(TAG, "close");
        try {
            image.close();
            if( dngCreator != null ) dngCreator.close();
        } finally {
            if( owner != null ) { owner.release(); owner = null; }
        }
    }
}

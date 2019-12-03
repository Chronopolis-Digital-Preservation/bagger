package org.chronopolis.bag.packager;

import com.google.common.hash.HashFunction;
import org.chronopolis.bag.core.Manifest;
import org.chronopolis.bag.core.PayloadFile;
import org.chronopolis.bag.core.TagFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Interface describing the actual writing of bags to an output stream
 *
 * Created by shake on 7/30/2015.
 */
public interface Packager {

    /**
     * Start a build using the given bag name
     *
     * @param bagName the name of the bag
     */
    PackagerData startBuild(String bagName);

    /**
     * Finish a build
     *
     */
    void finishBuild(PackagerData data);

    /**
     * Write a tag file to the bag
     *
     * @param tagFile  The TagFile to write
     * @param function The HashFunction to use
     * @return the result of writing the TagFile, including the Hash and Bytes written
     */
    PackageResult writeTagFile(TagFile tagFile, HashFunction function, PackagerData data) throws IOException;

    /**
     * Write the manifest file of the bag
     *
     * @param manifest The manifest to write
     * @param function The HashFunction to use
     * @return the result of writing the Manifest
     */
    PackageResult writeManifest(Manifest manifest, HashFunction function, PackagerData data) throws IOException;

    /**
     * Write a payload file to the data directory of the bag
     *
     * @param payloadFile The PayloadFile to write
     * @param function The HashFunction to use
     * @return the result of writing the payload file
     */
    PackageResult writePayloadFile(PayloadFile payloadFile, HashFunction function, PackagerData data) throws IOException;

    /**
     * Transfer bytes from an InputStream to an OutputStream using Channels
     *
     * @param is the InputStream to read from
     * @param os the OutputStream to write to
     * @throws IOException if there's an exception transferring bytes
     * @return the amount of bytes written
     */
    default Long transfer(InputStream is, OutputStream os) throws IOException {
        Long written = 0L;
        ReadableByteChannel inch = Channels.newChannel(is);
        WritableByteChannel wrch = Channels.newChannel(os);

        // 1MB, might want to make this configurable
        ByteBuffer buffer = ByteBuffer.allocateDirect(32768);
        while (inch.read(buffer) != -1) {
            buffer.flip();
            written += wrch.write(buffer);
            buffer.compact();
        }

        buffer.flip();
        if (buffer.hasRemaining()) {
            written += wrch.write(buffer);
        }

        buffer.clear();
        inch.close();
        wrch.close();
        is.close();
        os.close();
        return written;
    }

}

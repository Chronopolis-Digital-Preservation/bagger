package org.chronopolis.bag.packager;

import com.google.common.hash.HashFunction;
import com.google.common.hash.HashingInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.chronopolis.bag.core.Manifest;
import org.chronopolis.bag.core.PayloadFile;
import org.chronopolis.bag.core.TagFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Packager for creating a serialized bag in the form of a tarball
 * <p>
 * Created by shake on 8/28/15.
 */
@SuppressWarnings({"FieldCanBeLocal", "WeakerAccess"})
public class TarPackager implements Packager {
    private final Logger log = LoggerFactory.getLogger(TarPackager.class);

    private final String TAR = ".tar";

    private final Path base;

    public TarPackager(Path base) {
        this.base = base;
    }

    @Override
    public PackagerData startBuild(String bagName) {
        PackagerData data = new PackagerData();

        TarArchiveOutputStream outputStream = null;
        Path tarball = base.resolve(bagName + TAR);

        // TODO: Check this
        tarball.getParent().toFile().mkdirs();

        try {
            OutputStream os = new FileOutputStream(tarball.toString());
            outputStream = new TarArchiveOutputStream(os);
            outputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            outputStream.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
        } catch (FileNotFoundException e) {
            log.error("Error create TarArchiveOutputStream", e);
            // throw an exception
        }

        data.setName(bagName);
        data.setOs(outputStream);

        return data;
    }

    @Override
    public void finishBuild(PackagerData data) {
        try (OutputStream os = data.getOs()) {
            log.trace("Autoclosing outputstream");
        } catch (IOException e) {
            log.error("Error closing Tar OutputStream", e);
        }
    }

    @Override
    public PackageResult writeTagFile(TagFile tagFile, HashFunction function, PackagerData data) throws IOException {
        Path path = getTarPath(data.getName(), tagFile.getPath());
        try (InputStream is = tagFile.getInputStream()) {
            return writeFile(function, is, path.toString(), tagFile.getSize(), data);
        }
    }

    @Override
    public PackageResult writeManifest(Manifest manifest, HashFunction function, PackagerData data) throws IOException {
        Path path = getTarPath(data.getName(), manifest.getPath());
        try (InputStream is = manifest.getInputStream()) {
            return writeFile(function, is, path.toString(), manifest.getSize(), data);
        }
    }

    @Override
    public PackageResult writePayloadFile(PayloadFile payloadFile, HashFunction function, PackagerData data) throws IOException {
        Path path = getTarPath(data.getName(), payloadFile.getFile());
        try (InputStream is = payloadFile.getInputStream()) {
            return writeFile(function, is, path.toString(), payloadFile.getSize(), data);
        }
    }

    private PackageResult writeFile(HashFunction function, InputStream is, String path, long size, PackagerData data) throws IOException {
        PackageResult result;
        HashingInputStream his = null;
        OutputStream os = data.getOs();
        // Figure this out/clean it up a bit
        if (!(os instanceof TarArchiveOutputStream)) {
            log.error("Cannot package tar archive to non tar stream");
            return new PackageResult(0L, function.newHasher().hash());
        }

        TarArchiveOutputStream outputStream = (TarArchiveOutputStream) os;
        TarArchiveEntry entry = new TarArchiveEntry(path);
        Long bytes;

        log.trace("Setting payload size of " + size);
        entry.setSize(size);
        outputStream.putArchiveEntry(entry);
        long written = outputStream.getBytesWritten();
        his = new HashingInputStream(function, is);
        try {
            bytes = transfer(his, outputStream);
        } catch (IOException e) {
            log.error("Error writing TarArchiveEntry {}", path, e);
            failArchiveEntry(size, written, outputStream);
            throw e;
        }
        outputStream.closeArchiveEntry();

        return new PackageResult(bytes, his.hash());
    }

    /**
     * I'm not really sure of the best way to handle this, but we NEED to close the archive entry
     * in order to be able to close the tarball and be able to release the file descriptor. So
     * what we end up doing is pretty straightforward - calculate how much of the current Archive
     * Entry we've written, and see how much we still need to write. Then we fill the Entry with
     * junk (we're failing anyways), and if any errors happen along the way we just log them
     * because what else can we do?
     *
     * @param size         - the size of the ArchiveEntry
     * @param written      - how much was written to the OutputStream before starting the entry
     * @param outputStream - the OutputStream
     */
    private void failArchiveEntry(long size, long written, TarArchiveOutputStream outputStream) {
        log.info("Attempting to fill out archive entry so underlying stream can be closed");
        long current = outputStream.getBytesWritten();
        long delta = current - written;
        long fill = size - delta;
        log.debug("size={}, written={}, current={}, delta={}", new Object[]{size, written, current, delta});
        log.debug("Need to write {} bytes", fill);

        if (fill < 0) {
            log.error("Error calculating amount of bytes to fill, OutputStream will remain open!");
        }

        log.warn("Filling TarOutputStream with junk to close ArchiveEntry");
        while (fill >= 0) {
            try {
                // fill the os with junk
                // todo: this is probably slow as. need to revisit.
                outputStream.write(0b00000000);
                fill--;
            } catch (IOException e) {
                // ?? Not sure of the best course of action here
                log.error("", e);
                fill = -1;
            }
        }

        log.warn("Closing ArchiveEntry");
        try {
            outputStream.closeArchiveEntry();
        } catch (IOException e) {
            log.error("Unable to close ArchiveEntry, OutputStream will remain open!", e);
        }
    }

    private Path getTarPath(String name, Path relPath) {
        Path root = Paths.get(name + "/");
        return root.resolve(relPath);
    }

    /**
     * Transfer the content of the input stream to the output stream
     * without closing the underlying output stream (by calling wrch.close())
     *
     * @param is the InputStream to transfer from
     * @param os the OutputStream to transfer to
     * @throws IOException if there's a problem transferring bytes
     */
    @Override
    public Long transfer(InputStream is, OutputStream os) throws IOException {
        Long written = 0L;
        ReadableByteChannel inch = Channels.newChannel(is);
        WritableByteChannel wrch = Channels.newChannel(os);

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
        is.close();
        return written;
    }

}

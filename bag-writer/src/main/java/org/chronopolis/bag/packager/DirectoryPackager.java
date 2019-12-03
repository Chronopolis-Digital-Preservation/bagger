package org.chronopolis.bag.packager;

import com.google.common.hash.HashFunction;
import com.google.common.hash.HashingOutputStream;
import org.chronopolis.bag.core.Manifest;
import org.chronopolis.bag.core.PayloadFile;
import org.chronopolis.bag.core.TagFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Package bags to a separate directory
 *
 * Created by shake on 8/6/2015.
 */
public class DirectoryPackager implements Packager {
    private final Logger log = LoggerFactory.getLogger(DirectoryPackager.class);

    private final Path base;

    public DirectoryPackager(Path base) {
        this.base = base;
    }

    @Override
    public PackagerData startBuild(String name) {
        PackagerData data = new PackagerData();
        Path output = base.resolve(name);

        // TODO: Check if this exists first
        output.toFile().mkdirs();
        data.setName(name);
        data.setWrite(output);
        return data;
    }

    @Override
    public void finishBuild(PackagerData data) {
        // Nothing really to do
    }

    @Override
    public PackageResult writeTagFile(TagFile tagFile, HashFunction function, PackagerData data) throws IOException {
        // tagFile.getName() or tagFile.getPath()
        // "sub/dir/name" not really the name
        // "sub/dir/name" is a path
        // allows for things like the dpn-tags easily
        // probably want 1 method for transferring actual bytes/channel
        Path tag = data.getWrite().resolve(tagFile.getPath());
        try (InputStream is = tagFile.getInputStream()) {
            return writeFile(tag, function, is);
        }
    }

    @Override
    public PackageResult writeManifest(Manifest manifest, HashFunction function, PackagerData data) throws IOException {
        Path tag = data.getWrite().resolve(manifest.getPath());
        try (InputStream is = manifest.getInputStream()) {
            return writeFile(tag, function, is);
        }
    }

    @Override
    public PackageResult writePayloadFile(PayloadFile payloadFile, HashFunction function, PackagerData data) throws IOException {
        // Ensure that all the directories are created first
        Path payload = data.getWrite().resolve(payloadFile.getFile());
        try {
            Files.createDirectories(payload.getParent());
        } catch (IOException e) {
            log.error("Error creating directories for {}", payload, e);
            throw e;
        }

        try (InputStream is = payloadFile.getInputStream()) {
            return writeFile(payload, function, is);
        }
    }

    /**
     * Common code between all the other methods which write files
     *
     * @param out The path of the file we're writing to
     * @param function The HashFunction to capture the write with
     * @param is The InputStream we're reading from
     * @return The HashCode of the written OutputStream
     * @throws IOException if there's a problem writing bytes
     */
    @SuppressWarnings("WeakerAccess")
    protected PackageResult writeFile(Path out, HashFunction function, InputStream is) throws IOException {
        OutputStream os = Files.newOutputStream(out, StandardOpenOption.CREATE);
        HashingOutputStream hos = new HashingOutputStream(function, os);
        Long bytes = transfer(is, hos);
        return new PackageResult(bytes, hos.hash());
    }

}

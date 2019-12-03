package org.chronopolis.bag.packager;

import com.google.common.hash.HashFunction;
import com.google.common.io.Files;
import org.chronopolis.bag.core.Manifest;
import org.chronopolis.bag.core.PayloadFile;
import org.chronopolis.bag.core.TagFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Packager to support writing bags in place
 *
 * Created by shake on 11/10/15.
 */
public class InPlaceDirectoryPackager extends DirectoryPackager {
    private final Logger log = LoggerFactory.getLogger(InPlaceDirectoryPackager.class);

    private final Path base;

    public InPlaceDirectoryPackager(Path base) {
        super(base);

        this.base = base;
    }

    @Override
    public PackagerData startBuild(String name) {
        PackagerData data = new PackagerData();
        if (!base.endsWith(name)) {
            log.warn("Top level directory for bag {} does not match", name);
        }
        data.setName(name);
        data.setWrite(base);
        return data;
    }

    @Override
    public void finishBuild(PackagerData data) {
        // Nothing to do (as of now)
    }

    @Override
    public PackageResult writeTagFile(TagFile tagFile, HashFunction function, PackagerData data) {
        PackageResult result = null;

        Path tag = base.resolve(tagFile.getPath());
        String errorText = null;

        try {
            if (tag.toFile().exists()) {
                errorText = "Error hashing TagFile {}";
                log.debug("TagFile {} already exists, only hashing", tag);
                result = new PackageResult(0L, Files.hash(tag.toFile(), function));
            } else {
                errorText = "Error writing TagFile {}";
                log.debug("TagFile {} not found, creating", tag);
                result = writeFile(tag, function, tagFile.getInputStream());
            }
        } catch (IOException e) {
            log.error(errorText, tag, e);
        }

        return result;
    }

    @Override
    public PackageResult writeManifest(Manifest manifest, HashFunction function, PackagerData data) {
        return writeTagFile(manifest, function, data);
    }

    @Override
    public PackageResult writePayloadFile(PayloadFile payloadFile, HashFunction function, PackagerData data) {
        // Ensure that all the directories are created first
        Path payload = base.resolve(payloadFile.getFile());
        PackageResult result;
        try {
            result = new PackageResult(0L, Files.hash(payload.toFile(), function));
        } catch (IOException e) {
            log.error("Error hashing payload file {}", payload, e);
            result = new PackageResult(0L, function.hashInt(0));
        }

        return result;
    }

}

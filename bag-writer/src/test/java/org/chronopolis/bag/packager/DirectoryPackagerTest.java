package org.chronopolis.bag.packager;

import com.google.common.hash.HashCode;
import org.chronopolis.bag.core.BagIt;
import org.chronopolis.bag.core.Digest;
import org.chronopolis.bag.core.Manifest;
import org.chronopolis.bag.core.PayloadFile;
import org.chronopolis.bag.core.PayloadManifest;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * Created by shake on 5/16/16.
 */
public class DirectoryPackagerTest extends PackagerTest {

    private final String bagitHash = "b1d4cb0d13cb1d9ab509ff9048c4f8d9720bf73ec5856320b1c8ed8c81cd61e1";

    private DirectoryPackager startPackager() {
        return new DirectoryPackager(out);
    }

    @Test
    public void startBuild() throws Exception {
        String buildName = "packager-test";
        System.out.println("using " + out);
        DirectoryPackager packager = startPackager();
        packager.startBuild(buildName);

        Assert.assertTrue(Files.exists(out.resolve(buildName)));
        Assert.assertTrue(Files.isDirectory(out.resolve(buildName)));
    }

    @Test
    public void writeTagFile() throws Exception {
        String testName = "tag-files";
        Path bag = out.resolve(testName);
        DirectoryPackager packager = startPackager();
        PackagerData packagerData = packager.startBuild(testName);
        BagIt bagIt = new BagIt();
        HashCode hashCode = packager.writeTagFile(bagIt, func, packagerData).getHashCode();

        Assert.assertTrue(Files.exists(bag.resolve("bagit.txt")));
        Assert.assertTrue(Files.isRegularFile(bag.resolve("bagit.txt")));
        Assert.assertEquals(bagitHash, hashCode.toString());
    }

    @Test(expected = IOException.class)
    public void writeTagIO() throws Exception {
        String testName = "tag-files-ioe";

        DirectoryPackager packager = startPackager();
        PackagerData data = packager.startBuild(testName);
        IOTagFile tag = new IOTagFile();
        HashCode hash = packager.writeTagFile(tag, func, data).getHashCode();

        Assert.assertNull(hash);
        // Assert.assertFalse(Files.exists(bag.resolve(tag.getPath())));
    }

    @Test
    public void writeManifest() throws Exception {
        String testName = "manifest";
        Path bag = out.resolve(testName);

        DirectoryPackager packager = startPackager();
        PackagerData data = packager.startBuild(testName);
        PayloadManifest m = new PayloadManifest();
        m.setDigest(Digest.SHA_256);
        m.addPayloadFile(f);

        HashCode payload = func.hashString(f.toString(), Charset.defaultCharset());
        HashCode pkgHash = packager.writeManifest(m, func, data).getHashCode();
        HashCode fileHash = com.google.common.io.Files.hash(bag.resolve("manifest-sha256.txt").toFile(), func);

        Assert.assertTrue(Files.exists(bag.resolve("manifest-sha256.txt")));
        Assert.assertTrue(Files.isRegularFile(bag.resolve("manifest-sha256.txt")));

        // Test that the returned hashcode and the file on disk equal what we expect
        Assert.assertEquals(payload, pkgHash);
        Assert.assertEquals(payload, fileHash);
    }

    @Test(expected = IOException.class)
    public void writeManifestIO() throws Exception {
        String testName = "manifest-ioe";

        DirectoryPackager packager = startPackager();
        PackagerData data = packager.startBuild(testName);
        Manifest m = new IOManifest();
        HashCode hash = packager.writeManifest(m, func, data).getHashCode();

        Assert.assertNull(hash);
        // Assert.assertFalse(Files.exists(bag.resolve(m.getPath())));
    }

    @Test
    public void writePayloadFile() throws Exception {
        String testName = "payload";
        Path bag = out.resolve(testName);

        DirectoryPackager packager = startPackager();
        PackagerData data = packager.startBuild(testName);
        HashCode hashCode = packager.writePayloadFile(f, func, data).getHashCode();

        String tags = ClassLoader.getSystemClassLoader()
                                 .getResource("payload").getPath();
        HashCode fromResources = com.google.common.io.Files.hash(Paths.get(tags, "payload-file").toFile(), func);

        Path fileInBag = bag.resolve(f.getFile());
        Assert.assertTrue(Files.exists(fileInBag));
        Assert.assertTrue(Files.isRegularFile(fileInBag));

        // Make sure nothing borked while writing, similar to the manifest
        Assert.assertEquals(fromResources, hashCode);
        Assert.assertEquals(f.getDigest(), hashCode);
    }

    @Test(expected = IOException.class)
    public void writePayloadIO() throws Exception {
        String testName = "payload-ioe";

        DirectoryPackager packager = startPackager();
        PackagerData data = packager.startBuild(testName);
        PayloadFile payload = new IOPayloadFile();

        HashCode hash = packager.writePayloadFile(payload, func, data).getHashCode();
        Assert.assertNull(hash);
    }

}
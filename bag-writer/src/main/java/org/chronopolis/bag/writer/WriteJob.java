package org.chronopolis.bag.writer;

import com.google.common.base.Stopwatch;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import org.chronopolis.bag.core.Bag;
import org.chronopolis.bag.core.Manifest;
import org.chronopolis.bag.core.PayloadFile;
import org.chronopolis.bag.core.TagFile;
import org.chronopolis.bag.core.TagManifest;
import org.chronopolis.bag.metrics.Metric;
import org.chronopolis.bag.metrics.WriteMetrics;
import org.chronopolis.bag.packager.PackageResult;
import org.chronopolis.bag.packager.Packager;
import org.chronopolis.bag.packager.PackagerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Class which writes bags with a given packager
 * <p>
 * Created by shake on 11/16/16.
 */
public class WriteJob implements Callable<WriteResult>, Supplier<WriteResult> {
    private final Logger log = LoggerFactory.getLogger(WriteJob.class);

    private final Bag bag;
    private final boolean captureMetrics;
    private final boolean validate;
    private final Packager packager;
    private WriteResult result;

    public WriteJob(Bag bag, boolean captureMetrics, boolean validate, Packager packager) {
        this.bag = bag;
        this.captureMetrics = captureMetrics;
        this.validate = validate;
        this.packager = packager;
        result = new WriteResult();
    }

    @Override
    public WriteResult call() {
        // metrics for total bytes and files written
        Long bytes = 0L;
        Long files = 0L;

        // Is there a better way to get this information? Maybe store it in the bag.
        result.setBag(bag);
        // TODO: Get rid of this extraneous call
        bag.prepareForWrite();
        HashFunction hash = bag.getManifest().getDigest().getHashFunction();

        log.info("Starting build for {}", bag.getName());
        // Metric capturing classes
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        WriteMetrics metrics = new WriteMetrics();
        Metric tagMetric = new Metric();
        Metric manifestMetric = new Metric();
        MetricGroup tagMetrics = new MetricGroup();
        MetricGroup payloadMetrics = new MetricGroup();
        Metric bagMetric = new Metric();

        PackagerData data = null;
        try {
            stopwatch.start();
            data = packager.startBuild(bag.getName());
            TagManifest tagManifest = bag.getTagManifest();
            manifestMetric = writeManifest(bag, hash, tagManifest, data);
            tagMetrics = writeTagFiles(bag, hash, tagManifest, data);
            tagMetric = writeTagManifest(bag, hash, tagManifest, data);
            payloadMetrics = writePayloadFiles(bag, hash, data);
        } catch (Exception e) {
            result.setSuccess(false);
            log.error("Error building bag!", e);
        } finally {
            packager.finishBuild(data);
            stopwatch.stop();
        }

        if (captureMetrics) {
            metrics.setTagmanifest(tagMetric);
            metrics.setManifest(manifestMetric);
            metrics.setExtraTags(tagMetrics.getItems());
            metrics.setPayload(payloadMetrics.getGroup());
            metrics.setPayloadFiles(payloadMetrics.getItems());

            bytes += tagMetric.getBytesWritten()
                    + manifestMetric.getBytesWritten()
                    + tagMetrics.bytesWritten
                    + payloadMetrics.bytesWritten;
            files += tagMetric.getFilesWritten()
                    + manifestMetric.getFilesWritten()
                    + tagMetrics.filesWritten
                    + payloadMetrics.filesWritten;
            bagMetric.setElapsed(stopwatch.elapsed(TimeUnit.MILLISECONDS))
                    .setBytesWritten(bytes)
                    .setFilesWritten(files);
            metrics.setBag(bagMetric);
            result.setMetrics(metrics);
        }

        return result;
    }

    private Metric writeTagManifest(Bag bag, HashFunction hash, TagManifest tagManifest, PackagerData data) throws IOException {
        HashCode hashCode;
        PackageResult packageResult;
        Metric metric = new Metric();

        // Write the tagmanifest
        log.info("[{}] writing tagmanifest", bag.getName());
        Stopwatch stopwatch = Stopwatch.createStarted();
        packageResult = packager.writeManifest(tagManifest, hash, data);
        stopwatch.stop();

        // capture the receipt (hash of the tagmanifest)
        hashCode = packageResult.getHashCode();
        String receipt = hashCode.toString();
        bag.setReceipt(receipt);
        result.setReceipt(receipt);
        log.debug("[{}] receipt is {}", bag.getName(), receipt);

        // capture metrics
        metric.setFilesWritten(1L)
                .setBytesWritten(packageResult.getBytes())
                .setElapsed(stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return metric;
    }

    private MetricGroup writeTagFiles(Bag bag, HashFunction hash, TagManifest tagManifest, PackagerData data) throws IOException {
        HashCode hashCode;
        PackageResult result;
        MetricGroup group = new MetricGroup();

        // Write tag files
        log.info("Writing tag files for {}", bag.getName());
        for (TagFile tag : bag.getTags().values()) {
            Metric metric = new Metric();

            log.debug("{}", tag.getPath());
            Stopwatch stopwatch = Stopwatch.createStarted();
            result = packager.writeTagFile(tag, hash, data);
            stopwatch.stop();

            hashCode = result.getHashCode();
            tagManifest.addTagFile(tag.getPath(), hashCode);
            log.debug("HashCode is {}", hashCode.toString());

            if (captureMetrics) {
                metric.setFilesWritten(1L)
                        .setBytesWritten(result.getBytes())
                        .setElapsed(stopwatch.elapsed(TimeUnit.MILLISECONDS));
                group.addItem(metric);
            }
        }

        return group;
    }

    private Metric writeManifest(Bag bag, HashFunction hash, TagManifest tagManifest, PackagerData data) throws IOException {
        HashCode hashCode;
        PackageResult result;
        Metric metric = new Metric();

        // Write manifest
        log.info("[{}] writing manifest", bag.getName());
        Manifest manifest = bag.getManifest();
        Stopwatch stopwatch = Stopwatch.createStarted();
        result = packager.writeManifest(manifest, hash, data);
        stopwatch.stop();

        hashCode = result.getHashCode();
        tagManifest.addTagFile(manifest.getPath(), hashCode);
        log.debug("[{}] manifest digest is {}", bag.getName(), hashCode.toString());

        // capture metrics
        metric.setFilesWritten(1L)
                .setBytesWritten(result.getBytes())
                .setElapsed(stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return metric;
    }

    private MetricGroup writePayloadFiles(Bag bag, HashFunction hash, PackagerData data) throws IOException {
        HashCode hashCode;
        PackageResult packageResult;
        MetricGroup group = new MetricGroup();

        // if (capture) group.add(item)
        // Write payload files
        // Validate if wanted
        log.info("Writing payload files for {}", bag.getName());
        if (bag.getFiles().isEmpty()) {
            log.warn("Bag has no payload files, marking as error");
            bag.addBuildError("Bag has no payload files");
        }

        for (PayloadFile payloadFile : bag.getFiles().values()) {
            log.trace(payloadFile.getFile() + ": ");
            Metric fileMetric = new Metric();
            Stopwatch stopwatch = Stopwatch.createStarted();
            packageResult = packager.writePayloadFile(payloadFile, hash, data);
            stopwatch.stop();

            hashCode = packageResult.getHashCode();
            log.trace(hashCode.toString());
            if (validate && !hashCode.equals(payloadFile.getDigest())) {
                log.error("[{}] Digest mismatch for file {}. Expected {}; Found {}",
                        new Object[]{bag.getName(), payloadFile, payloadFile.getDigest(), hashCode});
                result.setSuccess(false);
                bag.addError(payloadFile);
            }

            bag.addFile(payloadFile);

            if (captureMetrics) {
                fileMetric.setFilesWritten(1L)
                        .setBytesWritten(packageResult.getBytes())
                        .setElapsed(stopwatch.elapsed(TimeUnit.MILLISECONDS));
                group.addItem(fileMetric);
            }
        }

        return group;
    }

    private static class MetricGroup {
        private Long elapsed = 0L;
        private Long bytesWritten = 0L;
        private Long filesWritten = 0L;

        private Set<Metric> items = new HashSet<>();

        private void addItem(Metric item) {
            elapsed += item.getElapsed();
            bytesWritten += item.getBytesWritten();
            filesWritten += item.getFilesWritten();
            items.add(item);
        }

        private Set<Metric> getItems() {
            return items;
        }

        private Metric getGroup() {
            return new Metric()
                    .setElapsed(elapsed)
                    .setBytesWritten(bytesWritten)
                    .setFilesWritten(filesWritten);
        }
    }

    @Override
    public WriteResult get() {
        return call();
    }
}

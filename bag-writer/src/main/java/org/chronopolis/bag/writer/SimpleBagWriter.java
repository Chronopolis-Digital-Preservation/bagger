package org.chronopolis.bag.writer;

import org.chronopolis.bag.core.Bag;
import org.chronopolis.bag.packager.Packager;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 *
 * Created by shake on 11/16/16.
 */
public class SimpleBagWriter implements BagWriter {

    private boolean captureMetrics;
    private boolean validate;
    private Packager packager;

    // TODO: Should we just have a constructor for these and
    //       not have any setters?

    @Override
    public BagWriter metrics(boolean metrics) {
        this.captureMetrics = metrics;
        return this;
    }

    @Override
    public BagWriter validate(boolean validate) {
        this.validate = validate;
        return this;
    }

    @Override
    public BagWriter withPackager(Packager packager) {
        this.packager = packager;
        return this;
    }

    @Override
    public List<WriteResult> write(List<Bag> bags) {
        checkNull(bags);
        return bags.stream()
                .map(this::fromBag)
                .collect(Collectors.toList());
    }

    // Just a helper so we have a clean map above
    // TODO should we have a WriteJobFactory so we can create custom WriteJobs?
    private WriteResult fromBag(Bag bag) {
        WriteJob job = new WriteJob(bag, captureMetrics, validate, packager);
        return job.get();
    }

    @Override
    public List<CompletableFuture<WriteResult>> write(List<Bag> bags, Executor executor) {
        checkNull(bags);
        return bags.stream()
                .map(b -> CompletableFuture.supplyAsync(new WriteJob(b, captureMetrics, validate, packager), executor))
                .collect(Collectors.toList());
    }

    private void checkNull(List<Bag> bags) {
        if (bags == null) {
            throw new IllegalArgumentException("Bags cannot be null");
        }
    }
}

package org.chronopolis.bag.writer;

import org.chronopolis.bag.core.Bag;
import org.chronopolis.bag.packager.Packager;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Test interface for a new writer
 *
 * Created by shake on 11/16/16.
 */
public interface BagWriter {

    BagWriter metrics(boolean metrics);
    BagWriter validate(boolean validate);
    BagWriter withPackager(Packager packager);

    List<WriteResult> write(List<Bag> bags);
    List<CompletableFuture<WriteResult>> write(List<Bag> bags, Executor executor);

}

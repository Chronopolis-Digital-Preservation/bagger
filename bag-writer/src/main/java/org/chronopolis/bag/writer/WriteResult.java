package org.chronopolis.bag.writer;

import org.chronopolis.bag.core.Bag;
import org.chronopolis.bag.metrics.WriteMetrics;

/**
 * Result from trying to write a bag
 *
 * Created by shake on 11/16/16.
 */
public class WriteResult {

    private Bag bag;
    private String receipt;
    private boolean success;
    private WriteMetrics metrics;

    public WriteResult() {
        this.success = true;
    }

    public Bag getBag() {
        return bag;
    }

    public WriteResult setBag(Bag bag) {
        this.bag = bag;
        return this;
    }

    public String getReceipt() {
        return receipt;
    }

    public WriteResult setReceipt(String receipt) {
        this.receipt = receipt;
        return this;
    }

    public boolean isSuccess() {
        return success;
    }

    public WriteResult setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public WriteResult setMetrics(WriteMetrics metrics) {
        this.metrics = metrics;
        return this;
    }

    public WriteMetrics getMetrics() {
        return metrics;
    }
}

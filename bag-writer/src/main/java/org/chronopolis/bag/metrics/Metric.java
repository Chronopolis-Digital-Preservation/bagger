package org.chronopolis.bag.metrics;

/**
 * Class for capturing information about a write
 *
 * This can take place over multiple files or a single file so
 * that we can capture information about the entire Bag being written,
 * a tag file, or all payload files. Honestly it's going to be rough
 * because it's being shoved in so that we can capture some info for
 * tests.
 *
 * @author shake
 */
public class Metric {
    private long elapsed;
    private Long bytesWritten;
    private Long filesWritten;

    public Long getBytesWritten() {
        return bytesWritten;
    }

    public Metric setBytesWritten(Long bytesWritten) {
        this.bytesWritten = bytesWritten;
        return this;
    }

    public Long getFilesWritten() {
        return filesWritten;
    }

    public Metric setFilesWritten(Long filesWritten) {
        this.filesWritten = filesWritten;
        return this;
    }

    public long getElapsed() {
        return elapsed;
    }

    public Metric setElapsed(long elapsed) {
        this.elapsed = elapsed;
        return this;
    }
}

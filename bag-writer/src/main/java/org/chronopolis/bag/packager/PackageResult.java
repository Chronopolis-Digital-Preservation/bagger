package org.chronopolis.bag.packager;

import com.google.common.hash.HashCode;

/**
 * Little class to capture information about the result of writing
 * a file with a packager
 *
 * @author shake
 */
public class PackageResult {
    private final Long bytes;
    private final HashCode hashCode;

    public PackageResult(Long bytes, HashCode hashCode) {
        this.bytes = bytes;
        this.hashCode = hashCode;
    }

    public Long getBytes() {
        return bytes;
    }

    public HashCode getHashCode() {
        return hashCode;
    }
}

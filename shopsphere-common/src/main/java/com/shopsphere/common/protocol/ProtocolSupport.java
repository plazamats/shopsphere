package com.shopsphere.common.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;

/**
 * Shared settings for the object streams. Java deserialization of untrusted data is dangerous,
 * so every ObjectInputStream gets a filter that only allows our own DTOs and a few JDK types.
 */
public final class ProtocolSupport {

    public static final int DEFAULT_PORT = 5555;

    private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
            "maxdepth=20;maxarray=100000;"
            + "com.shopsphere.common.**;java.lang.*;java.util.*;java.math.*;java.time.*;!*");

    private ProtocolSupport() { }

    // maxbytes and maxrefs are left out on purpose: the JDK counts them across the whole stream,
    // so on a long lived connection they would eventually reject normal requests.

    /** Create an ObjectInputStream that rejects every class that is not on the allow list. */
    public static ObjectInputStream filteredInput(InputStream in) throws IOException {
        ObjectInputStream ois = new ObjectInputStream(in);
        ois.setObjectInputFilter(FILTER);
        return ois;
    }
}

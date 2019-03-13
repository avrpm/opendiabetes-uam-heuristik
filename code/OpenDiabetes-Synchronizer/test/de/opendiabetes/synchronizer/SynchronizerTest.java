package de.opendiabetes.synchronizer;

import de.opendiabetes.vault.container.VaultEntry;
import de.opendiabetes.vault.container.VaultEntryType;
import de.opendiabetes.vault.nsapi.NSApi;
import de.opendiabetes.vault.nsapi.exception.NightscoutIOException;
import de.opendiabetes.vault.nsapi.exception.NightscoutServerException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class SynchronizerTest {
    private static NSApi read;
    private static NSApi write;

    @BeforeAll
    static void setUp() {
        String readHost = System.getenv("NS_HOST");
        String readSecret = System.getenv("NS_APISECRET");
        String writeHost = System.getenv("NS_HOST_2");
        String writeSecret = System.getenv("NS_APISECRET_2");
        if (readHost == null)
            System.err.println("Environment variable NS_HOST not found!");
        if (readSecret == null)
            System.err.println("Environment variable NS_APISECRET not found!");
        if (writeHost == null)
            System.err.println("Environment variable NS_HOST_2 not found!");
        if (writeSecret == null)
            System.err.println("Environment variable NS_APISECRET_2 not found!");
        if (readHost == null || readSecret == null || writeHost == null || writeSecret == null)
            fail("");

        read = new NSApi(readHost, readSecret);
        write = new NSApi(writeHost, writeSecret);
    }

    @AfterAll
    static void tearDown() throws IOException {
        read.close();
        write.close();
    }

    @Test
    void testMissingEntry() throws NightscoutIOException, NightscoutServerException {
        Synchronizer synchronizer = new Synchronizer(read, write);
        Synchronizable sync = new Synchronizable("entries", "dateString");
        synchronizer.findMissing(sync);
        int found = sync.getFindCount();
        int missing = sync.getMissingCount();
        synchronizer.getReadApi().postEntries(Collections.singletonList(
                // set one minute in the past to prevent issues with clocks out of sync
                new VaultEntry(VaultEntryType.GLUCOSE_CGM, new Date(System.currentTimeMillis() - 60 * 1000), 80)
        ));
        synchronizer.findMissing(sync);
        assertEquals(found + 1, sync.getFindCount());
        assertEquals(missing + 1, sync.getMissingCount());
    }

    @Test
    void testMissingTreatments() throws NightscoutIOException, NightscoutServerException {
        Synchronizer synchronizer = new Synchronizer(read, write);
        Synchronizable sync = new Synchronizable("treatments", "created_at");
        synchronizer.findMissing(sync);
        int found = sync.getFindCount();
        int missing = sync.getMissingCount();
        synchronizer.getReadApi().postTreatments(Collections.singletonList(
                // set one minute in the past to prevent issues with clocks out of sync
                new VaultEntry(VaultEntryType.BOLUS_NORMAL, new Date(System.currentTimeMillis() - 60 * 1000), 4)
        ));
        synchronizer.findMissing(sync);
        assertEquals(found + 1, sync.getFindCount());
        assertEquals(missing + 1, sync.getMissingCount());
    }
}

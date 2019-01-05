package de.opendiabetes.nsapi;

import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.exceptions.UnirestException;
import de.opendiabetes.vault.engine.container.VaultEntry;
import de.opendiabetes.vault.engine.container.VaultEntryType;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class NSApiTest {
    private static NSApi api;

    @BeforeAll
    static void setUp() {
        String host, port, secret;

        try (InputStream input = new FileInputStream("resources/config.properties")) {
            Properties properties = new Properties();
            properties.load(input);

            host = properties.getProperty("host");
            port = properties.getProperty("port");
            secret = properties.getProperty("secret");
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
            return;
        }

        api = new NSApi(host, port, secret);
    }

    @AfterAll
    static void tearDown() {
        api.close();
    }


    @Test
    void testStatus() throws UnirestException {
        JSONObject status = api.getStatus();
        assertNotNull(status);
        assertEquals("ok", status.getString("status"));        // tests should break if status is not ok
        assertTrue(status.getBoolean("apiEnabled"));    // test should break if api is not enabled
    }

    @Test
    void testEntries() throws UnirestException {
        String id = getRandomId(24);
        int sgv = 70 + new Random().nextInt(60);

        LocalDateTime time = LocalDateTime.now();
        long date = Timestamp.valueOf(time).getTime();
        String dateString = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(time);

        JsonNode result = api.postEntries("[{" +
                "\"direction\": \"Flat\"," +
                "\"_id\": \"" + id + "\"," +
                "\"sgv\": " + sgv + "," +
                "\"dateString\": \"" + dateString + "\"," +
                "\"date\": " + date + "," +
                "\"type\": \"sgv\"" +
                "}]");
        assertNotNull(result);

        List<VaultEntry> entries = api.getEntries().find("dateString").eq(dateString).getVaultEntries();
        assertEquals(1, entries.size());
        assertEquals(VaultEntryType.GLUCOSE_CGM, entries.get(0).getType());
        assertEquals(sgv, entries.get(0).getValue());
    }

    @Test
    void testTreatments() throws UnirestException {
        String id = getRandomId(24);
        int carbs = 80 + new Random().nextInt(100);

        LocalDateTime time = LocalDateTime.now();
        String createdAt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(time);

        JsonNode result = api.postTreatments("[{" +
                "\"_id\": \"" + id + "\"," +
                "\"carbs\": " + carbs + "," +
                "\"created_at\": \"" + createdAt + "\"," +
                "\"timestamp\": \"" + createdAt + "\"" +
                "}]");
        assertNotNull(result);

        List<VaultEntry> entries = api.getTreatments().find("created_at").eq(createdAt).getVaultEntries();
        assertEquals(1, entries.size());
        assertEquals(VaultEntryType.MEAL_MANUAL, entries.get(0).getType());
        assertEquals(carbs, entries.get(0).getValue());
    }

    @Test
    void testEchoTimes() throws UnirestException {
        String prefix = "foo";
        String regex = "bar";
        JSONObject echo = api.getTimesEcho(prefix, regex).get().getObject();
        assertTrue(echo.has("req"));
        assertTrue(echo.getJSONObject("req").has("params"));
        assertEquals(prefix, echo.getJSONObject("req").getJSONObject("params").getString("prefix"));
        assertEquals(regex, echo.getJSONObject("req").getJSONObject("params").getString("regex"));
    }

    @Test
    void testTimes() throws UnirestException {
        List<VaultEntry> entries = api.getTimes("2018", "T{15..17}:.*").getVaultEntries();
        entries.stream()
                .map(e -> LocalDateTime.ofInstant(e.getTimestamp().toInstant(), ZoneId.systemDefault()))
                .forEach(t -> assertTrue(t.getHour() >= 15 && t.getHour() <= 17));
    }

    private final static String ID_RANGE = "0123456789abcdef";

    private static String getRandomId(int length) {
        Random random = new Random();
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < length; i++)
            id.append(ID_RANGE.charAt(random.nextInt(ID_RANGE.length())));
        return id.toString();
    }
}
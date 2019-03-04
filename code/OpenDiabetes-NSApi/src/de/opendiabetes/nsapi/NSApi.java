package de.opendiabetes.nsapi;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequest;
import com.mashape.unirest.request.HttpRequestWithBody;
import de.opendiabetes.nsapi.exception.NightscoutDataException;
import de.opendiabetes.nsapi.exception.NightscoutIOException;
import de.opendiabetes.nsapi.exception.NightscoutServerException;
import de.opendiabetes.nsapi.exporter.NightscoutExporter;
import de.opendiabetes.parser.Profile;
import de.opendiabetes.parser.ProfileParser;
import de.opendiabetes.parser.Status;
import de.opendiabetes.parser.StatusParser;
import de.opendiabetes.vault.container.VaultEntry;
import de.opendiabetes.vault.util.IOStreamUtil;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;

public class NSApi {
    private final static NightscoutExporter EXPORTER = new NightscoutExporter();

    private String host;

    /**
     * Constructs a new NighScout API instance. Connects to the specified host and port, using the given token for all queries
     *
     * @param host   Host to connect to
     * @param secret Your API-Secret
     */
    public NSApi(String host, String secret) {
        this.host = host + "/api/v1/";
        Unirest.setDefaultHeader("accept", "application/json");
        Unirest.setDefaultHeader("content-type", "application/json");
        Unirest.setDefaultHeader("API-SECRET", DigestUtils.sha1Hex(secret));
    }

    /**
     * Closes the connection to the NightScout API
     *
     * @throws IOException if an exception occurs while closing the connection
     */
    public void close() throws IOException {
        Unirest.shutdown();
    }

    // utilities

    /**
     * Reads the stream using {@link IOStreamUtil#readInputStream(InputStream)}. Closes the stream after reading it.
     *
     * @param stream stream
     * @return contents of the stream
     * @throws NightscoutIOException if an I/O error occurs during the request
     */
    private String readInputStream(InputStream stream) throws NightscoutIOException {
        String content = IOStreamUtil.readInputStream(stream);
        try {
            stream.close();
        } catch (IOException e) {
            throw new NightscoutIOException("Exception while closing stream", e);
        }
        return content;
    }

    // GET

    private HttpRequest get(String path) {
        return Unirest.get(host + path);
    }

    /**
     * Sends the request to the server and gets the body of the response as an {@link InputStream}.
     *
     * @return the body of the response
     * @throws NightscoutIOException     if an I/O error occurs during the request
     * @throws NightscoutServerException if the Nightscout server returns a bad response status
     */
    InputStream getRawInputStream(HttpRequest request) throws NightscoutIOException, NightscoutServerException {
        HttpResponse<InputStream> response;
        try {
            response = request.asBinary();
        } catch (UnirestException e) {
            throw new NightscoutIOException("Exception while sending request to the server", e);
        }
        if (response.getStatus() != 200)
            throw new NightscoutServerException(response);
        return response.getBody();
    }

    /**
     * Sends a GET request for the status
     *
     * @return the status as a JSON formatted String
     * @throws NightscoutIOException     if an I/O error occurs during the request
     * @throws NightscoutServerException if the Nightscout server returns a bad response status
     */
    public Status getStatus() throws NightscoutIOException, NightscoutServerException {
        InputStream stream = getRawInputStream(get("status"));
        StatusParser parser = new StatusParser();
        return parser.parse(readInputStream(stream));
    }

    /**
     * Fetches and parses the profile from Nightscout
     *
     * @return the fetched Profile
     * @throws NightscoutIOException     if an I/O error occurs during the request
     * @throws NightscoutServerException if the Nightscout server returns a bad response status
     */
    public Profile getProfile() throws NightscoutIOException, NightscoutServerException {
        InputStream stream = getRawInputStream(get("profile"));
        ProfileParser parser = new ProfileParser();
        return parser.parse(readInputStream(stream));
    }

    /**
     * Creates a {@link GetBuilder} for entries
     *
     * @return a {@link GetBuilder} with <code>"entries"</code> as its path
     */
    public GetBuilder getEntries() {
        return new GetBuilder(this, get("entries"));
    }

    /**
     * Fetches all entries from Nightscout that are in between the given latest and oldest time (inclusive).
     * Entries are fetched in batches with the given batch size until Nightscout returns no more results.
     *
     * @param latest    latest point in time, has to be formatable with {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME}
     * @param oldest    oldest point in time, has to be formatable with {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME}
     * @param batchSize amount of entries fetched at once
     * @return list of fetched entries
     * @throws NightscoutIOException       if an I/O error occurs during the request
     * @throws NightscoutServerException   if the Nightscout server returns a bad response status
     * @throws java.time.DateTimeException if an error occurs while formatting latest or oldest
     */
    public List<VaultEntry> getEntries(TemporalAccessor latest, TemporalAccessor oldest, int batchSize) throws NightscoutIOException, NightscoutServerException {
        return getVaultEntries(latest, oldest, batchSize, "entries", "dateString");
    }

    /**
     * Creates a {@link GetBuilder} for slices
     *
     * @param storage Prefix to use in constructing a prefix-based regex, e.g. entries.
     * @param field   Name of the field to use Regex against in query object, e.g. dateString.
     * @param type    The type field to search against, e.g. sgv.
     * @param prefix  Prefix to use in constructing a prefix-based regex.
     * @param regex   Tail part of regexp to use in expanding/construccting a query object.
     *                Regexp also has bash-style brace and glob expansion applied to it,
     *                creating ways to search for modal times of day, perhaps using something like this syntax:
     *                T{15..17}:.*, this would search for all records from 3pm to 5pm.
     * @return a {@link GetBuilder} with the given parameters as its path
     */
    public GetBuilder getSlice(String storage, String field, String type, String prefix, String regex) {
        return new GetBuilder(this, get("slice/" + storage + "/" + field + "/" + type + "/" + prefix + "/" + regex));
    }

    /**
     * Information about the mongo query object created by the query.
     *
     * @param storage entries, or treatments to select the storage layer.
     * @param spec    entry id, such as 55cf81bc436037528ec75fa5 or a type filter such as sgv, mbg, etc.
     *                This parameter is optional.
     * @return a {@link GetBuilder} with the given parameters as its path
     */
    public GetBuilder getEcho(String storage, String spec) {
        return new GetBuilder(this, get("echo/" + storage + "/" + spec));
    }

    /**
     * Echo debug information about the query object constructed.
     *
     * @param prefix Prefix to use in constructing a prefix-based regex.
     * @param regex  Tail part of regexp to use in expanding/construccting a query object.
     *               Regexp also has bash-style brace and glob expansion applied to it,
     *               creating ways to search for modal times of day, perhaps using something like this syntax:
     *               T{15..17}:.*, this would search for all records from 3pm to 5pm.
     * @return a {@link GetBuilder} with the given parameters as its path
     */
    public GetBuilder getTimesEcho(String prefix, String regex) {
        return new GetBuilder(this, get("times/echo/" + prefix + "/" + regex));
    }

    /**
     * The Entries endpoint returns information about the Nightscout entries.
     *
     * @param prefix Prefix to use in constructing a prefix-based regex.
     * @param regex  Tail part of regexp to use in expanding/construccting a query object.
     *               Regexp also has bash-style brace and glob expansion applied to it,
     *               creating ways to search for modal times of day, perhaps using something like this syntax:
     *               T{15..17}:.*, this would search for all records from 3pm to 5pm.
     * @return a {@link GetBuilder} with the given parameters as its path
     */
    public GetBuilder getTimes(String prefix, String regex) {
        return new GetBuilder(this, get("times/" + prefix + "/" + regex));
    }

    /**
     * The Treatments endpoint returns information about the Nightscout treatments.
     *
     * @return a {@link GetBuilder} with treatments as its path
     */
    public GetBuilder getTreatments() {
        return new GetBuilder(this, get("treatments"));
    }

    /**
     * Fetches all treatments from Nightscout that are in between the given latest and oldest time (inclusive).
     * Treatments are fetched in batches with the given batch size until Nightscout returns no more results.
     *
     * @param latest    latest point in time, has to be formatable with {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME}
     * @param oldest    oldest point in time, has to be formatable with {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME}
     * @param batchSize amount of entries fetched at once
     * @return list of fetched entries
     * @throws NightscoutIOException       if an I/O error occurs during the request
     * @throws NightscoutServerException   if the Nightscout server returns a bad response status
     * @throws java.time.DateTimeException if an error occurs while formatting latest or oldest
     */
    public List<VaultEntry> getTreatments(TemporalAccessor latest, TemporalAccessor oldest, int batchSize) throws NightscoutIOException, NightscoutServerException {
        return getVaultEntries(latest, oldest, batchSize, "treatments", "created_at");
    }

    private List<VaultEntry> getVaultEntries(TemporalAccessor latest, TemporalAccessor oldest, int batchSize, String path, String dateField) throws NightscoutIOException, NightscoutServerException {
        List<VaultEntry> entries = new ArrayList<>();
        List<VaultEntry> fetched = null;
        GetBuilder getBuilder;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        String latestString = formatter.format(latest);
        String oldestString = formatter.format(oldest);
        do {
            getBuilder = new GetBuilder(this, get(path)).count(batchSize).find(dateField).gte(oldestString);
            if (fetched == null)    // first fetch: lte, following fetches: lt
                getBuilder.find(dateField).lte(latestString);
            else getBuilder.find(dateField).lt(latestString);
            fetched = getBuilder.getVaultEntries();
            if (!fetched.isEmpty()) {
                entries.addAll(fetched);
                latestString = fetched.get(fetched.size() - 1).getTimestamp().toInstant().toString();
            }
        } while (!fetched.isEmpty());
        return entries;
    }

    // POST

    private HttpRequestWithBody post(String path) {
        return Unirest.post(host + path);
    }

    /**
     * Sends a POST request with the given entries as its payload.
     * Uses {@link NightscoutExporter} to export the data.
     *
     * @param entries data
     * @throws NightscoutIOException     if an I/O error occurs during the request
     * @throws NightscoutServerException if the Nightscout server returns a bad response status
     * @throws NightscoutDataException   if an exception occurs while exporting the data
     */
    public void postEntries(List<VaultEntry> entries) throws NightscoutIOException, NightscoutServerException, NightscoutDataException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        EXPORTER.exportData(stream, entries);
        HttpResponse<InputStream> response;
        String body = new String(stream.toByteArray());
        try {
            response = post("entries").body(body).asBinary();
        } catch (UnirestException e) {
            throw new NightscoutIOException("Exception occured while sending the request", e);
        }
        if (response.getStatus() != 200)
            throw new NightscoutServerException(response);
    }

    /**
     * Sends a POST request with the given treatments as its payload.
     * Uses {@link NightscoutExporter} to export the data.
     *
     * @param treatments data
     * @throws NightscoutIOException     if an I/O error occurs during the request
     * @throws NightscoutServerException if the Nightscout server returns a bad response status
     * @throws NightscoutDataException   if an exception occurs while exporting the data
     */
    public void postTreatments(List<VaultEntry> treatments) throws NightscoutIOException, NightscoutServerException, NightscoutDataException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        EXPORTER.exportData(stream, treatments);
        HttpResponse<InputStream> response;
        try {
            response = post("treatments").body(stream.toByteArray()).asBinary();
        } catch (UnirestException e) {
            throw new NightscoutIOException("Exception occured while sending the request", e);
        }
        if (response.getStatus() != 200)
            throw new NightscoutServerException(response);
    }
}

package de.opendiabetes.vault.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * A generic parser to parse strings and file contents
 *
 * @param <T>
 */
public interface Parser<T> {
    /**
     * Parses the input
     *
     * @param input input
     * @return parsed content
     */
    T parse(String input);

    /**
     * Parses the contents of the file at the given path by invoking {@link #parse(String)}.
     *
     * @param path path to the file
     * @return parsed content
     */
    default T parseFile(String path) {
        return parseFile(Paths.get(path));
    }

    /**
     * Parses the contents of the file at the given path by invoking {@link #parse(String)}.
     *
     * @param path path to the file
     * @return parsed content
     */
    default T parseFile(Path path) {
        StringBuilder builder = new StringBuilder();

        try (Stream<String> stream = Files.lines(path, StandardCharsets.UTF_8)) {
            stream.forEach(builder::append);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return parse(builder.toString());
    }
}

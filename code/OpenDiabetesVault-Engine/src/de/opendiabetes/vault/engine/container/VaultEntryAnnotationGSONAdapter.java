/**
 * Copyright (C) 2017 OpenDiabetes
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.opendiabetes.vault.engine.container;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * Implements a JSON serializer for GSON based annotated VaultEntries.
 */
public class VaultEntryAnnotationGSONAdapter implements JsonSerializer<VaultEntryAnnotation>, JsonDeserializer<VaultEntryAnnotation> {

    /**
     * Serializer for VaultEntries.
     * @param entry VaultEntry to be serialized.
     * @param type Type of the entry.
     * @param jsc Context for the serializer.
     * @return Serialized VaultEntry as JSON element.
     */
    @Override
    public JsonElement serialize(final VaultEntryAnnotation entry, final Type type, final JsonSerializationContext jsc) {
        JsonObject obj = new JsonObject();
        obj.addProperty("t", entry.getType().ordinal());
        obj.addProperty("v", entry.getValue());
        return obj;
    }

    /**
     * Deserializer for JSON data.
     * @param element The JSON element to deserialize.
     * @param type The type of the element.
     * @param jdc Context for the deserializer.
     * @return De-serialized JSON element.
     * @throws JsonParseException Thrown if JSON element is faulty.
     */
    @Override
    public VaultEntryAnnotation deserialize(final JsonElement element, final Type type, final JsonDeserializationContext jdc)
            throws JsonParseException {
        VaultEntryAnnotation.TYPE veType = VaultEntryAnnotation.TYPE.values()[element.getAsJsonObject().get("t").getAsInt()];
        String weValue = element.getAsJsonObject().get("v").getAsString();
        return new VaultEntryAnnotation(weValue, veType);
    }

}
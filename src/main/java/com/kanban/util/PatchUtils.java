package com.kanban.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.json.Json;
import jakarta.json.JsonMergePatch;
import jakarta.json.JsonValue;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class PatchUtils {

    public JsonNode merge(JsonNode original, JsonNode patch) {
        if (patch == null || patch.isNull())
            return NullNode.instance;
        if (!patch.isObject() || !original.isObject())
            return patch;

        ObjectNode target = original.deepCopy();
        Iterator<String> fields = patch.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            JsonNode value = patch.get(field);

            if (value.isNull()) {
                target.remove(field);
            } else {
                JsonNode targetValue = target.get(field);
                if (value.isObject() && targetValue != null && targetValue.isObject()) {
                    JsonNode mergedChild = merge(targetValue, value);
                    target.set(field, mergedChild);
                } else {
                    target.set(field, value);
                }
            }
        }
        return target;
    }
}

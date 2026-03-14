package org.arcos.UserModel.PersonaTree;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.util.LinkedHashMap;

@JsonSerialize(using = PersonaNode.Serializer.class)
@JsonDeserialize(using = PersonaNode.Deserializer.class)
public class PersonaNode {

    private final LinkedHashMap<String, PersonaNode> children; // null if leaf
    private String value; // null if branch

    private PersonaNode(LinkedHashMap<String, PersonaNode> children, String value) {
        this.children = children;
        this.value = value;
    }

    public static PersonaNode leaf(String value) {
        return new PersonaNode(null, value != null ? value : "");
    }

    public static PersonaNode branch(LinkedHashMap<String, PersonaNode> children) {
        return new PersonaNode(children, null);
    }

    public boolean isLeaf() {
        return children == null;
    }

    public LinkedHashMap<String, PersonaNode> getChildren() {
        return children;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        if (!isLeaf()) throw new IllegalStateException("Cannot set value on a branch node");
        this.value = value;
    }

    public PersonaNode deepCopy() {
        if (isLeaf()) {
            return PersonaNode.leaf(this.value);
        }
        LinkedHashMap<String, PersonaNode> childCopies = new LinkedHashMap<>();
        for (var entry : children.entrySet()) {
            childCopies.put(entry.getKey(), entry.getValue().deepCopy());
        }
        return PersonaNode.branch(childCopies);
    }

    static class Serializer extends JsonSerializer<PersonaNode> {
        @Override
        public void serialize(PersonaNode node, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (node.isLeaf()) {
                gen.writeString(node.getValue());
            } else {
                gen.writeStartObject();
                for (var entry : node.getChildren().entrySet()) {
                    gen.writeFieldName(entry.getKey());
                    serialize(entry.getValue(), gen, provider);
                }
                gen.writeEndObject();
            }
        }
    }

    static class Deserializer extends JsonDeserializer<PersonaNode> {
        @Override
        public PersonaNode deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.currentToken() == JsonToken.VALUE_STRING) {
                return PersonaNode.leaf(p.getValueAsString());
            }
            if (p.currentToken() == JsonToken.START_OBJECT) {
                LinkedHashMap<String, PersonaNode> children = new LinkedHashMap<>();
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = p.currentName();
                    p.nextToken();
                    children.put(fieldName, deserialize(p, ctxt));
                }
                return PersonaNode.branch(children);
            }
            throw ctxt.wrongTokenException(p, PersonaNode.class, p.currentToken(),
                    "Expected STRING (leaf) or START_OBJECT (branch)");
        }
    }
}

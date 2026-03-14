package org.arcos.UserModel.PersonaTree;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.UserModelProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Component
public class PersonaTreeSchemaLoader {

    private final UserModelProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Set<String> validLeafPaths;
    private LinkedHashMap<String, PersonaNode> schemaRoots;

    public PersonaTreeSchemaLoader(UserModelProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource(properties.getPersonaTreeSchemaPath());
            try (InputStream is = resource.getInputStream()) {
                schemaRoots = objectMapper.readValue(is,
                        new TypeReference<LinkedHashMap<String, PersonaNode>>() {});
            }
            validLeafPaths = new LinkedHashSet<>();
            for (var entry : schemaRoots.entrySet()) {
                collectLeafPaths(entry.getKey(), entry.getValue(), validLeafPaths);
            }
            log.info("Loaded PersonaTree schema: {} root categories, {} leaf paths",
                    schemaRoots.size(), validLeafPaths.size());
        } catch (IOException e) {
            log.warn("Failed to load PersonaTree schema from '{}', creating minimal empty schema: {}",
                    properties.getPersonaTreeSchemaPath(), e.getMessage());
            schemaRoots = new LinkedHashMap<>();
            validLeafPaths = Set.of();
        }
    }

    public PersonaTree loadSchema() {
        LinkedHashMap<String, PersonaNode> copy = new LinkedHashMap<>();
        for (var entry : schemaRoots.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().deepCopy());
        }
        return new PersonaTree(copy);
    }

    public Set<String> getValidLeafPaths() {
        return Collections.unmodifiableSet(validLeafPaths);
    }

    public boolean isValidLeafPath(String path) {
        if (path == null || path.isEmpty()) return false;
        return validLeafPaths.contains(path);
    }

    private void collectLeafPaths(String currentPath, PersonaNode node, Set<String> paths) {
        if (node.isLeaf()) {
            paths.add(currentPath);
        } else if (node.getChildren() != null) {
            for (var entry : node.getChildren().entrySet()) {
                collectLeafPaths(currentPath + "." + entry.getKey(), entry.getValue(), paths);
            }
        }
    }
}

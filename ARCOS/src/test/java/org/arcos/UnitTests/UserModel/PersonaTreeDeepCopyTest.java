package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.PersonaTree.PersonaNode;
import org.arcos.UserModel.PersonaTree.PersonaTree;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class PersonaTreeDeepCopyTest {

    @Test
    void deepCopyPreservesConversationCount() {
        // Given
        LinkedHashMap<String, PersonaNode> roots = new LinkedHashMap<>();
        roots.put("test", PersonaNode.leaf("value"));
        PersonaTree tree = new PersonaTree(roots);
        tree.setConversationCount(42);

        // When
        PersonaTree copy = tree.deepCopy();

        // Then
        assertEquals(42, copy.getConversationCount());
    }

    @Test
    void deepCopyPreservesRootStructure() {
        // Given
        LinkedHashMap<String, PersonaNode> roots = new LinkedHashMap<>();
        roots.put("root1", PersonaNode.leaf("value1"));
        roots.put("root2", PersonaNode.leaf("value2"));
        LinkedHashMap<String, PersonaNode> children = new LinkedHashMap<>();
        children.put("child1", PersonaNode.leaf("childValue"));
        roots.put("root3", PersonaNode.branch(children));
        PersonaTree tree = new PersonaTree(roots);

        // When
        PersonaTree copy = tree.deepCopy();

        // Then
        assertEquals(3, copy.getRoots().size());
        assertTrue(copy.getRoots().containsKey("root1"));
        assertTrue(copy.getRoots().containsKey("root2"));
        assertTrue(copy.getRoots().containsKey("root3"));
        assertEquals("value1", copy.getRoots().get("root1").getValue());
        assertEquals("value2", copy.getRoots().get("root2").getValue());
        assertFalse(copy.getRoots().get("root3").isLeaf());
    }

    @Test
    void deepCopyIsIndependent() {
        // Given
        LinkedHashMap<String, PersonaNode> roots = new LinkedHashMap<>();
        roots.put("test", PersonaNode.leaf("original"));
        PersonaTree original = new PersonaTree(roots);

        // When
        PersonaTree copy = original.deepCopy();
        copy.getRoots().get("test").setValue("modified");

        // Then
        assertEquals("original", original.getRoots().get("test").getValue());
        assertEquals("modified", copy.getRoots().get("test").getValue());
    }

    @Test
    void incrementConversationCount() {
        // Given
        LinkedHashMap<String, PersonaNode> roots = new LinkedHashMap<>();
        roots.put("test", PersonaNode.leaf("value"));
        PersonaTree tree = new PersonaTree(roots);

        // When
        tree.incrementConversationCount();
        tree.incrementConversationCount();

        // Then
        assertEquals(2, tree.getConversationCount());
    }
}

package org.arcos.UserModel.PersonaTree;

import java.util.LinkedHashMap;

public class PersonaTree {

    private final LinkedHashMap<String, PersonaNode> roots;
    private int conversationCount;

    public PersonaTree(LinkedHashMap<String, PersonaNode> roots) {
        this.roots = roots;
        this.conversationCount = 0;
    }

    public LinkedHashMap<String, PersonaNode> getRoots() {
        return roots;
    }

    public int getConversationCount() {
        return conversationCount;
    }

    public void setConversationCount(int conversationCount) {
        this.conversationCount = conversationCount;
    }

    public void incrementConversationCount() {
        this.conversationCount++;
    }

    public PersonaTree deepCopy() {
        LinkedHashMap<String, PersonaNode> rootCopies = new LinkedHashMap<>();
        for (var entry : roots.entrySet()) {
            rootCopies.put(entry.getKey(), entry.getValue().deepCopy());
        }
        PersonaTree copy = new PersonaTree(rootCopies);
        copy.setConversationCount(this.conversationCount);
        return copy;
    }
}

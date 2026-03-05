package org.arcos.UserModel;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.ProfileStability;
import org.arcos.UserModel.Models.TreeBranch;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Slf4j
@Component
public class UserObservationTree {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<TreeBranch, List<ObservationLeaf>> branches;
    private final int maxActiveObservations;

    private int conversationCount;
    private ProfileStability profileStability;
    private final Map<TreeBranch, String> summaries;
    private Map<String, Double> heuristicBaselines;

    public UserObservationTree(UserModelProperties properties) {
        this.maxActiveObservations = properties.getMaxActiveObservations();
        this.branches = new EnumMap<>(TreeBranch.class);
        for (TreeBranch branch : TreeBranch.values()) {
            this.branches.put(branch, new ArrayList<>());
        }
        this.conversationCount = 0;
        this.profileStability = ProfileStability.LOW;
        this.summaries = new EnumMap<>(TreeBranch.class);
        this.heuristicBaselines = new HashMap<>();
    }

    public void addLeaf(ObservationLeaf leaf) {
        lock.writeLock().lock();
        try {
            int totalActive = getTotalActiveLeavesCount();
            if (totalActive >= maxActiveObservations) {
                log.warn("Max active observations reached ({}), rejecting leaf: {}", maxActiveObservations, leaf.getText());
                return;
            }
            branches.get(leaf.getBranch()).add(leaf);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<ObservationLeaf> findLeafById(String id) {
        lock.readLock().lock();
        try {
            return branches.values().stream()
                    .flatMap(List::stream)
                    .filter(leaf -> leaf.getId().equals(id))
                    .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<ObservationLeaf> getActiveLeaves(TreeBranch branch) {
        lock.readLock().lock();
        try {
            return new ArrayList<>(branches.get(branch));
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<ObservationLeaf> getAllActiveLeaves() {
        lock.readLock().lock();
        try {
            return branches.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean removeLeaf(String id) {
        lock.writeLock().lock();
        try {
            for (List<ObservationLeaf> leaves : branches.values()) {
                if (leaves.removeIf(leaf -> leaf.getId().equals(id))) {
                    return true;
                }
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getConversationCount() {
        lock.readLock().lock();
        try {
            return conversationCount;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setConversationCount(int count) {
        lock.writeLock().lock();
        try {
            this.conversationCount = count;
            this.profileStability = ProfileStability.fromConversationCount(count);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void incrementConversationCount() {
        lock.writeLock().lock();
        try {
            this.conversationCount++;
            this.profileStability = ProfileStability.fromConversationCount(this.conversationCount);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ProfileStability getProfileStability() {
        lock.readLock().lock();
        try {
            return profileStability;
        } finally {
            lock.readLock().unlock();
        }
    }

    public String getSummary(TreeBranch branch) {
        lock.readLock().lock();
        try {
            return summaries.get(branch);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setSummary(TreeBranch branch, String summary) {
        lock.writeLock().lock();
        try {
            summaries.put(branch, summary);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<TreeBranch, String> getAllSummaries() {
        lock.readLock().lock();
        try {
            return new EnumMap<>(summaries);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, Double> getHeuristicBaselines() {
        lock.readLock().lock();
        try {
            return new HashMap<>(heuristicBaselines);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setHeuristicBaselines(Map<String, Double> baselines) {
        lock.writeLock().lock();
        try {
            this.heuristicBaselines = new HashMap<>(baselines);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private int getTotalActiveLeavesCount() {
        return branches.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * Replaces all data in the tree (used by persistence load).
     */
    public void replaceAll(Map<TreeBranch, List<ObservationLeaf>> newBranches,
                           int newConversationCount,
                           Map<TreeBranch, String> newSummaries,
                           Map<String, Double> newBaselines) {
        lock.writeLock().lock();
        try {
            for (TreeBranch branch : TreeBranch.values()) {
                branches.get(branch).clear();
                List<ObservationLeaf> incoming = newBranches.get(branch);
                if (incoming != null) {
                    branches.get(branch).addAll(incoming);
                }
            }
            this.conversationCount = newConversationCount;
            this.profileStability = ProfileStability.fromConversationCount(newConversationCount);
            this.summaries.clear();
            if (newSummaries != null) {
                this.summaries.putAll(newSummaries);
            }
            this.heuristicBaselines = newBaselines != null ? new HashMap<>(newBaselines) : new HashMap<>();
        } finally {
            lock.writeLock().unlock();
        }
    }
}

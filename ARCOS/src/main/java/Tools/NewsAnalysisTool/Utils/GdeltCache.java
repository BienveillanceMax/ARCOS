package Tools.NewsAnalysisTool.Utils;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Cache simple pour les requêtes GDELT
 */
@Component
public class GdeltCache
{

    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Duration cacheExpiration = Duration.ofMinutes(30);

    public void put(String key, Object value) {
        cache.put(key, new GdeltCache.CacheEntry(value, LocalDateTime.now()));
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        GdeltCache.CacheEntry entry = cache.get(key);

        if (entry == null || isExpired(entry)) {
            cache.remove(key);
            return null;
        }

        return (T) entry.getValue();
    }

    public void invalidate(String key) {
        cache.remove(key);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        cleanExpiredEntries();
        return cache.size();
    }

    private boolean isExpired(GdeltCache.CacheEntry entry) {
        return Duration.between(entry.getCreationTime(), LocalDateTime.now())
                .compareTo(cacheExpiration) > 0;
    }

    private void cleanExpiredEntries() {
        cache.entrySet().removeIf(entry -> isExpired(entry.getValue()));
    }

    private static class CacheEntry
    {
        private final Object value;
        private final LocalDateTime creationTime;

        public CacheEntry(Object value, LocalDateTime creationTime) {
            this.value = value;
            this.creationTime = creationTime;
        }

        public Object getValue() {
            return value;
        }

        public LocalDateTime getCreationTime() {
            return creationTime;
        }
    }
}

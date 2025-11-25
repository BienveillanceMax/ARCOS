package EventBus;

import EventBus.Events.Event;
import EventBus.persistence.PersistentEvent;
import EventBus.persistence.PersistentEventRepository;
import com.google.gson.Gson;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class PersistentEventQueue {

    private final PersistentEventRepository repository;
    private final Gson gson;
    private final Object lock = new Object();

    public PersistentEventQueue(PersistentEventRepository repository, Gson gson) {
        this.repository = repository;
        this.gson = gson;
    }

    @Transactional
    public boolean offer(Event<?> event) {
        PersistentEvent persistentEvent = new PersistentEvent();
        persistentEvent.setEventType(event.getType());
        persistentEvent.setPayload(gson.toJson(event.getPayload()));
        repository.save(persistentEvent);
        synchronized (lock) {
            lock.notifyAll();
        }
        return true;
    }

    @Transactional
    public Event<?> take() throws InterruptedException {
        while (true) {
            Optional<PersistentEvent> oldestEventOpt = repository.findFirstByOrderByCreatedAtAsc();
            if (oldestEventOpt.isPresent()) {
                PersistentEvent persistentEvent = oldestEventOpt.get();
                try {
                    Event<?> event = deserializeEvent(persistentEvent);
                    repository.delete(persistentEvent);
                    return event;
                } catch (ClassNotFoundException e) {
                    // Handle error: log, delete the problematic event, and continue
                    repository.delete(persistentEvent);
                }
            }
            synchronized (lock) {
                lock.wait();
            }
        }
    }

    private Event<?> deserializeEvent(PersistentEvent persistentEvent) throws ClassNotFoundException {
        Class<?> payloadType = Class.forName(persistentEvent.getEventType().getPayloadType().getName());
        Object payload = gson.fromJson(persistentEvent.getPayload(), payloadType);
        return new Event<>(persistentEvent.getEventType(), payload, "PersistentEventQueue");
    }
}

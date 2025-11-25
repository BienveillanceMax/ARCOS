package EventBus;

import EventBus.Events.Event;
import EventBus.Events.EventType;
import EventBus.persistence.PersistentEvent;
import EventBus.persistence.PersistentEventRepository;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PersistentEventQueueTest {

    @Mock
    private PersistentEventRepository repository;

    @Mock
    private Gson gson;

    @InjectMocks
    private PersistentEventQueue persistentEventQueue;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testOfferAndTake() throws InterruptedException {
        // Given
        Event<String> event = new Event<>(EventType.WAKEWORD, "test payload", "test");
        PersistentEvent persistentEvent = new PersistentEvent();
        persistentEvent.setEventType(EventType.WAKEWORD);
        persistentEvent.setPayload("\"test payload\"");

        when(gson.toJson(any())).thenReturn("\"test payload\"");
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(persistentEvent));
        when(gson.fromJson(anyString(), eq(String.class))).thenReturn("test payload");

        // When
        persistentEventQueue.offer(event);

        // Then
        verify(repository, times(1)).save(any(PersistentEvent.class));

        // When
        Event<?> retrievedEvent = persistentEventQueue.take();

        // Then
        assertEquals(EventType.WAKEWORD, retrievedEvent.getType());
        assertEquals("test payload", retrievedEvent.getPayload());
        verify(repository, times(1)).delete(any(PersistentEvent.class));
    }
}

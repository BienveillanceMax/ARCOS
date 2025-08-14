package com.arcos.bus;

import com.arcos.events.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Consumer;

public class EventBus {
    private static final EventBus INSTANCE = new EventBus();
    private final PriorityBlockingQueue<Event> eventQueue = new PriorityBlockingQueue<>();
    private final Map<Class<? extends Event>, List<Consumer<Event>>> listeners = new ConcurrentHashMap<>();
    private final Thread eventProcessor;

    private EventBus() {
        this.eventProcessor = new Thread(this::processEvents);
        this.eventProcessor.setDaemon(true);
    }

    public static EventBus getInstance() {
        return INSTANCE;
    }

    public void publish(Event event) {
        eventQueue.put(event);
    }

    @SuppressWarnings("unchecked")
    public <T extends Event> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add((Consumer<Event>) listener);
    }

    public void start() {
        eventProcessor.start();
    }

    private void processEvents() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Event event = eventQueue.take();
                listeners.getOrDefault(event.getClass(), List.of()).forEach(listener -> listener.accept(event));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

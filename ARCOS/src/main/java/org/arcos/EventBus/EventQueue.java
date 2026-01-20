package org.arcos.EventBus;


import org.arcos.EventBus.Events.Event;
import org.springframework.stereotype.Component;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Queue thread safe pour les événements avec support des priorités
 */
@Component
public class EventQueue {
    private static final Logger logger = Logger.getLogger(EventQueue.class.getName());

    private final PriorityBlockingQueue<Event<?>> queue;
    private final AtomicInteger eventCount;
    private final int maxCapacity;

    public EventQueue() {
        this(10000); // Capacité par défaut
    }

    public EventQueue(int maxCapacity) {
        this.queue = new PriorityBlockingQueue<>(100);
        this.eventCount = new AtomicInteger(0);
        this.maxCapacity = maxCapacity;
    }

    /**
     * Ajoute un événement à la queue
     * @param event L'événement à ajouter
     * @return true si l'événement a été ajouté, false sinon
     */
    public boolean offer(Event<?> event) {
        if (eventCount.get() >= maxCapacity) {
            logger.warning("Queue pleine, événement rejeté: " + event);
            return false;
        }

        boolean added = queue.offer(event);
        if (added) {
            eventCount.incrementAndGet();
            logger.info("Événement ajouté à la queue: " + event);
        }
        return added;
    }

    /**
     * Récupère et retire le prochain événement (bloquant)
     * @return Le prochain événement selon la priorité
     * @throws InterruptedException si le thread est interrompu
     */
    public Event<?> take() throws InterruptedException {
        Event<?> event = queue.take();
        eventCount.decrementAndGet();
        logger.fine("Événement retiré de la queue: " + event);
        return event;
    }

    /**
     * Récupère et retire le prochain événement avec timeout
     * @param timeout Timeout en millisecondes
     * @return Le prochain événement ou null si timeout
     */
    public Event<?> poll(long timeout) throws InterruptedException {
        Event<?> event = queue.poll(timeout, TimeUnit.MILLISECONDS);
        if (event != null) {
            eventCount.decrementAndGet();
            logger.fine("Événement retiré de la queue (avec timeout): " + event);
        }
        return event;
    }

    /**
     * Récupère le prochain événement sans le retirer
     */
    public Event<?> peek() {
        return queue.peek();
    }

    /**
     * Vide la queue
     */
    public void clear() {
        int cleared = queue.size();
        queue.clear();
        eventCount.set(0);
        logger.info("Queue vidée, " + cleared + " événements supprimés");
    }

    // Getters pour monitoring
    public int size() {
        return eventCount.get();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public double getLoadPercentage() {
        return (double) eventCount.get() / maxCapacity * 100;
    }
}

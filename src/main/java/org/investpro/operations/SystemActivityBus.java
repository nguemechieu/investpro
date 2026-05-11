package org.investpro.operations;

import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Thread-safe event bus for system activity monitoring.
 * Maintains a bounded history of recent events and notifies listeners of new
 * events.
 */
@Slf4j
public class SystemActivityBus {

    private static final int DEFAULT_HISTORY_SIZE = 1000;
    private static volatile SystemActivityBus instance;

    private final List<SystemActivityEvent> eventHistory;
    private final List<Consumer<SystemActivityEvent>> listeners;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final int maxHistorySize;

    private SystemActivityBus(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
        this.eventHistory = new LinkedList<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Get the singleton instance of SystemActivityBus
     */
    public static SystemActivityBus getInstance() {
        if (instance == null) {
            synchronized (SystemActivityBus.class) {
                if (instance == null) {
                    instance = new SystemActivityBus(DEFAULT_HISTORY_SIZE);
                }
            }
        }
        return instance;
    }

    /**
     * Initialize with custom history size
     */
    public static void initialize(int historySize) {
        if (instance == null) {
            synchronized (SystemActivityBus.class) {
                if (instance == null) {
                    instance = new SystemActivityBus(historySize);
                }
            }
        }
    }

    /**
     * Record a system activity event
     */
    public void recordEvent(SystemActivityEvent event) {
        lock.writeLock().lock();
        try {
            eventHistory.add(event);
            // Remove oldest events if exceeding max size
            while (eventHistory.size() > maxHistorySize) {
                eventHistory.removeFirst();
            }
        } finally {
            lock.writeLock().unlock();
        }

        // Notify listeners (async to avoid blocking)
        notifyListeners(event);
    }

    /**
     * Convenience method to record an event
     */
    public void record(SystemActivityEvent.Component component,
            SystemActivityEvent.Severity severity,
            String eventType,
            String message) {
        recordEvent(new SystemActivityEvent(component, severity, eventType, message));
    }

    /**
     * Convenience method to record an event with correlation ID
     */
    public void record(SystemActivityEvent.Component component,
            SystemActivityEvent.Severity severity,
            String eventType,
            String message,
            String correlationId) {
        recordEvent(new SystemActivityEvent(component, severity, eventType, message, correlationId));
    }

    /**
     * Get all recorded events in chronological order
     */
    public List<SystemActivityEvent> getAllEvents() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(eventHistory);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get recent events (last N)
     */
    public List<SystemActivityEvent> getRecentEvents(int count) {
        lock.readLock().lock();
        try {
            int startIndex = Math.max(0, eventHistory.size() - count);
            return new ArrayList<>(eventHistory.subList(startIndex, eventHistory.size()));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get events filtered by component
     */
    public List<SystemActivityEvent> getEventsByComponent(SystemActivityEvent.Component component) {
        lock.readLock().lock();
        try {
            return eventHistory.stream()
                    .filter(e -> e.getComponent() == component)
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get events filtered by severity
     */
    public List<SystemActivityEvent> getEventsBySeverity(SystemActivityEvent.Severity severity) {
        lock.readLock().lock();
        try {
            return eventHistory.stream()
                    .filter(e -> e.getSeverity() == severity)
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get events filtered by multiple criteria
     */
    public List<SystemActivityEvent> getEvents(SystemActivityEvent.Component component,
            SystemActivityEvent.Severity severity) {
        lock.readLock().lock();
        try {
            return eventHistory.stream()
                    .filter(e -> e.getComponent() == component && e.getSeverity() == severity)
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the most recent event of a specific type
     */
    public Optional<SystemActivityEvent> getLastEvent(String eventType) {
        lock.readLock().lock();
        try {
            return eventHistory.stream()
                    .filter(e -> e.getEventType().equals(eventType))
                    .reduce((first, second) -> second);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Register a listener for new events
     */
    public void subscribe(Consumer<SystemActivityEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Unregister a listener
     */
    public void unsubscribe(Consumer<SystemActivityEvent> listener) {
        listeners.remove(listener);
    }

    /**
     * Clear all event history
     */
    public void clearHistory() {
        lock.writeLock().lock();
        try {
            eventHistory.clear();
            log.info("SystemActivityBus history cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get current history size
     */
    public int getHistorySize() {
        lock.readLock().lock();
        try {
            return eventHistory.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get event count by severity
     */
    public int getErrorCount() {
        return (int) getEventsBySeverity(SystemActivityEvent.Severity.ERROR).size();
    }

    /**
     * Get warning count
     */
    public int getWarningCount() {
        return (int) getEventsBySeverity(SystemActivityEvent.Severity.WARN).size();
    }

    /**
     * Get critical error count
     */
    public int getCriticalCount() {
        return (int) getEventsBySeverity(SystemActivityEvent.Severity.CRITICAL).size();
    }

    private void notifyListeners(SystemActivityEvent event) {
        listeners.forEach(listener -> {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error notifying listener", e);
            }
        });
    }
}

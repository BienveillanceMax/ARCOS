package com.arcos.events;

public class QueryTranscribedEvent extends Event {
    private final String query;

    public QueryTranscribedEvent(String query) {
        super(Priority.MEDIUM);
        this.query = query;
    }

    public String getQuery() {
        return query;
    }
}

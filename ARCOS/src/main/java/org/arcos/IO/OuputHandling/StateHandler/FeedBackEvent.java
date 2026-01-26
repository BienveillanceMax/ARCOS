package org.arcos.IO.OuputHandling.StateHandler;

public class FeedBackEvent
{
    public FeedBackEvent(UXEventType UXEventType) {
        this.UXEventType = UXEventType;
    }

    private final UXEventType UXEventType;

    public UXEventType getEventType() {
        return UXEventType;
    }
}
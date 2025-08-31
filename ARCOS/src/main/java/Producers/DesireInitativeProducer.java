package Producers;

import Memory.LongTermMemory.Models.DesireEntry;

public class DesireInitativeProducer
{
    public DesireEntry.Status initDesireInitiative(DesireEntry createdDesire) {
        return DesireEntry.Status.SATISFIED;
    }
}

package Producers;

import Memory.LongTermMemory.Models.DesireEntry;
import org.springframework.stereotype.Component;

@Component
public class DesireInitativeProducer
{
    public DesireEntry.Status initDesireInitiative(DesireEntry createdDesire) {
        return DesireEntry.Status.SATISFIED;
    }
}

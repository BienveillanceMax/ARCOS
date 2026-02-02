package org.arcos.IntegrationTests.Services;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Personality.Opinions.OpinionService;
import org.arcos.common.utils.ObjectCreationUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class OpinionServiceIT
{
    @Autowired
    private OpinionService opinionService;

    @Test
    void addValidMemory_ShouldReturnValidOpinionEntry() {
        //given

        MemoryEntry memoryEntry = ObjectCreationUtils.createMemoryEntry();

        //when

        List<OpinionEntry> results = opinionService.processInteraction(memoryEntry);

        //then

        assertNotNull(results);

    }
}

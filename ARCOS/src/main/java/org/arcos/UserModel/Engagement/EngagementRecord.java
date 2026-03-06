package org.arcos.UserModel.Engagement;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EngagementRecord {

    private Instant timestamp;
    private int messageCount;
}

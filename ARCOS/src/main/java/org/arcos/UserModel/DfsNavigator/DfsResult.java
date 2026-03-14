package org.arcos.UserModel.DfsNavigator;

import java.util.List;
import java.util.Map;

public record DfsResult(
    Map<String, String> relevantLeaves,
    List<String> selectedL1Branches,
    List<String> selectedL2Branches,
    long latencyMs
) {}

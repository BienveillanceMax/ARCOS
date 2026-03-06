package org.arcos.UserModel.Heuristics;

import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.SignificantChange;

import java.util.List;

public interface HeuristicLeafProvider {

    List<ObservationLeaf> generateLeaves(List<SignificantChange> changes, int conversationCount);

    boolean isAvailable();
}

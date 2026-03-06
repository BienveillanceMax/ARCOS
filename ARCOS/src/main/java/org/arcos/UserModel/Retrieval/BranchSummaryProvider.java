package org.arcos.UserModel.Retrieval;

import org.arcos.UserModel.Models.TreeBranch;

public interface BranchSummaryProvider {

    String rebuild(TreeBranch branch);

    boolean isAvailable();
}

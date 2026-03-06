package org.arcos.UserModel.Retrieval;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.Models.TreeBranch;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Primary
public class BranchSummaryProviderChain implements BranchSummaryProvider {

    private final BranchSummaryBuilder builder;
    private final BranchSummaryGenerator generator;

    public BranchSummaryProviderChain(BranchSummaryBuilder builder,
                                      @Nullable BranchSummaryGenerator generator) {
        this.builder = builder;
        this.generator = generator;
    }

    @Override
    public String rebuild(TreeBranch branch) {
        if (generator != null && generator.isAvailable()) {
            try {
                String result = generator.rebuild(branch);
                if (result != null) {
                    return result;
                }
            } catch (Exception e) {
                log.warn("BranchSummaryGenerator failed for {}, falling back to builder: {}",
                        branch, e.getMessage());
            }
        }
        return builder.rebuild(branch);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}

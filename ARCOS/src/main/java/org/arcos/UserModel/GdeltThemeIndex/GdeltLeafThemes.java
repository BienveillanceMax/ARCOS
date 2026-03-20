package org.arcos.UserModel.GdeltThemeIndex;

import java.time.Instant;
import java.util.List;

public record GdeltLeafThemes(
    String leafPath,
    String sourceHash,
    List<GdeltKeyword> keywords,
    Instant indexedAt
) {}

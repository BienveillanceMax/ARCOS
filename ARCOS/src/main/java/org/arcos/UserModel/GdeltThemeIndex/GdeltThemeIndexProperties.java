package org.arcos.UserModel.GdeltThemeIndex;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@Data
@ConfigurationProperties(prefix = "arcos.user-model.gdelt-theme-index")
public class GdeltThemeIndexProperties {

    private String path = "data/gdelt-theme-index.json";
    private String extractorModel = "pierrewagniart/memlistener:q4_k_m";
    private int extractorMaxTokens = 128;
    private double extractorTemperature = 0.2;
    private long extractorTimeoutMs = 30000;
    private int maxKeywordsPerLeaf = 5;

    /**
     * Specific leaf paths that reliably produce actionable GDELT search keywords.
     * Only HIGH and MEDIUM news-value leaves — excludes low-value leaves like
     * Soft_Skills, Employment_Type, Aesthetic_Style, Language_Use, etc.
     */
    public static final Set<String> RELEVANT_LEAF_PATHS = Set.of(
            // HIGH: direct news-topic producers
            "4_Identity_Characteristics.Life_Beliefs.Political_Stance",
            "4_Identity_Characteristics.Life_Beliefs.Public_Interest_Engagement.Voluntary_Service",
            "4_Identity_Characteristics.Life_Beliefs.Public_Interest_Engagement.Social_Contribution",
            "5_Behavioral_Characteristics.Interests_and_Skills.Interests_and_Hobbies",
            "5_Behavioral_Characteristics.Social_Engagement.Public_Participation",
            "5_Behavioral_Characteristics.Social_Engagement.Social_Issues_of_Concern",
            "4_Identity_Characteristics.Social_Identity.Occupational_Role.Industry",
            // MEDIUM: contextual news producers
            "4_Identity_Characteristics.Life_Beliefs.Values",
            "4_Identity_Characteristics.Life_Beliefs.Worldview",
            "4_Identity_Characteristics.Motivations_and_Goals.Aspirations",
            "5_Behavioral_Characteristics.Interests_and_Skills.Professional_Skills",
            "4_Identity_Characteristics.Social_Identity.Occupational_Role.Vocational_Interests",
            "5_Behavioral_Characteristics.Social_Engagement.Mutual_Aid_and_Donations",
            "5_Behavioral_Characteristics.Knowledge_Base.Artistic_Preferences.Music",
            "5_Behavioral_Characteristics.Knowledge_Base.Artistic_Preferences.Film_and_Television",
            "5_Behavioral_Characteristics.Knowledge_Base.Artistic_Preferences.Literature"
    );
}

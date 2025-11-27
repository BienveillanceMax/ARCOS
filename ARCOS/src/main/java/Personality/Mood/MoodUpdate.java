package Personality.Mood;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MoodUpdate {
    @JsonProperty("delta_pleasure")
    public double deltaPleasure;

    @JsonProperty("delta_arousal")
    public double deltaArousal;

    @JsonProperty("delta_dominance")
    public double deltaDominance;

    @JsonProperty("reasoning")
    public String reasoning;
}

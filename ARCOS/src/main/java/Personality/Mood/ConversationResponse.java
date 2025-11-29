package Personality.Mood;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationResponse {
    @JsonProperty("response")
    public String response;

    @JsonProperty("mood_update")
    public MoodUpdate moodUpdate;
}

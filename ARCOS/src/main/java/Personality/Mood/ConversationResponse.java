package Personality.Mood;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ConversationResponse {
    @JsonProperty("response")
    public String response;

    @JsonProperty("mood_update")
    public MoodUpdate moodUpdate;
}

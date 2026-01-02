package IO.OuputHandling.StateHandler.AudioCue;

import org.springframework.stereotype.Component;

@Component
public class AudioCueFeedbackHandler
{
    private final AudioCueEngine audioCueEngine;

    public AudioCueFeedbackHandler(AudioCueEngine audioCueEngine) {
        this.audioCueEngine = audioCueEngine;
    }

    //When addressed after a long silence (>4 min) a sound should play to notify the user that the assistant is listening.
    public void playWakeUpSoundHard() {
        //TODO
    }

    //When addressed shortly, should only play a soft sound
    public void playWakeUpSoundSoft() {
        //TODO
    }

    //When starting a long task (ie deep research), a sound should play to inform the user of the begining of a long task.
    public void playLongTaskStartedSound() {
        //TODO
    }

    //At the end of a long task, should play a sound to signal work is done
    public void playLongTaskFinishedSound() {
        //TODO
    }

    //When the system decide to execute an initiative, the user should be notified
    public void playInitiativeStartedSound() {
        //TODO
    }

    //When the system successfully executed an initiative, the user should be notified
    public void playInitiativeEndedSound() {
        //TODO
    }

    //When the system crash/encounter an error, a sound should be played to inform the user
    public void playFailureSound() {
        //TODO
    }
}

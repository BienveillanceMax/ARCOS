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
        audioCueEngine.play("wakeup_hard.wav");
    }

    //When addressed shortly, should only play a soft sound
    public void playWakeUpSoundSoft() {
        audioCueEngine.play("wakeup_soft.wav");
    }

    //When starting a long task (ie deep research), a sound should play to inform the user of the begining of a long task.
    public void playLongTaskStartedSound() {
        audioCueEngine.play("longtask_start.wav");
    }

    //At the end of a long task, should play a sound to signal work is done
    public void playLongTaskFinishedSound() {
        audioCueEngine.play("longtask_end.wav");
    }

    //When the system decide to execute an initiative, the user should be notified
    public void playInitiativeStartedSound() {
        audioCueEngine.play("initiative_start.wav");
    }

    //When the system successfully executed an initiative, the user should be notified
    public void playInitiativeEndedSound() {
        audioCueEngine.play("initiative_end.wav");
    }

    //When the system crash/encounter an error, a sound should be played to inform the user
    public void playFailureSound() {
        audioCueEngine.play("failure.wav");
    }
}

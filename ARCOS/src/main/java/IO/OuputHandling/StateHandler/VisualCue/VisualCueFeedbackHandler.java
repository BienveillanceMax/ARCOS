package IO.OuputHandling.StateHandler.VisualCue;

import org.springframework.stereotype.Component;

@Component
public class VisualCueFeedbackHandler
{
    private final LedEngine ledEngine;
    private final ScreenDisplayEngine screenDisplayEngine;

    public VisualCueFeedbackHandler(LedEngine ledEngine, ScreenDisplayEngine screenDisplayEngine) {
        this.ledEngine = ledEngine;
        this.screenDisplayEngine = screenDisplayEngine;
    }

    //When addressed after a long silence (>4 min) a led "animation" should play to notify the user that the assistant has heard the wakeword.
    public void playWakeUp() {
        //TODO
    }

    //When starting a long task (ie deep research), a led "animation" should play to inform the user of the begining of a long task.
    public void playLongTaskStarted() {
        //TODO
    }

    //When finishing a long task (ie deep research), a led "animation" should play to inform the user of the end of a long task.
    public void playLongTaskFinished() {
        //TODO
    }

    //When starting an initiative, a led "animation" should play to inform the user of the begining of an initiative.
    public void playInitiativeInProgress() {
        //TODO
    }

    //When an error is detected, a led "animation" should play to inform the use.
    public void playFailureLed() {
        //TODO
    }
}

package org.arcos.IO.OuputHandling.StateHandler;

import org.arcos.IO.OuputHandling.StateHandler.AudioCue.AudioCueFeedbackHandler;
import org.arcos.IO.OuputHandling.StateHandler.VisualCue.VisualCueFeedbackHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CentralFeedBackHandler
{
    private final AudioCueFeedbackHandler audioCueHandler;
    private final VisualCueFeedbackHandler visualCueHandler;

    @Autowired
    public CentralFeedBackHandler(AudioCueFeedbackHandler audioCueHandler, VisualCueFeedbackHandler visualCueHandler) {
        this.audioCueHandler = audioCueHandler;
        this.visualCueHandler = visualCueHandler;
    }

    public void handleFeedBack(FeedBackEvent feedBackEvent) {
        switch (feedBackEvent.getEventType()) {

            case ARCOS_START:
                audioCueHandler.playArcosStart();
            case WAKEUP_LONG:
                audioCueHandler.playWakeUpSoundHard();
                break;
            case WAKEUP_SHORT:
                audioCueHandler.playWakeUpSoundSoft();
                break;
            case LONGTASK_START:
                audioCueHandler.playLongTaskStartedSound();
                break;
            case LONGTASK_END:
                audioCueHandler.playLongTaskFinishedSound();
                break;
            case INITIATIVE_START:
                audioCueHandler.playInitiativeStartedSound();
                break;
            case INITIATIVE_END:
                audioCueHandler.playInitiativeEndedSound();
                break;
            case FAILURE:
                audioCueHandler.playFailureSound();
                break;
        }
    }
}

package IO.OuputHandling.StateHandler;

import IO.OuputHandling.StateHandler.AudioCue.AudioCueFeedbackHandler;
import IO.OuputHandling.StateHandler.VisualCue.VisualCueFeedbackHandler;
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
        //todo
    }
}

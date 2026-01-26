package IO.OuputHandling.StateHandler;

import org.arcos.IO.OuputHandling.StateHandler.AudioCue.AudioCueEngine;
import org.arcos.IO.OuputHandling.StateHandler.AudioCue.AudioCueFeedbackHandler;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.UXEventType;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import org.arcos.IO.OuputHandling.StateHandler.VisualCue.VisualCueFeedbackHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

public class CentralFeedBackHandlerTest {

    private AudioCueEngine audioCueEngine;
    private AudioCueFeedbackHandler audioCueFeedbackHandler;
    private VisualCueFeedbackHandler visualCueFeedbackHandler;
    private CentralFeedBackHandler centralFeedBackHandler;

    @BeforeEach
    public void setUp() {
        audioCueEngine = Mockito.mock(AudioCueEngine.class);
        audioCueFeedbackHandler = new AudioCueFeedbackHandler(audioCueEngine);
        visualCueFeedbackHandler = Mockito.mock(VisualCueFeedbackHandler.class);
        centralFeedBackHandler = new CentralFeedBackHandler(audioCueFeedbackHandler, visualCueFeedbackHandler);
    }

    @Test
    public void testWakeUpLongEvent() {
        FeedBackEvent event = new FeedBackEvent(UXEventType.WAKEUP_LONG);
        centralFeedBackHandler.handleFeedBack(event);
        verify(audioCueEngine, times(1)).play("wakeup_hard.wav");
    }

    @Test
    public void testWakeUpShortEvent() {
        FeedBackEvent event = new FeedBackEvent(UXEventType.WAKEUP_SHORT);
        centralFeedBackHandler.handleFeedBack(event);
        verify(audioCueEngine, times(1)).play("wakeup_soft.wav");
    }

    @Test
    public void testLongTaskStartEvent() {
        FeedBackEvent event = new FeedBackEvent(UXEventType.LONGTASK_START);
        centralFeedBackHandler.handleFeedBack(event);
        verify(audioCueEngine, times(1)).play("longtask_start.wav");
    }

    @Test
    public void testLongTaskEndEvent() {
        FeedBackEvent event = new FeedBackEvent(UXEventType.LONGTASK_END);
        centralFeedBackHandler.handleFeedBack(event);
        verify(audioCueEngine, times(1)).play("longtask_end.wav");
    }

    @Test
    public void testInitiativeStartEvent() {
        FeedBackEvent event = new FeedBackEvent(UXEventType.INITIATIVE_START);
        centralFeedBackHandler.handleFeedBack(event);
        verify(audioCueEngine, times(1)).play("initiative_start.wav");
    }

    @Test
    public void testInitiativeEndEvent() {
        FeedBackEvent event = new FeedBackEvent(UXEventType.INITIATIVE_END);
        centralFeedBackHandler.handleFeedBack(event);
        verify(audioCueEngine, times(1)).play("initiative_end.wav");
    }

    @Test
    public void testFailureEvent() {
        FeedBackEvent event = new FeedBackEvent(UXEventType.FAILURE);
        centralFeedBackHandler.handleFeedBack(event);
        verify(audioCueEngine, times(1)).play("failure.wav");
    }
}

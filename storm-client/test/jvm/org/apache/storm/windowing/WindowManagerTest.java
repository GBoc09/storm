package org.apache.storm.windowing;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@RunWith(value = Parameterized.class)
public class WindowManagerTest {
    private enum LIST_STATE {
        EMPTY,
        NOT_EMPTY
    }
    private LIST_STATE listState;
    private WindowManager<String> windowManager;
    private List<String> expectedExpiredEvent;
    private List<String> eventsToBeAdded;
    private List<String> actualExpiredEvent;
    private int threshold;  // {sizeList-1, size_list, size_list+1}
    private final int TRIGGER_WINDOW = 1;

    /** adding a boolean flag to indicate if the event is watermark or not */
    private boolean isWatermark;
    private WaterMarkEvent waterMarkEvent;

    public WindowManagerTest(WindowManagerTuple windowManagerTuple) {
        this.listState = windowManagerTuple.listState();
        this.threshold = windowManagerTuple.threshold();
        this.isWatermark = windowManagerTuple.isWatermark();
    }
    @Parameterized.Parameters
    public static Collection<WindowManagerTuple> getWindowManagerTuple() {
        List<WindowManagerTuple> windowManagerTuples = new ArrayList<>();

        //windowManagerTuples.add(new WindowManagerTuple(LIST_STATE.EMPTY, -1));
        windowManagerTuples.add(new WindowManagerTuple(LIST_STATE.EMPTY, 1, false));
        windowManagerTuples.add(new WindowManagerTuple(LIST_STATE.NOT_EMPTY, 3, false ));
        windowManagerTuples.add(new WindowManagerTuple(LIST_STATE.NOT_EMPTY, 4, false));
        windowManagerTuples.add(new WindowManagerTuple(LIST_STATE.NOT_EMPTY, 5, false));

        windowManagerTuples.add(new WindowManagerTuple(LIST_STATE.NOT_EMPTY, 5, true));

        return windowManagerTuples;
    }

    private final static class WindowManagerTuple {
        private final LIST_STATE listState;
        private final int threshold;
        private final boolean isWatermark;

        private WindowManagerTuple(LIST_STATE listState, int threshold, boolean isWatermark) {
            this.listState = listState;
            this.threshold = threshold;
            this.isWatermark = isWatermark;
        }
        public LIST_STATE listState(){return listState;}
        public int threshold(){return threshold;}
        public boolean isWatermark(){return isWatermark;}
    }

    @Before
    public void setUp() {
        try{
            switch (listState) {
                case EMPTY:
                    this.eventsToBeAdded = Collections.emptyList();
                    break;
                    case NOT_EMPTY:
                        List<String> eventsList= Arrays.asList("event1", "event2", "event3", "event4");
                        this.eventsToBeAdded = eventsList;
                        break;
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        WindowLifecycleListener<String> listener = mock(WindowLifecycleListener.class);
        if (!isWatermark) {
            if (this.threshold > this.eventsToBeAdded.size()) {
                this.expectedExpiredEvent = Collections.emptyList();
            } else {
                int expiredLenght = this.eventsToBeAdded.size() - this.threshold;
                this.expectedExpiredEvent = new ArrayList<>();
                for (int i = 0; i < expiredLenght; i++) {
                    this.expectedExpiredEvent.add(this.eventsToBeAdded.get(i));
                }
            }
            this.actualExpiredEvent = new ArrayList<>();

            doAnswer((mockedInstance) -> {
                List<String> expiredEvents = mockedInstance.getArgument(0);
                this.actualExpiredEvent.addAll(expiredEvents);
                return null;
            }).when(listener).onExpiry(anyList());
            this.windowManager = new WindowManager<>(listener);
            CountEvictionPolicy<String> evictionPolicy = new CountEvictionPolicy<>(this.threshold);
            CountTriggerPolicy<String> triggerPolicy = new CountTriggerPolicy<>(TRIGGER_WINDOW, this.windowManager, evictionPolicy);
            this.windowManager.setEvictionPolicy(evictionPolicy);
            this.windowManager.setTriggerPolicy(triggerPolicy);
            triggerPolicy.start();
        } else {
            waterMarkEvent = new WaterMarkEvent<>(10);
            this.windowManager = new WindowManager<>(listener);
            CountEvictionPolicy<String> evictionPolicy = new CountEvictionPolicy<>(this.threshold);
            CountTriggerPolicy<String> triggerPolicy = new CountTriggerPolicy<>(TRIGGER_WINDOW, this.windowManager, evictionPolicy);
            this.windowManager.setEvictionPolicy(evictionPolicy);
            this.windowManager.setTriggerPolicy(triggerPolicy);
            triggerPolicy.start();
        }
    }

    @Test
    public void testInteraction() {
        if (this.isWatermark == false) {
            for (String event : this.eventsToBeAdded) {
                System.out.println(event);
                this.windowManager.add(event);
            }
            Assert.assertEquals(this.expectedExpiredEvent, this.actualExpiredEvent);
        } else {
                this.windowManager.add(waterMarkEvent);
                System.out.println(windowManager.queue);
                Assert.assertTrue(windowManager.queue.isEmpty());
        }
    }
}



package org.apache.storm.topology;

import org.apache.storm.Config;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.apache.storm.windowing.TimestampExtractor;
import org.apache.storm.windowing.WaterMarkEventGenerator;
import org.apache.storm.windowing.WindowManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@RunWith(value = Parameterized.class)
public class WindowedBoltExecutorTest {
    private boolean isLate; // to identify a late tuple
    private boolean expectedException;
    private Map<String, Object> configurations;
    private WindowedBoltExecutor executor;
    private WaterMarkEventGenerator waterMarkEventGenerator;

    private enum CONFIG {
        VALID,
        NEW, // with no configuration specified
        NULL,
        LTS, // specified late tuple stream
        INVALID, // set of configuration that can't be together
        WRONG, // wrong initialization
        NO_TIMESTAMP // consider a tuple without any timestamp

    }
    private CONFIG config;

    public WindowedBoltExecutorTest(WindowedBoltExecutorTuple windowedBoltExecutorTuple) {
        this.isLate = windowedBoltExecutorTuple.isLate();
        this.config = windowedBoltExecutorTuple.config();
        this.expectedException = windowedBoltExecutorTuple.expectedException();
    }
    @Parameterized.Parameters
    public static Collection<WindowedBoltExecutorTuple> getWindowedBoltExecutorTuple() {
        List<WindowedBoltExecutorTuple> executorTuples = new ArrayList<>();

        executorTuples.add(new WindowedBoltExecutorTuple(true, CONFIG.VALID, false));
        executorTuples.add(new WindowedBoltExecutorTuple(false, CONFIG.VALID, false));
        executorTuples.add(new WindowedBoltExecutorTuple(true, CONFIG.NEW, true));
        executorTuples.add(new WindowedBoltExecutorTuple(true, CONFIG.NULL, true));

        executorTuples.add(new WindowedBoltExecutorTuple(true, CONFIG.LTS, false));
        executorTuples.add(new WindowedBoltExecutorTuple(false, CONFIG.INVALID, true));

        executorTuples.add(new WindowedBoltExecutorTuple(false, CONFIG.NO_TIMESTAMP, false));

        executorTuples.add(new WindowedBoltExecutorTuple(true, CONFIG.WRONG, true));

        return executorTuples;
    }
    private static final class WindowedBoltExecutorTuple {
        private final boolean isLate;
        private final CONFIG config;
        private final boolean expectedException;

        private WindowedBoltExecutorTuple(boolean isLate,  CONFIG config, boolean expectedException) {
            this.isLate = isLate;
            this.config = config;
            this.expectedException = expectedException;
        }
        public boolean isLate(){return this.isLate;}
        public CONFIG config(){return this.config;}
        public boolean expectedException(){return this.expectedException;}
    }

    @Before
    public void setUp() throws Exception {
        IWindowedBolt bolt = mock(IWindowedBolt.class); // an IWindowedBolt wrapper that does the windowing of tuples
        TimestampExtractor timestampExtractor = mock(TimestampExtractor.class);
        if (config == CONFIG.NO_TIMESTAMP) {
            // we need to simulate this class execution because it is necessary for calling the constructor
            when(bolt.getTimestampExtractor()).thenReturn(null);
        } else {
            // we need to simulate this class execution because it is necessary for calling the constructor
            when(bolt.getTimestampExtractor()).thenReturn(timestampExtractor);
        }
        this.waterMarkEventGenerator = mock(WaterMarkEventGenerator.class);
        when(this.waterMarkEventGenerator.track(any(), anyLong())).thenReturn(!this.isLate); // track method will be called with 2 arguments, it will return isLate

        this.executor = new WindowedBoltExecutor(bolt);
        try{
            this.configurations = new HashMap<>();
            switch (this.config){
                case VALID:
                    this.configurations.put(Config.TOPOLOGY_BOLTS_WINDOW_LENGTH_COUNT, 1); // configure the bolts' window length  as a number of tuple
                    break;
                case NEW:
                    this.configurations = new HashMap<>();
                    break;
                case NULL:
                    this.configurations = null;
                    break;
                case LTS: // configure a specific stream for the late tuple
                    this.configurations.put(Config.TOPOLOGY_BOLTS_LATE_TUPLE_STREAM, "testStream"); // fixed stream for late tuple
                    this.configurations.put(Config.TOPOLOGY_BOLTS_WATERMARK_EVENT_INTERVAL_MS, 10);
                    this.configurations.put(Config.TOPOLOGY_BOLTS_WINDOW_LENGTH_COUNT, 1);
                    break;
                case INVALID: // timeout timer shorter than the window length
                    this.configurations.put(Config.TOPOLOGY_MESSAGE_TIMEOUT_SECS, 10); // max amount of time given to the topology to fully process a message emitted by a spouts, if it isn't acked within this time frame, storm will fail
                    this.configurations.put(Config.TOPOLOGY_BOLTS_WINDOW_LENGTH_DURATION_MS, 10000);
                    this.configurations.put(Config.TOPOLOGY_BOLTS_SLIDING_INTERVAL_DURATION_MS, 5000);
                    break;
                case WRONG: // wrong configuration, verify the wrong initialization
                    this.configurations.put(Config.TOPOLOGY_BOLTS_WINDOW_LENGTH_COUNT, 1);
                    this.configurations.put(Config.TOPOLOGY_BOLTS_LATE_TUPLE_STREAM, "testStream");
                    break;
                case NO_TIMESTAMP:
                    this.configurations.put(Config.TOPOLOGY_BOLTS_WINDOW_LENGTH_COUNT, 1);
                    break;

            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    /** we will try what happens when we add a late tuple  in a valid window, what happens if the tuple is invalid */
    @Test
    public void ackGeneratedTest() {
        Tuple tuple = mock(Tuple.class);
        TopologyContext context = mock(TopologyContext.class);
        OutputCollector collector = mock(OutputCollector.class);
        try{
            if(config == CONFIG.WRONG){
                executor.prepare(this.configurations, context, collector);
                Assert.fail("Wrong confing");
            }
            if (config != CONFIG.NULL && this.configurations.containsKey(Config.TOPOLOGY_BOLTS_LATE_TUPLE_STREAM)){
                Set<String>stream = new HashSet<>();
                stream.add((String) this.configurations.get(Config.TOPOLOGY_BOLTS_LATE_TUPLE_STREAM));
                when(context.getThisStreams()).thenReturn(stream);
            }
            executor.prepare(this.configurations, context, collector);
            this.executor.waterMarkEventGenerator = this.waterMarkEventGenerator;

            this.executor.execute(tuple);
            int wantedNumberOfInvocations;

            if (config == CONFIG.NO_TIMESTAMP){
                wantedNumberOfInvocations = 0;

            }
            if(this.isLate) {
                wantedNumberOfInvocations = 1;
            }else {
                wantedNumberOfInvocations = 0;
            }

            if(this.configurations.containsKey(Config.TOPOLOGY_BOLTS_LATE_TUPLE_STREAM)){
                String stream = (String) this.configurations.get(Config.TOPOLOGY_BOLTS_LATE_TUPLE_STREAM);
                verify(collector, times(1)).emit(stream, Arrays.asList(tuple), new Values(tuple));
            }
            verify(collector, times(wantedNumberOfInvocations)).ack(tuple);


            Assert.assertFalse("An exception should be thrown.", this.expectedException);
        }catch (IllegalArgumentException | NullPointerException e) {
            Assert.assertTrue(this.expectedException);
        }
    }

    @Test
    public void startInvocationTest(){
        IWindowedBolt bolt = mock(IWindowedBolt.class);
        WindowedBoltExecutor spyExecutor = spy(new WindowedBoltExecutor(bolt));
        Map<String, Object> config = new HashMap<>();
        config.put(Config.TOPOLOGY_BOLTS_WINDOW_LENGTH_COUNT, 1);
        spyExecutor.prepare(config, mock(TopologyContext.class), mock(OutputCollector.class));
        verify(spyExecutor, times(1)).start();
    }

    /** verify() to check methods were called with given arguments
     * can use flexible argument matching, or a capture */
}


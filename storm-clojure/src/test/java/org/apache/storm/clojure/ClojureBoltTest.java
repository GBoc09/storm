package org.apache.storm.clojure;

import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;
import org.apache.storm.clojure.ClojureBolt;
import org.apache.storm.generated.StreamInfo;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ClojureBoltTest {

    private List<String> fnSpec;
    private List<String> confSpec;
    private List<Object> params;
    private Map<String, StreamInfo> fields;
    private OutputCollector collector;
    private TopologyContext context;
    private Tuple tuple;

    @BeforeEach
    public void setUp() {
        fnSpec = new ArrayList<>(Arrays.asList("namespace", "function"));
        confSpec = new ArrayList<>(Arrays.asList("namespace", "confFunction"));
        params = new ArrayList<>(Collections.singletonList(new Object()));
        fields = new HashMap<>();
        fields.put("default", new StreamInfo(Arrays.asList("field1", "field2"), false));

        collector = mock(OutputCollector.class);
        context = mock(TopologyContext.class);
        tuple = mock(Tuple.class);
    }

    @Test
    public void testClojureBoltInitialization() {
        ClojureBolt clojureBolt = new ClojureBolt(fnSpec, confSpec, params, fields);
        assertNotNull(clojureBolt);
    }

//    @Test
//    public void testPrepare() {
//        ClojureBolt clojureBolt = new ClojureBolt(fnSpec, confSpec, params, fields);
//        Map<String, Object> topoConf = new HashMap<>();
//        clojureBolt.prepare(topoConf, context, collector);
//        // Verify interactions or further state changes if necessary
//    }

//    @Test
//    public void testExecute() {
//        ClojureBolt clojureBolt = new ClojureBolt(fnSpec, confSpec, params, fields);
//        Map<String, Object> topoConf = new HashMap<>();
//        clojureBolt.prepare(topoConf, context, collector);
//        clojureBolt.execute(tuple);
//        // Verify interactions or further state changes if necessary
//    }
//
//    @Test
//    public void testCleanup() {
//        ClojureBolt clojureBolt = new ClojureBolt(fnSpec, confSpec, params, fields);
//        clojureBolt.cleanup();
//        // Verify interactions or further state changes if necessary
//    }

    @Test
    public void testDeclareOutputFields() {
        ClojureBolt clojureBolt = new ClojureBolt(fnSpec, confSpec, params, fields);
        OutputFieldsDeclarer declarer = mock(OutputFieldsDeclarer.class);
        clojureBolt.declareOutputFields(declarer);
        // Verify that the correct fields are declared
        verify(declarer).declareStream(Mockito.eq("default"), Mockito.eq(false), Mockito.any());
    }

    @Test
    public void testFinishedId() {
        ClojureBolt clojureBolt = new ClojureBolt(fnSpec, confSpec, params, fields);
        Object id = new Object();
        clojureBolt.finishedId(id);
        // Verify interactions or further state changes if necessary
    }

//    @Test
//    public void testGetComponentConfiguration() {
//        ClojureBolt clojureBolt = new ClojureBolt(fnSpec, confSpec, params, fields);
//        Map<String, Object> conf = clojureBolt.getComponentConfiguration();
//        assertNotNull(conf);
//    }
}


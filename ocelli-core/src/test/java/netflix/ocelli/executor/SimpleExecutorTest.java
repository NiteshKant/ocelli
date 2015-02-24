package netflix.ocelli.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import netflix.ocelli.CachingInstanceTransformer;
import netflix.ocelli.FailureDetectingInstanceFactory;
import netflix.ocelli.Instance;
import netflix.ocelli.InstanceCollector;
import netflix.ocelli.MutableInstance;
import netflix.ocelli.client.Behaviors;
import netflix.ocelli.client.Connects;
import netflix.ocelli.client.ManualFailureDetector;
import netflix.ocelli.client.TestClient;
import netflix.ocelli.client.TestClientConnectorFactory;
import netflix.ocelli.functions.Delays;
import netflix.ocelli.loadbalancer.RoundRobinLoadBalancer;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import rx.Observable;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

public class SimpleExecutorTest {
    public static Func1<TestClient, Observable<String>> request(final Integer req) {
        return new Func1<TestClient, Observable<String>>() {
            @Override
            public Observable<String> call(TestClient t1) {
                return Observable.just(t1.id() + "-" + req);
            }
        };
    }
    
    private static final int NUM_HOSTS = 10;
    private static Observable<Instance<TestClient>> source;
    
    private PublishSubject<Instance<TestClient>> hostEvents = PublishSubject.create();
    private TestClientConnectorFactory clientConnector = new TestClientConnectorFactory();
    private ManualFailureDetector failureDetector = new ManualFailureDetector();
    
    @Rule
    public TestName testName = new TestName();
    private RoundRobinLoadBalancer<TestClient> lb;
    
    @BeforeClass
    public static void setup() {
        List<Instance<TestClient>> hosts = new ArrayList<Instance<TestClient>>();
        for (int i = 0; i < NUM_HOSTS; i++) {
            hosts.add(MutableInstance.from(TestClient.create("host-"+i, Connects.immediate(), Behaviors.immediate())));
        }
        
        source = Observable.from(hosts);
    }
    
    @After
    public void afterTest() {
    }
    
    @Test
    public void test() {
        FailureDetectingInstanceFactory<TestClient> factory =
                FailureDetectingInstanceFactory.<TestClient>builder()
                .withQuarantineStrategy(Delays.fixed(1, TimeUnit.SECONDS))
                .withFailureDetector(failureDetector)
                .withClientConnector(clientConnector)
                .build();
    
        this.lb = RoundRobinLoadBalancer.create();
        
        hostEvents
            .map(CachingInstanceTransformer.create(factory))
            .compose(new InstanceCollector<TestClient>())
            .subscribe(lb);  

        Executor<String, String> execution = new SimpleExecutor<TestClient, String, String>(lb, TestClient.func());
        source.subscribe(hostEvents);
        
//        List<String> result = Observable.range(0, 10)
//            .map(new Func1<Integer, Func1<TestClient, Observable<String>>>() {
//                @Override
//                public Func1<TestClient, Observable<String>> call(Integer client) {
//                    return request(client);
//                }
//            })
//            .flatMap(new SimpleExecutionStrategy<TestClient>(lb).<String>asFunction())
//            .toList()
//            .toBlocking()
//            .first();
        
//        System.out.println(result);
    }
}

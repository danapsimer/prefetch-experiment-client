package com.bluesoft.test.prefetch.client;

import com.bluesoft.ws.prefetch.PrefetchExperiment;
import com.bluesoft.ws.prefetch.PrefetchExperimentRequest;
import com.bluesoft.ws.prefetch.PrefetchExperimentResponse;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import javax.annotation.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Test;

/**
 *
 * @author danap
 */
@ContextConfiguration(locations = "classpath:applicationContext.xml")
@Test(suiteName = "com.bluesoft.test", testName = "PrefetchExperimentService")
public class PrefetchExperimentServiceClientTest extends AbstractTestNGSpringContextTests {

  private static final org.slf4j.Logger sLogger = org.slf4j.LoggerFactory.getLogger(PrefetchExperimentServiceClientTest.class);
  private static final int INVOCATION_COUNT = 1000;
  private static final int THREADS = 20;
  private final Random random = new Random(System.currentTimeMillis());

  @Resource(name = "PrefetchExperimentClientEndpointJms")
  private PrefetchExperiment jmsService;
  private final List<Sample> jmsSamples = new ArrayList<Sample>(INVOCATION_COUNT);
  private int jmsSoapFaultCount = 0;
  private long jmsTestStart = -1;

  @Resource(name = "PrefetchExperimentClientEndpointHttp")
  private PrefetchExperiment httpService;
  private final List<Sample> httpSamples = new ArrayList<Sample>(INVOCATION_COUNT);
  private int httpSoapFaultCount = 0;
  private long httpTestStart = -1;

  public static class Sample {

    private long timestamp;
    private boolean successful;
    private Long requestTransportTime;
    private Long requestProcessingTime;
    private Long responseTransportTime;
    private long totalTime;

    public Sample(long testStart,PrefetchExperimentRequest request, PrefetchExperimentResponse response) {
      Date received = new Date();
      timestamp = request.getSent().getTime() - testStart;
      successful = true;
      requestTransportTime = response.getRequestReceived().getTime() - request.getSent().getTime();
      requestProcessingTime = response.getSent().getTime() - response.getRequestReceived().getTime();
      responseTransportTime = received.getTime() - response.getSent().getTime();
      totalTime = received.getTime() - request.getSent().getTime();
    }

    public Sample(long testStart,PrefetchExperimentRequest request) {
      Date received = new Date();
      timestamp = request.getSent().getTime() - testStart;
      totalTime = received.getTime() - request.getSent().getTime();
      successful = false;
      requestTransportTime = requestProcessingTime = responseTransportTime = null;
    }

    public Long getRequestProcessingTime() {
      return requestProcessingTime;
    }

    public Long getRequestTransportTime() {
      return requestTransportTime;
    }

    public Long getResponseTransportTime() {
      return responseTransportTime;
    }

    public long getTotalTime() {
      return totalTime;
    }

    public boolean isSuccessful() {
      return successful;
    }

    public long getTimestamp() {
      return timestamp;
    }

    @Override
    public String toString() {
      return "Sample{timestamp = " + timestamp
              + ",\nrsuccessful=" + successful
              + ",\nrequestTransportTime=" + requestTransportTime
              + ",\nrequestProcessingTime=" + requestProcessingTime
              + ",\nresponseTransportTime=" + responseTransportTime
              + ",\ntotalTime=" + totalTime + '}';
    }
  }

  @Test(invocationCount = INVOCATION_COUNT, threadPoolSize = THREADS)
  public void testPing() {
    if ( jmsTestStart == -1L ) {
      synchronized(this) {
        if ( jmsTestStart == -1L ) {
          jmsTestStart = System.currentTimeMillis();
        }
      }
    }
    PrefetchExperimentRequest request = new PrefetchExperimentRequest();
    request.setMessage("Sending Prefetch Message");
    request.setJunkPayload(randomString(100 + random.nextInt(100)));
    request.setSent(new Date());
    PrefetchExperimentResponse response = null;
    Sample sample;
    try {
      response = jmsService.ping(request);
      assert response != null;
      sample = new Sample(jmsTestStart, request, response);
      assert response.getMessage().equals("Sending Prefetch Message");

    } catch (Exception ex) {
      synchronized (this) {
        jmsSoapFaultCount += 1;
      }
      sample = new Sample(jmsTestStart, request);
    }
    sLogger.info("sample = {}", sample);
    synchronized (jmsSamples) {
      jmsSamples.add(sample);
    }
  }

  @Test(invocationCount = INVOCATION_COUNT, threadPoolSize = THREADS)
  public void testHttpPing() {
    if ( httpTestStart == -1L ) {
      synchronized(this) {
        if ( httpTestStart == -1L ) {
          httpTestStart = System.currentTimeMillis();
        }
      }
    }
    PrefetchExperimentRequest request = new PrefetchExperimentRequest();
    request.setMessage("Sending Prefetch Message");
    request.setJunkPayload(randomString(100 + random.nextInt(100)));
    request.setSent(new Date());
    PrefetchExperimentResponse response = null;
    Sample sample;
    try {
      response = httpService.ping(request);
      assert response != null;
      sample = new Sample(httpTestStart, request, response);
      assert response.getMessage().equals("Sending Prefetch Message");
    } catch (Exception ex) {
      sLogger.debug("SoapFault: ", ex);
      synchronized (this) {
        httpSoapFaultCount += 1;
      }
      sample = new Sample(httpTestStart, request);
    }
    sLogger.info("sample = {}", sample);
    synchronized (httpSamples) {
      httpSamples.add(sample);
    }
  }

  @AfterSuite
  public void printResults() {
    sLogger.info("JMS Results:");
    printResultsToLog(jmsSamples,jmsSoapFaultCount);
    sLogger.info("HTTP Results:");
    printResultsToLog(httpSamples,httpSoapFaultCount);
  }

  private void printResultsToLog(List<Sample> samples,int soapFaultCount) {
    sLogger.info("---");
    sLogger.info("TIMESTAMP,SUCCESS,TOTAL,REQ_TRANSPORT,REQ_PROCESSING,RSP_TRANSPORT");
    for (Sample sample : samples) {
      sLogger.info("{},{},{},{},{},{}", new Object[]{
                sample.getTimestamp(), sample.isSuccessful(),
                sample.getTotalTime(), sample.getRequestTransportTime(),
                sample.getRequestProcessingTime(), sample.getResponseTransportTime()
              });
    }
    sLogger.info("---");
    sLogger.info("SOAP Fault Count = {}", soapFaultCount);
  }

  private String randomString(int length) {
    StringBuilder string = new StringBuilder(length);
    for (int c = 0; c < length; c++) {
      string.append((char) (32 + random.nextInt(95)));
    }
    return string.toString();
  }
}

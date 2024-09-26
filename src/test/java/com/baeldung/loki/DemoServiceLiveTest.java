package com.baeldung.loki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(classes = DemoService.class)
public class DemoServiceLiveTest {

    /**
     * This test assumes that loki service is already up.
     * For more details please check section #2 Running Loki and Grafana Service
     * Which spin up Loki server using docker-compose
     */
    @Test
    public void givenLokiContainerRunning_whenDemoServiceInvoked_thenLokiAppenderCollectLogs()
            throws JsonProcessingException, InterruptedException {
        /*
         * Let’s conduct a live test by spinning up Grafana and Loki containers, and
         * then executing the service method to push the logs to Loki. Afterward, we’ll
         * query Loki using the HTTP API to confirm if the log was indeed pushed. For
         * spinning up Grafana and Loki containers, please see the earlier section.
         * 
         * First, let’s execute the DemoService.log() method, which will call
         * Logger.info(). This sends a message using the loki-logback-appender, which
         * Loki will collect:
         */
        DemoService service = new DemoService();
        service.log();
        /*
         * Second, we’ll create a request for invoking the REST endpoint provided by the
         * Loki HTTP API. This GET API accepts query parameters representing the query,
         * start time, and end time. We’ll add those parameters as part of our request
         * object:
         */
        String baseUrl = "http://localhost:3100/loki/api/v1/query_range";
        // Set up query parameters
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String query = "{level=\"INFO\"} |= `DemoService.log invoked`";

        // Get current time in UTC
        LocalDateTime currentDateTime = LocalDateTime.now(ZoneOffset.UTC);
        String currentTimeUtc = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));

        LocalDateTime tenMinsAgo = currentDateTime.minusMinutes(10);
        String startTimeUtc = tenMinsAgo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));

        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("query", query)
                .queryParam("start", startTimeUtc)
                .queryParam("end", currentTimeUtc)
                .build()
                .toUri();
        // Next, let’s use the request object to execute REST request:
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers),
                String.class);
        /*
         * Now we need to process the response and extract the log messages we’re
         * interested in. We’ll use an ObjectMapper to read the JSON response and
         * extract the log messages:
         */
        List<String> messages = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        assertEquals(response.getStatusCode(), HttpStatus.OK);

        String responseBody = response.getBody();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        JsonNode result = jsonNode.get("data")
                .get("result")
                .get(0)
                .get("values");
        result.iterator()
                .forEachRemaining(e -> {
                    Iterator<JsonNode> elements = e.elements();
                    elements.forEachRemaining(f -> messages.add(f.toString()));
                });
        // Finally, let’s assert that the messages we receive in the response contain
        // the message that was logged by the DemoService:
        String expected = "DemoService.log invoked";
        assertThat(messages).anyMatch(e -> e.contains(expected));
    }
}
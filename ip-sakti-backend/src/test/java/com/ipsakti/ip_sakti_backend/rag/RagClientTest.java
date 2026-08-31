package com.ipsakti.ip_sakti_backend.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadGateway;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ipsakti.ip_sakti_backend.exception.RagClientException;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAnswerSource;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskRequest;
import com.ipsakti.ip_sakti_backend.rag.dto.RagAskResponse;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RagClientTest {

    @Test
    void sendsTypedRequestAndReadsGroundedResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://rag.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RagClient client = new RagClient(builder.build());

        server.expect(requestTo("http://rag.test/api/v1/ask"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"question\":\"What is a trademark?\"}"))
                .andRespond(withSuccess("""
                        {
                          "answer": "Grounded answer",
                          "confidence": 0.91,
                          "abstained": false,
                          "citations": [{"document":"Trade Marks Act","document_id":"IND-TM-ACT-1999","page":12,"section":"Section 18","authority":"IP India","source_url":"https://example.invalid","chunk_id":"chunk-1"}],
                          "sources": [{"document_id":"IND-TM-ACT-1999","score":0.94}]
                        }
                        """, MediaType.APPLICATION_JSON));

        RagAskResponse response = client.ask(new RagAskRequest("What is a trademark?", null, null, null));

        assertThat(response.answer()).isEqualTo("Grounded answer");
        assertThat(response.answerSource()).isEqualTo(RagAnswerSource.RAG_GROUNDED);
        server.verify();
    }

    @Test
    void classifiesAbstainedResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://rag.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RagClient client = new RagClient(builder.build());

        server.expect(requestTo("http://rag.test/api/v1/ask"))
                .andRespond(withSuccess("""
                        {"answer":"Not enough evidence","confidence":0.18,"abstained":true,"citations":[],"sources":[]}
                        """, MediaType.APPLICATION_JSON));

        RagAskResponse response = client.ask(new RagAskRequest("Unsupported section?", null, null, null));

        assertThat(response.answerSource()).isEqualTo(RagAnswerSource.ABSTAINED);
    }

    @Test
    void classifiesGeneralFallbackResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://rag.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RagClient client = new RagClient(builder.build());

        server.expect(requestTo("http://rag.test/api/v1/ask"))
                .andRespond(withSuccess("""
                        {"answer":"General fallback","confidence":0.35,"abstained":false,"citations":[],"sources":[]}
                        """, MediaType.APPLICATION_JSON));

        RagAskResponse response = client.ask(new RagAskRequest("What is the weather?", null, null, null));

        assertThat(response.answerSource()).isEqualTo(RagAnswerSource.GENERAL_FALLBACK);
    }

    @Test
    void mapsMalformedRagResponseToControlledException() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://rag.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RagClient client = new RagClient(builder.build());

        server.expect(requestTo("http://rag.test/api/v1/ask"))
                .andRespond(withSuccess("{\"answer\":\"missing required fields\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.ask(new RagAskRequest("Question?", null, null, null)))
                .isInstanceOf(RagClientException.class)
                .hasMessageContaining("invalid response");
    }

    @Test
    void mapsUnexpectedRagStatusToControlledException() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://rag.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RagClient client = new RagClient(builder.build());

        server.expect(requestTo("http://rag.test/api/v1/ask"))
                .andRespond(withBadGateway());

        assertThatThrownBy(() -> client.ask(new RagAskRequest("Question?", null, null, null)))
                .isInstanceOf(RagClientException.class)
                .hasMessageContaining("HTTP 502");
    }

    @Test
    void mapsTimeoutToControlledException() {
        ClientHttpRequestFactory timeoutFactory = (uri, httpMethod) -> {
            throw new SocketTimeoutException("Read timed out");
        };
        RagClient client = new RagClient(RestClient.builder().baseUrl("http://rag.test").requestFactory(timeoutFactory).build());

        assertThatThrownBy(() -> client.ask(new RagAskRequest("Question?", null, null, null)))
                .isInstanceOf(RagClientException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void mapsUnavailableToControlledException() {
        ClientHttpRequestFactory unavailableFactory = (uri, httpMethod) -> {
            throw new java.net.ConnectException("Connection refused");
        };
        RagClient client = new RagClient(RestClient.builder().baseUrl("http://rag.test").requestFactory(unavailableFactory).build());

        assertThatThrownBy(() -> client.ask(new RagAskRequest("Question?", null, null, null)))
                .isInstanceOf(RagClientException.class)
                .hasMessageContaining("unavailable");
    }
}

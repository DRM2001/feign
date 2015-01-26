/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign;

import static dagger.Provides.Type.SET;
import static feign.Util.UTF_8;
import static feign.assertj.MockWebServerAssertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.SocketPolicy;
import com.squareup.okhttp.mockwebserver.rule.MockWebServerRule;
import dagger.Module;
import dagger.Provides;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.codec.StringDecoder;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

// unbound wildcards are not currently injectable in dagger.
@SuppressWarnings("rawtypes")
public class FeignTest {
  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Rule public final MockWebServerRule server = new MockWebServerRule();

  interface TestInterface {
    @RequestLine("POST /")
    Response response();

    @RequestLine("POST /")
    String post();

    @RequestLine("POST /")
    @Body(
        "%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\":"
            + " \"{password}\"%7D")
    void login(
        @Param("customer_name") String customer,
        @Param("user_name") String user,
        @Param("password") String password);

    @RequestLine("POST /")
    void body(List<String> contents);

    @RequestLine("POST /")
    @Headers("Content-Encoding: gzip")
    void gzipBody(List<String> contents);

    @RequestLine("POST /")
    void form(
        @Param("customer_name") String customer,
        @Param("user_name") String user,
        @Param("password") String password);

    @RequestLine("GET /{1}/{2}")
    Response uriParam(@Param("1") String one, URI endpoint, @Param("2") String two);

    @RequestLine("GET /?1={1}&2={2}")
    Response queryParams(@Param("1") String one, @Param("2") Iterable<String> twos);

    @dagger.Module(injects = Feign.class, addsTo = Feign.Defaults.class)
    static class Module {
      @Provides
      Decoder defaultDecoder() {
        return new Decoder.Default();
      }

      @Provides
      Encoder defaultEncoder() {
        return new Encoder() {
          @Override
          public void encode(Object object, RequestTemplate template) {
            if (object instanceof Map) {
              template.body(new Gson().toJson(object));
            } else {
              template.body(object.toString());
            }
          }
        };
      }
    }
  }

  @Test
  public void iterableQueryParams() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api =
        Feign.create(
            TestInterface.class,
            "http://localhost:" + server.getPort(),
            new TestInterface.Module());

    api.queryParams("user", Arrays.asList("apple", "pear"));

    assertThat(server.takeRequest()).hasPath("/?1=user&2=apple&2=pear");
  }

  interface OtherTestInterface {
    @RequestLine("POST /")
    String post();

    @RequestLine("POST /")
    byte[] binaryResponseBody();

    @RequestLine("POST /")
    void binaryRequestBody(byte[] contents);
  }

  @Test
  public void postTemplateParamsResolve() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api =
        Feign.create(
            TestInterface.class,
            "http://localhost:" + server.getPort(),
            new TestInterface.Module());

    api.login("netflix", "denominator", "password");

    assertThat(server.takeRequest())
        .hasBody(
            "{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\":"
                + " \"password\"}");
  }

  @Test
  public void responseCoercesToStringBody() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api =
        Feign.create(
            TestInterface.class,
            "http://localhost:" + server.getPort(),
            new TestInterface.Module());

    Response response = api.response();
    assertTrue(response.body().isRepeatable());
    assertEquals("foo", response.body().toString());
  }

  @Test
  public void postFormParams() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api =
        Feign.create(
            TestInterface.class,
            "http://localhost:" + server.getPort(),
            new TestInterface.Module());

    api.form("netflix", "denominator", "password");

    assertThat(server.takeRequest())
        .hasBody(
            "{\"customer_name\":\"netflix\",\"user_name\":\"denominator\",\"password\":\"password\"}");
  }

  @Test
  public void postBodyParam() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api =
        Feign.create(
            TestInterface.class,
            "http://localhost:" + server.getPort(),
            new TestInterface.Module());

    api.body(Arrays.asList("netflix", "denominator", "password"));

    assertThat(server.takeRequest())
        .hasHeaders("Content-Length: 32")
        .hasBody("[netflix, denominator, password]");
  }

  @Test
  public void postGZIPEncodedBodyParam() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api =
        Feign.create(
            TestInterface.class,
            "http://localhost:" + server.getPort(),
            new TestInterface.Module());

    api.gzipBody(Arrays.asList("netflix", "denominator", "password"));

    assertThat(server.takeRequest())
        .hasNoHeaderNamed("Content-Length")
        .hasGzippedBody("[netflix, denominator, password]".getBytes(UTF_8));
  }

  @Module(library = true)
  static class ForwardedForInterceptor implements RequestInterceptor {
    @Provides(type = SET)
    RequestInterceptor provideThis() {
      return this;
    }

    @Override
    public void apply(RequestTemplate template) {
      template.header("X-Forwarded-For", "origin.host.com");
    }
  }

  @Test
  public void singleInterceptor() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api =
        Feign.create(
            TestInterface.class,
            "http://localhost:" + server.getPort(),
            new TestInterface.Module(),
            new ForwardedForInterceptor());

    api.post();

    assertThat(server.takeRequest()).hasHeaders("X-Forwarded-For: origin.host.com");
  }

  @Module(library = true)
  static class UserAgentInterceptor implements RequestInterceptor {
    @Provides(type = SET)
    RequestInterceptor provideThis() {
      return this;
    }

    @Override
    public void apply(RequestTemplate template) {
      template.header("User-Agent", "Feign");
    }
  }

  @Test
  public void multipleInterceptor() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api =
        Feign.create(
            TestInterface.class,
            "http://localhost:" + server.getPort(),
            new TestInterface.Module(),
            new ForwardedForInterceptor(),
            new UserAgentInterceptor());

    api.post();

    assertThat(server.takeRequest())
        .hasHeaders("X-Forwarded-For: origin.host.com", "User-Agent: Feign");
  }

  @Test
  public void toKeyMethodFormatsAsExpected() throws Exception {
    assertEquals(
        "TestInterface#post()", Feign.configKey(TestInterface.class.getDeclaredMethod("post")));
    assertEquals(
        "TestInterface#uriParam(String,URI,String)",
        Feign.configKey(
            TestInterface.class.getDeclaredMethod(
                "uriParam", String.class, URI.class, String.class)));
  }

  @dagger.Module(overrides = true, library = true, includes = TestInterface.Module.class)
  static class IllegalArgumentExceptionOn404 {
    @Provides
    @Singleton
    ErrorDecoder errorDecoder() {
      return new ErrorDecoder.Default() {

        @Override
        public Exception decode(String methodKey, Response response) {
          if (response.status() == 404) return new IllegalArgumentException("zone not found");
          return super.decode(methodKey, response);
        }
      };
    }
  }

  @Test
  public void canOverrideErrorDecoder() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setResponseCode(404).setBody("foo"));
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("zone not found");

    TestInterface api =
        Feign.create(
            TestInterface.class,
            "http://localhost:" + server.getPort(),
            new IllegalArgumentExceptionOn404());

    api.post();
  }

  @Test
  public void retriesLostConnectionBeforeRead() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server.enqueue(new MockResponse().setBody("success!"));

    TestInterface api =
        Feign.create(
            TestInterface.class,
            "http://localhost:" + server.getPort(),
            new TestInterface.Module());

    api.post();

    assertEquals(2, server.getRequestCount());
  }

  @dagger.Module(overrides = true, library = true, includes = TestInterface.Module.class)
  static class DecodeFail {
    @Provides
    Decoder decoder() {
      return new Decoder() {
        @Override
        public Object decode(Response response, Type type) {
          return "fail";
        }
      };
    }
  }

  @Test
  public void overrideTypeSpecificDecoder() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("success!"));

    TestInterface api =
        Feign.create(TestInterface.class, "http://localhost:" + server.getPort(), new DecodeFail());

    assertEquals(api.post(), "fail");
  }

  @dagger.Module(overrides = true, library = true, includes = TestInterface.Module.class)
  static class RetryableExceptionOnRetry {
    @Provides
    Decoder decoder() {
      return new StringDecoder() {
        @Override
        public Object decode(Response response, Type type) throws IOException, FeignException {
          String string = super.decode(response, type).toString();
          if ("retry!".equals(string)) throw new RetryableException(string, null);
          return string;
        }
      };
    }
  }

  /** when you must parse a 2xx status to determine if the operation succeeded or not. */
  public void retryableExceptionInDecoder() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("retry!"));
    server.enqueue(new MockResponse().setBody("success!"));

    TestInterface api =
        Feign.create(
            TestInterface.class,
            "http://localhost:" + server.getPort(),
            new RetryableExceptionOnRetry());

    assertEquals(api.post(), "success!");
    assertEquals(2, server.getRequestCount());
  }

  @dagger.Module(overrides = true, library = true, includes = TestInterface.Module.class)
  static class IOEOnDecode {
    @Provides
    Decoder decoder() {
      return new Decoder() {
        @Override
        public Object decode(Response response, Type type) throws IOException {
          throw new IOException("error reading response");
        }
      };
    }
  }

  @Test
  public void doesntRetryAfterResponseIsSent() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("success!"));
    thrown.expect(FeignException.class);
    thrown.expectMessage("error reading response POST http://");

    TestInterface api =
        Feign.create(
            TestInterface.class, "http://localhost:" + server.getPort(), new IOEOnDecode());

    try {
      api.post();
    } finally {
      assertEquals(1, server.getRequestCount());
    }
  }

  @Module(overrides = true, includes = TestInterface.Module.class)
  static class TrustSSLSockets {
    @Provides
    SSLSocketFactory trustingSSLSocketFactory() {
      return TrustingSSLSocketFactory.get();
    }
  }

  @Test
  public void canOverrideSSLSocketFactory() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.useHttps(TrustingSSLSocketFactory.get("localhost"), false);
    server.enqueue(new MockResponse().setBody("success!"));
    server.play();

    try {
      TestInterface api =
          Feign.create(
              TestInterface.class, "https://localhost:" + server.getPort(), new TrustSSLSockets());
      api.post();
    } finally {
      server.shutdown();
    }
  }

  @Module(overrides = true, includes = TrustSSLSockets.class)
  static class DisableHostnameVerification {
    @Provides
    HostnameVerifier acceptAllHostnameVerifier() {
      return new HostnameVerifier() {
        @Override
        public boolean verify(String s, SSLSession sslSession) {
          return true;
        }
      };
    }
  }

  @Test
  public void canOverrideHostnameVerifier() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.useHttps(TrustingSSLSocketFactory.get("bad.example.com"), false);
    server.enqueue(new MockResponse().setBody("success!"));
    server.play();

    try {
      TestInterface api =
          Feign.create(
              TestInterface.class,
              "https://localhost:" + server.getPort(),
              new DisableHostnameVerification());
      api.post();
    } finally {
      server.shutdown();
    }
  }

  @Test
  public void retriesFailedHandshake() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.useHttps(TrustingSSLSocketFactory.get("localhost"), false);
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.FAIL_HANDSHAKE));
    server.enqueue(new MockResponse().setBody("success!"));
    server.play();

    try {
      TestInterface api =
          Feign.create(
              TestInterface.class,
              "https://localhost:" + server.getPort(),
              new TestInterface.Module(),
              new TrustSSLSockets());
      api.post();
      assertEquals(2, server.getRequestCount());
    } finally {
      server.shutdown();
    }
  }

  @Test
  public void equalsHashCodeAndToStringWork() {
    Target<TestInterface> t1 =
        new HardCodedTarget<TestInterface>(TestInterface.class, "http://localhost:8080");
    Target<TestInterface> t2 =
        new HardCodedTarget<TestInterface>(TestInterface.class, "http://localhost:8888");
    Target<OtherTestInterface> t3 =
        new HardCodedTarget<OtherTestInterface>(OtherTestInterface.class, "http://localhost:8080");
    TestInterface i1 = Feign.builder().target(t1);
    TestInterface i2 = Feign.builder().target(t1);
    TestInterface i3 = Feign.builder().target(t2);
    OtherTestInterface i4 = Feign.builder().target(t3);

    assertThat(i1).isEqualTo(i2).isNotEqualTo(i3).isNotEqualTo(i4);

    assertThat(i1.hashCode())
        .isEqualTo(i2.hashCode())
        .isNotEqualTo(i3.hashCode())
        .isNotEqualTo(i4.hashCode());

    assertThat(i1.toString())
        .isEqualTo(i2.toString())
        .isNotEqualTo(i3.toString())
        .isNotEqualTo(i4.toString());

    assertThat(t1).isNotEqualTo(i1);

    assertThat(t1.hashCode()).isEqualTo(i1.hashCode());

    assertThat(t1.toString()).isEqualTo(i1.toString());
  }

  @Test
  public void decodeLogicSupportsByteArray() throws Exception {
    byte[] expectedResponse = {12, 34, 56};
    server.enqueue(new MockResponse().setBody(expectedResponse));

    OtherTestInterface api =
        Feign.builder().target(OtherTestInterface.class, "http://localhost:" + server.getPort());

    assertThat(api.binaryResponseBody()).containsExactly(expectedResponse);
  }

  @Test
  public void encodeLogicSupportsByteArray() throws Exception {
    byte[] expectedRequest = {12, 34, 56};
    server.enqueue(new MockResponse());

    OtherTestInterface api =
        Feign.builder().target(OtherTestInterface.class, "http://localhost:" + server.getPort());

    api.binaryRequestBody(expectedRequest);

    assertThat(server.takeRequest()).hasBody(expectedRequest);
  }
}

/**
 * Copyright 2012-2020 The Feign Authors
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * @author pengfei.zhao
 */
@SuppressWarnings("deprecation")
public class OptionsTest {

  interface OptionsInterface {
    @RequestLine("GET /")
    String get(Request.Options options);

    @RequestLine("GET /")
    String get();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void socketTimeoutTest() {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("foo").setBodyDelay(3, TimeUnit.SECONDS));

    final OptionsInterface api =
        Feign.builder()
            .options(new Request.Options(1000, 1000))
            .target(OptionsInterface.class, server.url("/").toString());

    thrown.expect(FeignException.class);
    thrown.expectCause(CoreMatchers.isA(SocketTimeoutException.class));

    api.get();
  }

  @Test
  public void normalResponseTest() {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("foo").setBodyDelay(3, TimeUnit.SECONDS));

    final OptionsInterface api =
        Feign.builder()
            .options(new Request.Options(1000, 1000))
            .target(OptionsInterface.class, server.url("/").toString());

    assertThat(api.get(new Request.Options(1000, 4 * 1000))).isEqualTo("foo");
  }
}

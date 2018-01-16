package feign.form.feign.spring;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

import feign.codec.Decoder;
import feign.form.spring.converter.SpringManyMultipartFilesReader;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.netflix.feign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(
    name = "multipart-download-support-service",
    url = "http://localhost:8080",
    configuration = DownloadClient.ClientConfiguration.class)
public interface DownloadClient {

  @RequestMapping(value = "/multipart/download/{fileId}", method = GET)
  MultipartFile[] download(@PathVariable("fileId") String fileId);

  class ClientConfiguration {

    @Autowired private ObjectFactory<HttpMessageConverters> messageConverters;

    @Bean
    public Decoder feignDecoder() {
      final List<HttpMessageConverter<?>> springConverters =
          messageConverters.getObject().getConverters();
      final List<HttpMessageConverter<?>> decoderConverters =
          new ArrayList<HttpMessageConverter<?>>(springConverters.size() + 1);

      decoderConverters.addAll(springConverters);
      decoderConverters.add(new SpringManyMultipartFilesReader(4096));
      final HttpMessageConverters httpMessageConverters =
          new HttpMessageConverters(decoderConverters);

      return new SpringDecoder(
          new ObjectFactory<HttpMessageConverters>() {
            @Override
            public HttpMessageConverters getObject() {
              return httpMessageConverters;
            }
          });
    }
  }
}

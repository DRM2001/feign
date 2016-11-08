/*
 * Copyright 2016 Artem Labazin <xxlabaza@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.form;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.I_AM_A_TEAPOT;
import static org.springframework.http.HttpStatus.LOCKED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import java.util.List;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author Artem Labazin <xxlabaza@gmail.com>
 * @since 30.04.2016
 */
@Controller
@SpringBootApplication
public class Server {

  @RequestMapping(value = "/form", method = POST)
  public ResponseEntity<Void> form(
      @RequestParam("key1") String key1, @RequestParam("key2") String key2) {
    HttpStatus status = !key1.equals(key2) ? BAD_REQUEST : OK;
    return ResponseEntity.status(status).body(null);
  }

  @RequestMapping(value = "/upload/{id}", method = POST)
  @ResponseStatus(OK)
  public ResponseEntity<Long> upload(
      @PathVariable("id") Integer id,
      @RequestParam("public") Boolean isPublic,
      @RequestParam("file") MultipartFile file) {
    HttpStatus status;
    if (id == null || id != 10) {
      status = LOCKED;
    } else if (isPublic == null || !isPublic) {
      status = FORBIDDEN;
    } else if (file.getSize() == 0) {
      status = I_AM_A_TEAPOT;
    } else if (file.getOriginalFilename() == null || file.getOriginalFilename().trim().isEmpty()) {
      status = CONFLICT;
    } else {
      status = OK;
    }
    return ResponseEntity.status(status).body(file.getSize());
  }

  @RequestMapping(value = "/json", method = POST, consumes = APPLICATION_JSON_VALUE)
  public ResponseEntity<String> json(@RequestBody Dto dto) {
    HttpStatus status;
    if (!dto.getName().equals("Artem")) {
      status = CONFLICT;
    } else if (!dto.getAge().equals(11)) {
      status = I_AM_A_TEAPOT;
    } else {
      status = OK;
    }
    return ResponseEntity.status(status).body("ok");
  }

  @RequestMapping(value = "/query_map")
  public ResponseEntity<Integer> queryMap(@RequestParam("filter") List<String> filters) {
    HttpStatus status;
    if (filters == null || filters.isEmpty()) {
      status = BAD_REQUEST;
    } else {
      status = OK;
    }
    return ResponseEntity.status(status).body(filters.size());
  }

  @RequestMapping(
      value = "/wild-card-map",
      method = POST,
      consumes = APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<Integer> wildCardMap(
      @RequestParam("key1") String key1, @RequestParam("key2") String key2) {
    HttpStatus status = key1.equals(key2) ? OK : BAD_REQUEST;
    return ResponseEntity.status(status).body(null);
  }
}

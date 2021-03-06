/*
 * Copyright (C) 2016 Original Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.fabric8.docker.client.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import io.fabric8.docker.client.Config;
import io.fabric8.docker.client.DockerClientException;
import io.fabric8.docker.dsl.EventListener;
import io.fabric8.docker.client.utils.URLUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class OperationSupport {

  public static final MediaType MEDIA_TYPE_RAW_STREAM = MediaType.parse("application/vnd.docker.raw-stream");
  public static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
  public static final MediaType MEDIA_TYPE_TEXT = MediaType.parse("text/play; charset=utf-8");
  public static final MediaType MEDIA_TYPE_TAR = MediaType.parse("application/tar");
  public static final MediaType MEDIA_TYPE_BZIP2 = MediaType.parse("application/x-bzip2");

  public static final String Q = "?";
  public static final String A = "&";
  public static final String EQUALS = "=";
  public static final String EMPTY = "";

  public static final String TRUE = "1";
  public static final String FLASE = "0";

  protected static final ObjectMapper JSON_MAPPER = new ObjectMapper()
          .enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS);

  protected static final String JSON_OPERATION = "json";
  protected static final String SEARCH_OPERATION = "search";

  protected static final EventListener NULL_LISTENER = new EventListener() {
    @Override
    public void onSuccess(String message) {
    }

    @Override
    public void onError(String message) {
    }

    @Override
    public void onError(Throwable t) {

    }

    @Override
    public void onEvent(String event) {
    }
  };
  
  protected final OkHttpClient client;
  protected final Config config;
  protected final String resourceType;
  protected final String name;
  protected final String operationType;

  public OperationSupport() {
    this(null, null, null, null, null);
  }

  public OperationSupport(OkHttpClient client, Config config, String resourceType) {
    this(client, config, resourceType, null, null);
  }

  public OperationSupport(OkHttpClient client, Config config, String resourceType, String name, String operationType) {
    this.client = client;
    this.config = config;
    this.resourceType = resourceType;
    this.name = name;
    this.operationType = operationType;
  }


  public String getResourceType() {
    return resourceType;
  }

  public String getName() {
    return name;
  }

  public URL getRootUrl() {
    try {
      return new URL(URLUtils.join(config.getDockerUrl().toString()));
    } catch (MalformedURLException e) {
      throw DockerClientException.launderThrowable(e);
    }
  }

  public URL getResourceUrl(String name) throws MalformedURLException {
    if (name != null && !name.isEmpty()) {
      return new URL(URLUtils.join(getRootUrl().toString(), resourceType, name));
    } else {
      return new URL(URLUtils.join(getRootUrl().toString(), resourceType));
    }
  }

  public URL getResourceUrl() throws MalformedURLException {
   return getResourceUrl(name);
  }

  public URL getOperationUrl() throws MalformedURLException {
    return getOperationUrl(operationType);
  }

  public URL getOperationUrl(String operationType) throws MalformedURLException {
    if (operationType != null && !operationType.isEmpty()) {
      return new URL(URLUtils.join(getResourceUrl().toString(), operationType));
    } else {
      return new URL(URLUtils.join(getResourceUrl().toString()));
    }
  }

  protected void handleDelete(URL requestUrl) throws ExecutionException, InterruptedException, DockerClientException, IOException {
    handleDelete(requestUrl, (Class) null);
  }

  protected <T> T handleDelete(URL requestUrl, Class<T> type) throws ExecutionException, InterruptedException, DockerClientException, IOException {
    Request.Builder requestBuilder = new Request.Builder().delete(null).url(requestUrl);
   return handleResponse(requestBuilder, type, 200, 204);
  }

  protected <T> T handleDelete(URL requestUrl, JavaType type) throws ExecutionException, InterruptedException, DockerClientException, IOException {
    Request.Builder requestBuilder = new Request.Builder().delete(null).url(requestUrl);
    return handleResponse(requestBuilder, type, 200,204);
  }

  public <T> void handleCreate(T resource) throws ExecutionException, InterruptedException, DockerClientException, IOException {
    RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, JSON_MAPPER.writeValueAsString(resource));
    Request.Builder requestBuilder = new Request.Builder().post(body).url(getResourceUrl().toString());
    handleResponse(requestBuilder, 200, 201, 204);
  }

  public <T, I> T handleCreate(I resource, Class<T> outputType, String ...dirs) throws ExecutionException, InterruptedException, DockerClientException, IOException {
    RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, JSON_MAPPER.writeValueAsString(resource));
    Request.Builder requestBuilder = new Request.Builder().post(body).url(URLUtils.join(getResourceUrl().toString(), URLUtils.join(dirs)));
    return handleResponse(requestBuilder, outputType, 200, 201, 204);
  }

  public void handleGet() throws ExecutionException, InterruptedException, DockerClientException, IOException {
    Request.Builder requestBuilder = new Request.Builder().get().url(getOperationUrl());
    handleResponse(requestBuilder, 200);
  }

  public <T> T handleGet(Class<T> type) throws ExecutionException, InterruptedException, DockerClientException, IOException {
    Request.Builder requestBuilder = new Request.Builder().get().url(getOperationUrl());
    return handleResponse(requestBuilder, type, 200);
  }

  public <T> T handleGet(URL resourceUrl, Class<T> type) throws ExecutionException, InterruptedException, DockerClientException, IOException {
    Request.Builder requestBuilder = new Request.Builder().get().url(resourceUrl);
    return handleResponse(requestBuilder, type, 200);
  }

  protected <T> List<T> handleList(URL resourceUrl, Class<T> type) throws ExecutionException, InterruptedException, DockerClientException, IOException {
    Request request = new Request.Builder().get().url(resourceUrl).build();
    Response response = null;
    try {
      response = client.newCall(request).execute();
    } catch (Exception e) {
      throw requestException(request, e);
    }

    if (response.code() == 404) {
      return Collections.emptyList();
    }
    assertResponseCodes(request, response, new int[]{200, 404});
    if (type != null) {
      return JSON_MAPPER.readValue(response.body().byteStream(), JSON_MAPPER.getTypeFactory().constructCollectionType(List.class, type));
    } else {
      return null;
    }
  }

  protected <T> T handleResponse(Request.Builder requestBuilder, Class<T> type, int... successStatusCodes) throws ExecutionException, InterruptedException, DockerClientException, IOException {
    return handleResponse(requestBuilder, type == null ? null : JSON_MAPPER.getTypeFactory().constructType(type), successStatusCodes);
  }

  protected Response handleResponse(Request.Builder requestBuilder, int... successStatusCodes) throws ExecutionException, InterruptedException, DockerClientException, IOException {
    Request request = requestBuilder.build();
    Response response = null;
    try {
      response = client.newCall(request).execute();
    } catch (Exception e) {
      throw requestException(request, e);
    }
    assertResponseCodes(request, response, successStatusCodes);
    return response;
  }

  protected <T> T handleResponse(Request.Builder requestBuilder, JavaType type, int... successStatusCodes) throws ExecutionException, InterruptedException, DockerClientException, IOException {
    Request request = requestBuilder.build();
    Response response = null;
    try {
      response = client.newCall(request).execute();
    } catch (Exception e) {
      throw requestException(request, e);
    }
    assertResponseCodes(request, response, successStatusCodes);
    if (type != null) {
      return JSON_MAPPER.readValue(response.body().byteStream(), type);
    } else {
      return null;
    }
  }

  protected InputStream handleResponseStream(Request.Builder requestBuilder, int... successStatusCodes) throws ExecutionException, InterruptedException, DockerClientException, IOException {
    Request request = requestBuilder.build();
    Response response = null;
    try {
      response = client.newCall(request).execute();
    } catch (Exception e) {
      throw requestException(request, e);
    }
    assertResponseCodes(request, response, successStatusCodes);
    return response.body().byteStream();
  }

  /**
   * Checks if the response status code is the expected and throws the appropriate DockerClientException if not.
   *
   * @param request            The {#link Request} object.
   * @param response           The {@link Response} object.
   * @param expectedStatusCodes The expected status codes.
   * @throws DockerClientException When the response code is not the expected.
   */
  protected void assertResponseCodes(Request request, Response response, int... expectedStatusCodes) {
    int statusCode = response.code();
    if (expectedStatusCodes.length > 0) {
      for (int expected : expectedStatusCodes) {
        if (statusCode == expected) {
          return;
        }
      }
      throw requestFailure(request, response);
    }
  }

  DockerClientException requestFailure(Request request, Response response) {
    StringBuilder sb = new StringBuilder();
    sb.append("Failure executing: ").append(request.method())
            .append(" at: ").append(request.url().toString()).append(".")
            .append(" Status:").append(response.code()).append(".")
            .append(" Message: ").append(response.message()).append(".");
    try {
      String body = response.body().string();
      sb.append(" Body: ").append(body);
    } catch (Throwable t) {
      sb.append(" Body: <unreadable>");
    }
    return new DockerClientException(sb.toString(), response.code());
  }

  DockerClientException requestException(Request request, Exception e) {
    StringBuilder sb = new StringBuilder();
    sb.append("Error executing: ").append(request.method())
      .append(" at: ").append(request.url().toString())
      .append(". Cause: ").append(e.getMessage());

    return new DockerClientException(sb.toString(), e);
  }

  public Config getConfig() {
    return config;
  }
}

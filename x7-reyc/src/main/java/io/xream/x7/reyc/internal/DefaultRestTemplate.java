/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.xream.x7.reyc.internal;

import io.xream.x7.base.KV;
import io.xream.x7.base.util.LoggerProxy;
import io.xream.x7.reyc.api.HeaderRequestInterceptor;
import io.xream.x7.reyc.api.HeaderResponseInterceptor;
import io.xream.x7.reyc.api.SimpleRestTemplate;
import io.xream.x7.reyc.api.SimpleResult;
import org.apache.http.Header;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author Sim
 */
public class DefaultRestTemplate implements SimpleRestTemplate {

    private HttpProperties properties;

    private List<HttpRequestInterceptor> httpRequestInterceptorList = new ArrayList<>();
    private List<HttpResponseInterceptor> httpResponseInterceptorList = new ArrayList<>();

    private List<HeaderRequestInterceptor> headerRequestInterceptorList = new ArrayList<>();
    private List<HeaderResponseInterceptor> headerResponseInterceptorList = new ArrayList<>();
    public void add(HeaderRequestInterceptor headerInterceptor) {
        this.headerRequestInterceptorList.add(headerInterceptor);
    }
    public void add(HeaderResponseInterceptor headerResponseInterceptor) {
        this.headerResponseInterceptorList.add(headerResponseInterceptor);
    }
    public void add(HttpRequestInterceptor httpRequestInterceptor) {
        this.httpRequestInterceptorList.add(httpRequestInterceptor);
    }
    public void add(HttpResponseInterceptor httpResponseInterceptor){
        this.httpResponseInterceptorList.add(httpResponseInterceptor);
    }

    public DefaultRestTemplate(
            HttpProperties properties){
        this.properties = properties;
    }



    @Override
    public SimpleResult post(Class clz, String url, Object requestObject, List<KV> headerList) {

        return execute(clz,url,requestObject,headerList,
                (httpclient) -> HttpClientUtil.post(clz,url, requestObject, properties.getConnectTimeout(),properties.getSocketTimeout(),httpclient)
        );
    }

    @Override
    public SimpleResult get(Class clz, String url, List<KV> headerList) {
        return execute(clz,url,null,headerList,
                    (httpclient) -> HttpClientUtil.get(clz,url, properties.getConnectTimeout(),properties.getSocketTimeout(),httpclient)
                );
    }


    private  HttpClientBuilder builder() {
        HttpClientBuilder builder = HttpClients.custom();
        return builder;
    }


    private SimpleResult execute(Class clz, String url, Object request, List<KV> headerList, Client client) {

        HttpClientBuilder builder = builder();
        for (HttpRequestInterceptor httpRequestInterceptor : httpRequestInterceptorList) {
            builder.addInterceptorFirst(httpRequestInterceptor);
        }
        for (HttpResponseInterceptor httpResponseInterceptor : httpResponseInterceptorList) {
            builder.addInterceptorFirst(httpResponseInterceptor);
        }
        builder.addInterceptorFirst((HttpRequestInterceptor) (httpRequest, httpContext) -> {
            if (headerList != null) {
                for (KV kv1 : headerList) {
                    httpRequest.addHeader(kv1.k, String.valueOf(kv1.v));
                }
            }
            for (HeaderRequestInterceptor headerInterceptor : headerRequestInterceptorList){
                KV kv = headerInterceptor.apply();
                httpRequest.addHeader(kv.k,String.valueOf(kv.v));
            }
        });

        Map<String,String> responseHeaderMap = new HashMap<>();

        builder.addInterceptorFirst((HttpResponseInterceptor) (httpResponse, httpContext) -> {
            for (HeaderResponseInterceptor headerResponseInterceptor : headerResponseInterceptorList) {
                Header header = httpResponse.getFirstHeader(headerResponseInterceptor.getKey());
                if (header == null)
                    continue;
                responseHeaderMap.put(header.getName(),header.getValue());
                LoggerProxy.info(clz,header.toString());
            }
        });

        CloseableHttpClient httpclient  = builder.build();

        String body = client.execute(httpclient);

        return new SimpleResult(body,responseHeaderMap);
    }


}

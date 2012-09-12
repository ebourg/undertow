/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package io.undertow.test.handlers.form;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.blocking.BlockingHandler;
import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormEncodedDataHandler;
import io.undertow.server.handlers.form.MultiPartHandler;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.Headers;
import junit.textui.TestRunner;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.Parameterized.class)
public class MultipartFormDataParserTestCase {

    static class AggregateRunner extends TestRunner {

    }

    private final HttpHandler rootHandler;

    public MultipartFormDataParserTestCase(final HttpHandler rootHandler) {
        this.rootHandler = rootHandler;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> handlerChains() {
        List<Object[]> ret = new ArrayList<Object[]>();
        final MultiPartHandler fd = new MultiPartHandler();
        fd.setNext(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
                final FormDataParser parser = (FormDataParser) exchange.getAttachment(FormDataParser.ATTACHMENT_KEY);
                try {
                    FormData data = parser.parse().get();
                    Iterator<String> it = data.iterator();
                    while (it.hasNext()) {
                        String fd = it.next();
                        exchange.getResponseHeaders().addAll(fd, data.get(fd));
                    }
                    completionHandler.handleComplete();
                } catch (IOException e) {
                    exchange.setResponseCode(500);
                    completionHandler.handleComplete();
                }
            }
        });
        ret.add(new Object[]{fd});
        final BlockingHandler blocking = new BlockingHandler();

        final MultiPartHandler bf = new MultiPartHandler();
        bf.setNext(blocking);
        blocking.setRootHandler(new BlockingHttpHandler() {


            @Override
            public void handleRequest(final BlockingHttpServerExchange exchange) throws Exception {
                final FormDataParser parser = (FormDataParser) exchange.getExchange().getAttachment(FormDataParser.ATTACHMENT_KEY);
                try {
                    FormData data = parser.parseBlocking();
                    Iterator<String> it = data.iterator();
                    while (it.hasNext()) {
                        String fd = it.next();
                        exchange.getExchange().getResponseHeaders().addAll(fd, data.get(fd));
                    }
                } catch (IOException e) {
                    exchange.getExchange().setResponseCode(500);
                }
            }
        });
        ret.add(new Object[]{bf});
        return ret;

    }

    @Test
    public void testFormDataParsing() throws Exception {
        runTest(new BasicNameValuePair("name", "A Value"));
        runTest(new BasicNameValuePair("name", "A Value"), new BasicNameValuePair("A/name/with_special*chars", "A $ value&& with=SomeCharacters"));

    }

    private void runTest(final NameValuePair ... pairs) throws Exception{
        DefaultServer.setRootHandler(rootHandler);
        DefaultHttpClient client = new DefaultHttpClient();
        try {

            final List<NameValuePair> data = new ArrayList<NameValuePair>();
            data.addAll(Arrays.asList(pairs));
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerAddress() + "/path");
            //post.setHeader(Headers.CONTENT_TYPE, MultiPartHandler.MULTIPART_FORM_DATA);
            MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
            for(NameValuePair part : pairs) {
                entity.addPart(part.getName(), new StringBody( part.getValue(), "text/plain", Charset.forName("UTF-8")));
            }
            post.setEntity(entity);
            HttpResponse result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            checkResult(data,  result);
            HttpClientUtils.readResponse(result);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private void checkResult(final List<NameValuePair> data, final HttpResponse result) {
        for(NameValuePair vp : data) {
            Assert.assertEquals(vp.getValue(), result.getHeaders(vp.getName())[0].getValue());
        }
    }

}

/**
 * Copyright 2007-2016, Kaazing Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kaazing.gateway.transport.http.acceptor.specification.rfc7234;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;

import org.apache.mina.core.service.IoHandler;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.jmock.lib.concurrent.Synchroniser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.gateway.resource.address.ResourceAddress;
import org.kaazing.gateway.resource.address.ResourceAddressFactory;
import org.kaazing.gateway.transport.IoHandlerAdapter;
import org.kaazing.gateway.transport.http.HttpAcceptSession;
import org.kaazing.gateway.transport.http.HttpAcceptorRule;
import org.kaazing.gateway.transport.http.HttpHeaders;
import org.kaazing.gateway.transport.http.HttpStatus;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.kaazing.test.util.ITUtil;
import org.kaazing.test.util.MethodExecutionTrace;

/**
 * Test to validate behavior as specified in <a href="https://tools.ietf.org/html/rfc7231#section-4">RFC 7231 section 4:
 * Request Methods</a>.
 */
public class ExpiresIT {
    private static final ResourceAddress HTTP_ADDRESS = httpAddress();

    private final HttpAcceptorRule acceptor = new HttpAcceptorRule();

    private JUnitRuleMockery context = new JUnitRuleMockery() {
        {
            setThreadingPolicy(new Synchroniser());
        }
    };

    private TestRule contextRule = ITUtil.toTestRule(context);
    private final TestRule trace = new MethodExecutionTrace();
    private final K3poRule k3po = new K3poRule().setScriptRoot("org/kaazing/specification/http/rfc7234/expires");
    private final TestRule timeoutRule = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public TestRule chain = RuleChain.outerRule(trace).around(timeoutRule).around(contextRule).around(acceptor).around(k3po);

    @Test
    @Specification({"already.expired.conditional.request.304/request"})
    public void shouldReceiveOKWhenCacheResponseExpiredForUnconditionalRequest() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final IoHandler acceptHandler = new IoHandlerAdapter<HttpAcceptSession>() {
            @Override
            protected void doSessionOpened(HttpAcceptSession session) throws Exception {
                latch.countDown();
                if(session.getReadHeaders().containsKey(HttpHeaders.HEADER_IF_MODIFIED_SINCE)) {
                    session.setStatus(HttpStatus.REDIRECT_NOT_MODIFIED);
                } else {
                    session.setStatus(HttpStatus.SUCCESS_OK);
                }
                session.addWriteHeader(HttpHeaders.HEADER_LAST_MODIFIED, String.valueOf(System.currentTimeMillis()));
                session.addWriteHeader(HttpHeaders.HEADER_EXPIRES, String.valueOf(System.currentTimeMillis()));
                session.close(false);
            }
        };
        acceptor.bind(HTTP_ADDRESS, acceptHandler);

        k3po.finish();
        assertTrue(latch.await(4, SECONDS));
    }

    @Test
    @Specification({"already.expired.unconditional.request.200/request"})
    public void shouldReceiveNotModifiedWhenCacheResponseExpiredForConditionalRequest() throws Exception {
        testHttpNoResponseMessage(HTTP_ADDRESS);
    }

    @Test
    @Specification({"fresh.response.from.cache/request"})
    public void shouldReceiveUnexpiredResponseFromCache() throws Exception {
        testHttpNoResponseMessage(HTTP_ADDRESS);
    }

    @Test
    @Specification({"ignored.when.cache-control.max-age.is.available/request"})
    public void shouldIgnoreExpiresHeaderWhenCacheControlMaxAgeIsAvailable() throws Exception {
        testHttpNoResponseMessage(HTTP_ADDRESS);
    }

    @Test
    @Specification({"ignored.when.cache-control.s-maxage.is.available/request"})
    public void shouldIgnoreExpiresHeaderWhenCacheControlSharedMaxAgeIsAvailable() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        final IoHandler acceptHandler = new IoHandlerAdapter<HttpAcceptSession>() {

            @Override
            protected void doSessionOpened(HttpAcceptSession session) throws Exception {
                latch.countDown();
                session.addWriteHeader(HttpHeaders.HEADER_CACHE_CONTROL, String.valueOf("s-maxage=10"));
                session.addWriteHeader(HttpHeaders.HEADER_EXPIRES, String.valueOf(System.currentTimeMillis()));
                session.close(false);
            }
        };
        acceptor.bind(HTTP_ADDRESS, acceptHandler);

        k3po.finish();
        assertTrue(latch.await(4, SECONDS));
    }

    @Test
    @Specification({"ignored.when.multiple.expires.200/request"})
    public void shouldIgnoreMultipleExpiresHeaderInResponse() throws Exception {
        testHttpNoResponseMessage(HTTP_ADDRESS);
    }

    @Test
    @Specification({"invalid.date.conditional.request.304/request"})
    public void shouldReceiveNotModifiedWhenCachedResponseIsStaleDueToInvalidDateForConditionalRequest() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final IoHandler acceptHandler = new IoHandlerAdapter<HttpAcceptSession>() {
            @Override
            protected void doSessionOpened(HttpAcceptSession session) throws Exception {
                latch.countDown();
                if(session.getReadHeaders().containsKey(HttpHeaders.HEADER_IF_MODIFIED_SINCE)) {
                    session.setStatus(HttpStatus.REDIRECT_NOT_MODIFIED);
                } else {
                    session.setStatus(HttpStatus.SUCCESS_OK);
                }
                session.addWriteHeader(HttpHeaders.HEADER_LAST_MODIFIED, String.valueOf(System.currentTimeMillis()));
                session.addWriteHeader(HttpHeaders.HEADER_EXPIRES, String.valueOf(System.currentTimeMillis()));
                session.close(false);
            }
        };
        acceptor.bind(HTTP_ADDRESS, acceptHandler);

        k3po.finish();
        assertTrue(latch.await(4, SECONDS));
    }

    @Test
    @Specification({"invalid.date.unconditional.request.200/request"})
    public void shouldReceiveOKWhenCachedResponseIsStaleDueToInvalidDateForUnconditionalRequest() throws Exception {
        testHttpNoResponseMessage(HTTP_ADDRESS);
    }

    @Test
    @Specification({"stale.response.conditional.request.304/request"})
    public void shouldReceiveNotModifiedWhenCachedResponseIsStaleForConditionalRequest() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final IoHandler acceptHandler = new IoHandlerAdapter<HttpAcceptSession>() {
            @Override
            protected void doSessionOpened(HttpAcceptSession session) throws Exception {
                latch.countDown();
                if(session.getReadHeaders().containsKey(HttpHeaders.HEADER_IF_MODIFIED_SINCE)) {
                    session.setStatus(HttpStatus.REDIRECT_NOT_MODIFIED);
                } else {
                    session.setStatus(HttpStatus.SUCCESS_OK);
                }
                session.addWriteHeader(HttpHeaders.HEADER_LAST_MODIFIED, String.valueOf(System.currentTimeMillis()));
                session.addWriteHeader(HttpHeaders.HEADER_EXPIRES, String.valueOf(System.currentTimeMillis()));
                session.close(false);
            }
        };
        acceptor.bind(HTTP_ADDRESS, acceptHandler);

        k3po.finish();
        assertTrue(latch.await(4, SECONDS));
    }

    @Test
    @Specification({"stale.response.unconditional.request.200/request"})
    public void shouldReceiveOKWhenCachedResponseIsStaleForUnconditionalRequest() throws Exception {
        testHttpNoResponseMessage(HTTP_ADDRESS);
    }

    private void testHttpNoResponseMessage(ResourceAddress address) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        final IoHandler acceptHandler = new IoHandlerAdapter<HttpAcceptSession>() {

            @Override
            protected void doSessionOpened(HttpAcceptSession session) throws Exception {
                latch.countDown();
                session.addWriteHeader(HttpHeaders.HEADER_CACHE_CONTROL, String.valueOf("max-age=10"));
                session.addWriteHeader(HttpHeaders.HEADER_EXPIRES, String.valueOf(System.currentTimeMillis()));
                session.close(false);
            }
        };
        acceptor.bind(HTTP_ADDRESS, acceptHandler);

        k3po.finish();
        assertTrue(latch.await(4, SECONDS));
    }

    private void testHttpResponseWithLastModifiedAndExpired(ResourceAddress address) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        final IoHandler acceptHandler = new IoHandlerAdapter<HttpAcceptSession>() {

            @Override
            protected void doSessionOpened(HttpAcceptSession session) throws Exception {
                latch.countDown();
                session.addWriteHeader(HttpHeaders.HEADER_LAST_MODIFIED, String.valueOf(System.currentTimeMillis()));
                session.addWriteHeader(HttpHeaders.HEADER_EXPIRES, String.valueOf(System.currentTimeMillis()));
                session.close(false);
            }
        };
        acceptor.bind(address, acceptHandler);

        k3po.finish();
        assertTrue(latch.await(4, SECONDS));
    }

    private static ResourceAddress httpAddress() {
        ResourceAddressFactory addressFactory = ResourceAddressFactory.newResourceAddressFactory();
        String address = "http://localhost:8000/index.html";
        return addressFactory.newResourceAddress(address);
    }
}

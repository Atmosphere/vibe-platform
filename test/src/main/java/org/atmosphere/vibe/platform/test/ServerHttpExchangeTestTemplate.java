/*
 * Copyright 2014 The Vibe Project
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
package org.atmosphere.vibe.platform.test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.atmosphere.vibe.platform.action.Action;
import org.atmosphere.vibe.platform.action.VoidAction;
import org.atmosphere.vibe.platform.http.HttpStatus;
import org.atmosphere.vibe.platform.http.ServerHttpExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

/**
 * Template class to test {@link ServerHttpExchange}.
 *
 * @author Donghwan Kim
 */
public abstract class ServerHttpExchangeTestTemplate {

    @Rule
    public Timeout globalTimeout = new Timeout(10000);
    protected Performer performer;
    protected int port;

    @Before
    public void before() throws Exception {
        performer = new Performer();
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            port = serverSocket.getLocalPort();
        }
        startServer();
    }

    @After
    public void after() throws Exception {
        stopServer();
    }

    /**
     * Starts the server listening port
     * {@link ServerHttpExchangeTestTemplate#port} and if HTTP request's path is
     * {@code /test}, create {@link ServerHttpExchange} and pass it to
     * {@code performer.serverAction()}. This method is executed following
     * {@link Before}.
     */
    protected abstract void startServer() throws Exception;

    /**
     * Stops the server started in
     * {@link ServerHttpExchangeTestTemplate#startServer()}. This method is
     * executed following {@link After}.
     *
     * @throws Exception
     */
    protected abstract void stopServer() throws Exception;

    @Test
    public void uri() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                assertThat(http.uri(), is("/test?hello=there"));
                performer.start();
            }
        })
        .send("/test?hello=there");
    }

    @Test
    public void method() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                assertThat(http.method(), is("POST"));
                performer.start();
            }
        })
        .send(new Action<Request>() {
            @Override
            public void on(Request req) {
                req.method(HttpMethod.POST);
            }
        });
    }

    @Test
    public void header() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                assertThat(http.headerNames(), either(hasItems("a", "b")).or(hasItems("A", "B")));
                assertThat(http.header("A"), is("A"));
                assertThat(http.header("B"), is("B1"));
                assertThat(http.headers("A"), contains("A"));
                assertThat(http.headers("B"), contains("B1", "B2"));
                performer.start();
            }
        })
        .send(new Action<Request>() {
            @Override
            public void on(Request req) {
                req.header("A", "A").header("B", "B1").header("B", "B2");
            }
        });
    }

    @Test
    public void read_text() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                final StringBuilder body = new StringBuilder();
                http.chunkAction(new Action<String>() {
                    @Override
                    public void on(String data) {
                        body.append(data);
                    }
                })
                .endAction(new VoidAction() {
                    @Override
                    public void on() {
                        assertThat(body.toString(), is("A Breath Clad In Happiness"));
                        performer.start();
                    }
                })
                .read();
            }
        })
        .send(new Action<Request>() {
            @Override
            public void on(Request req) {
                req.method(HttpMethod.POST).content(new StringContentProvider("A Breath Clad In Happiness"), "text/plain; charset=utf-8");
            }
        });
    }

    @Test
    public void readAsText() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                final StringBuilder body = new StringBuilder();
                http.chunkAction(new Action<String>() {
                    @Override
                    public void on(String data) {
                        body.append(data);
                    }
                })
                .endAction(new VoidAction() {
                    @Override
                    public void on() {
                        assertThat(body.toString(), is("Day 7: Poem of the Ocean"));
                        performer.start();
                    }
                })
                .readAsText();
            }
        })
        .send(new Action<Request>() {
            @Override
            public void on(Request req) {
                req.method(HttpMethod.POST).content(new StringContentProvider("Day 7: Poem of the Ocean"), "application/octet-stream");
            }
        });
    }

    @Test
    public void readAsText_charset() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                final StringBuilder body = new StringBuilder();
                http.chunkAction(new Action<String>() {
                    @Override
                    public void on(String data) {
                        body.append(data);
                    }
                })
                .endAction(new VoidAction() {
                    @Override
                    public void on() {
                        assertThat(body.toString(), is("시간 속에 만들어진 무대 위에 그대는 없다"));
                        performer.start();
                    }
                })
                .readAsText("utf-8");
            }
        })
        .send(new Action<Request>() {
            @Override
            public void on(Request req) {
                req.method(HttpMethod.POST).content(new StringContentProvider("시간 속에 만들어진 무대 위에 그대는 없다", "utf-8"), "text/plain; charset=euc-kr");
            }
        });
    }

    @Test
    public void read_binary() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                final ByteArrayOutputStream body = new ByteArrayOutputStream();
                http.chunkAction(new Action<ByteBuffer>() {
                    @Override
                    public void on(ByteBuffer data) {
                        byte[] bytes = new byte[data.remaining()];
                        data.get(bytes);
                        body.write(bytes, 0, bytes.length);
                    }
                })
                .endAction(new VoidAction() {
                    @Override
                    public void on() {
                        assertThat(body.toByteArray(), is(new byte[] { 'h', 'i' }));
                        performer.start();
                    }
                })
                .read();
            }
        })
        .send(new Action<Request>() {
            @Override
            public void on(Request req) {
                req.method(HttpMethod.POST).content(new BytesContentProvider(new byte[] { 'h', 'i' }), "application/octet-stream");
            }
        });
    }

    @Test
    public void readAsBinary() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                final ByteArrayOutputStream body = new ByteArrayOutputStream();
                http.chunkAction(new Action<ByteBuffer>() {
                    @Override
                    public void on(ByteBuffer data) {
                        byte[] bytes = new byte[data.remaining()];
                        data.get(bytes);
                        body.write(bytes, 0, bytes.length);
                    }
                })
                .endAction(new VoidAction() {
                    @Override
                    public void on() {
                        assertThat(body.toByteArray(), is(new byte[] { 'h', 'i' }));
                        performer.start();
                    }
                })
                .readAsBinary();
            }
        })
        .send(new Action<Request>() {
            @Override
            public void on(Request req) {
                req.method(HttpMethod.POST).content(new BytesContentProvider(new byte[] { 'h', 'i' }), "text/plain");
            }
        });
    }

    @Test
    public void read_after_end() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                final StringBuilder body = new StringBuilder();
                http.end().chunkAction(new Action<String>() {
                    @Override
                    public void on(String data) {
                        body.append(data);
                    }
                })
                .endAction(new VoidAction() {
                    @Override
                    public void on() {
                        assertThat(body.toString(), is("Blaze the Trail"));
                        performer.start();
                    }
                })
                .read();
            }
        })
        .send(new Action<Request>() {
            @Override
            public void on(Request req) {
                req.method(HttpMethod.POST).content(new StringContentProvider("Blaze the Trail"));
            }
        });
    }

    @Test
    public void bodyAction_with_text() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                http.bodyAction(new Action<String>() {
                    @Override
                    public void on(String data) {
                        assertThat(data, is("A Breath Clad In Happiness"));
                        performer.start();
                    }
                })
                .read();
            }
        })
        .send(new Action<Request>() {
            @Override
            public void on(Request req) {
                req.method(HttpMethod.POST).content(new StringContentProvider("A Breath Clad In Happiness"));
            }
        });
    }

    @Test
    public void bodyAction_with_binary() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                http.bodyAction(new Action<ByteBuffer>() {
                    @Override
                    public void on(ByteBuffer data) {
                        assertThat(data, is(ByteBuffer.wrap(new byte[] { 'h', 'i' })));
                        performer.start();
                    }
                })
                .read();
            }
        })
        .send(new Action<Request>() {
            @Override
            public void on(Request req) {
                req.method(HttpMethod.POST).content(new BytesContentProvider(new byte[] { 'h', 'i' }));
            }
        });
    }

    @Test
    public void setStatus() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                http.setStatus(HttpStatus.NOT_FOUND).end();
            }
        })
        .responseListener(new Response.Listener.Adapter() {
            @Override
            public void onSuccess(Response response) {
                assertThat(response.getStatus(), is(404));
                performer.start();
            }
        })
        .send();
    }

    @Test
    public void setHeader() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                http.setHeader("A", "A").setHeader("B", Arrays.asList("B1", "B2")).end();
            }
        })
        .responseListener(new Response.Listener.Adapter() {
            @Override
            public void onSuccess(Response res) {
                HttpFields headers = res.getHeaders();
                assertThat(headers.getFieldNamesCollection(), hasItems("A", "B"));
                assertThat(headers.get("A"), is("A"));
                assertThat(headers.get("B"), is("B1, B2"));
                performer.start();
            }
        })
        .send();
    }

    @Test
    public void write_text() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                http.setHeader("content-type", "text/plain; charset=euc-kr").end("기억 속에 머무른 그 때의 모습으로 그때의 웃음으로");
            }
        })
        .responseListener(new Response.Listener.Adapter() {
            String body;

            @Override
            public void onContent(Response response, ByteBuffer content) {
                body = Charset.forName("euc-kr").decode(content).toString();
            }

            @Override
            public void onSuccess(Response response) {
                assertThat(body, is("기억 속에 머무른 그 때의 모습으로 그때의 웃음으로"));
                performer.start();
            }
        })
        .send();
    }

    @Test
    public void write_text_charset() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                http.end("기억 속에 머무른 그 때의 모습으로 그때의 웃음으로", "euc-kr");
            }
        })
        .responseListener(new Response.Listener.Adapter() {
            String body;

            @Override
            public void onContent(Response response, ByteBuffer content) {
                body = Charset.forName("euc-kr").decode(content).toString();
            }

            @Override
            public void onSuccess(Response response) {
                assertThat(body, is("기억 속에 머무른 그 때의 모습으로 그때의 웃음으로"));
                performer.start();
            }
        })
        .send();
    }

    @Test
    public void write_binary() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                http.write(ByteBuffer.wrap(new byte[] { 0x00 }).asReadOnlyBuffer())
                .write(ByteBuffer.wrap(new byte[] { 0x01 }))
                .write(ByteBuffer.wrap(new byte[] { 0x02 }))
                .end();
            }
        })
        .responseListener(new Response.Listener.Adapter() {
            List<byte[]> chunks = new ArrayList<>();

            @Override
            public void onContent(Response response, ByteBuffer content) {
                byte[] bytes = new byte[content.remaining()];
                content.get(bytes);
                chunks.add(bytes);
            }

            @Override
            public void onSuccess(Response response) {
                assertThat(chunks, contains(new byte[] { 0x00 }, new byte[] { 0x01 }, new byte[] { 0x02 }));
                performer.start();
            }
        })
        .send();
    }

    @Test
    public void end() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                http.end();
            }
        })
        .responseListener(new Response.Listener.Adapter() {
            @Override
            public void onSuccess(Response response) {
                performer.start();
            }
        })
        .send();
    }

    @Test
    public void end_text_data() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                http.end("Out of existence");
            }
        })
        .responseListener(new Response.Listener.Adapter() {
            String body;

            @Override
            public void onContent(Response response, ByteBuffer content) {
                body = Charset.forName("iso-8859-1").decode(content).toString();
            }

            @Override
            public void onSuccess(Response response) {
                assertThat(body, is("Out of existence"));
                performer.start();
            }
        })
        .send();
    }

    @Test
    public void end_text_data_charset() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                http.end("Out of existence", "utf-8");
            }
        })
        .responseListener(new Response.Listener.Adapter() {
            String body;

            @Override
            public void onContent(Response response, ByteBuffer content) {
                body = Charset.forName("utf-8").decode(content).toString();
            }

            @Override
            public void onSuccess(Response response) {
                assertThat(body, is("Out of existence"));
                performer.start();
            }
        })
        .send();
    }

    @Test
    public void end_binary_data() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                http.end(ByteBuffer.wrap(new byte[] { 'h', 'i' }));
            }
        })
        .responseListener(new Response.Listener.Adapter() {
            byte[] body;

            @Override
            public void onContent(Response response, ByteBuffer content) {
                body = new byte[content.remaining()];
                content.get(body);
            }

            @Override
            public void onSuccess(Response response) {
                assertThat(body, is(new byte[] { 'h', 'i' }));
                performer.start();
            }
        })
        .send();
    }

    @Test
    public void closeAction_response_end_request_end() {
        final AtomicBoolean reqEnded = new AtomicBoolean();
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                http.end().endAction(new VoidAction() {
                    @Override
                    public void on() {
                        reqEnded.set(true);
                    }
                })
                .read().closeAction(new VoidAction() {
                    @Override
                    public void on() {
                        assertThat(reqEnded.get(), is(true));
                        performer.start();
                    }
                });
            }
        })
        .send(new Action<Request>() {
            @Override
            public void on(final Request req) {
                new Timer(true).schedule(new TimerTask() {
                    @Override
                    public void run() {
                        req.content(new StringContentProvider("Day 2: The Day Before"));
                    }
                }, 1000);
            }
        });
    }

    @Test
    public void closeAction_request_end_response_end() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(final ServerHttpExchange http) {
                http.endAction(new VoidAction() {
                    @Override
                    public void on() {
                        http.end().closeAction(new VoidAction() {
                            @Override
                            public void on() {
                                performer.start();
                            }
                        });
                    }
                })
                .read();
            }
        })
        .send(new Action<Request>() {
            @Override
            public void on(final Request req) {
                new Timer(true).schedule(new TimerTask() {
                    @Override
                    public void run() {
                        req.content(new StringContentProvider("Day 2: The Day Before"));
                    }
                }, 1000);
            }
        });
    }

    @Test
    public void closeAction_abnormal() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                http.closeAction(new VoidAction() {
                    @Override
                    public void on() {
                        performer.start();
                    }
                });
            }
        })
        .send(new Action<Request>() {
            @Override
            public void on(final Request req) {
                new Timer(true).schedule(new TimerTask() {
                    @Override
                    public void run() {
                        req.abort(new RuntimeException());
                    }
                }, 1000);
            }
        });
    }

    // TODO
    // Now errorAction depends on the underlying platform so that it's not easy
    // to test. However, with the consistent exception hierarchy, it might be
    // possible in the future.

    protected class Performer {

        CountDownLatch latch = new CountDownLatch(1);
        Request.Listener requestListener = new Request.Listener.Adapter();
        Response.Listener responseListener = new Response.Listener.Adapter();
        Action<ServerHttpExchange> serverAction = new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange object) {
            }
        };

        public Performer requestListener(Request.Listener requestListener) {
            this.requestListener = requestListener;
            return this;
        }

        public Performer responseListener(Response.Listener responseListener) {
            this.responseListener = responseListener;
            return this;
        }

        public Action<ServerHttpExchange> serverAction() {
            return serverAction;
        }

        public Performer serverAction(Action<ServerHttpExchange> serverAction) {
            this.serverAction = serverAction;
            return this;
        }

        public Performer send() {
            return send("/test");
        }

        public Performer send(Action<Request> requestAction) {
            return send("/test", requestAction);
        }

        public Performer send(String uri) {
            return send(uri, null);
        }

        public Performer send(String uri, Action<Request> requestAction) {
            HttpClient client = new HttpClient();
            try {
                client.start();
                Request req = client.newRequest("http://localhost:" + port + uri);
                if (requestAction != null) {
                    requestAction.on(req);
                }
                req.listener(requestListener).send(responseListener);
                latch.await();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    client.stop();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return this;
        }

        public Performer start() {
            latch.countDown();
            return this;
        }

    }

}
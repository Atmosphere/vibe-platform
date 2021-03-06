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
package org.atmosphere.vibe.platform.bridge.vertx2;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

import org.atmosphere.vibe.platform.action.Action;
import org.atmosphere.vibe.platform.http.AbstractServerHttpExchange;
import org.atmosphere.vibe.platform.http.HttpStatus;
import org.atmosphere.vibe.platform.http.ServerHttpExchange;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;

/**
 * {@link ServerHttpExchange} for Vert.x 2.
 *
 * @author Donghwan Kim
 */
public class VertxServerHttpExchange extends AbstractServerHttpExchange {

    private final HttpServerRequest request;
    private final HttpServerResponse response;

    public VertxServerHttpExchange(HttpServerRequest request) {
        this.request = request;
        this.response = request.response();
        request.exceptionHandler(new Handler<Throwable>() {
            @Override
            public void handle(Throwable t) {
                errorActions.fire(t);
            }
        });
        response.exceptionHandler(new Handler<Throwable>() {
            @Override
            public void handle(Throwable t) {
                errorActions.fire(t);
            }
        })
        .closeHandler(new VoidHandler() {
            @Override
            protected void handle() {
                closeActions.fire();
            }
        })
        .setChunked(true);
    }

    @Override
    public String uri() {
        return request.uri();
    }

    @Override
    public String method() {
        return request.method();
    }

    @Override
    public Set<String> headerNames() {
        return request.headers().names();
    }

    @Override
    public List<String> headers(String name) {
        return request.headers().getAll(name);
    }
    
    @Override
    protected void doRead(final Action<ByteBuffer> chunkAction) {
        request.dataHandler(new Handler<Buffer>() {
            @Override
            public void handle(Buffer body) {
                chunkAction.on(body.getByteBuf().nioBuffer());
            }
        })
        .endHandler(new VoidHandler() {
            @Override
            protected void handle() {
                endActions.fire();
            }
        });
    }

    @Override
    protected void doSetStatus(HttpStatus status) {
        response.setStatusCode(status.code()).setStatusMessage(status.reason());
    }

    @Override
    protected void doSetHeader(String name, String value) {
        response.putHeader(name, value);
    }

    @Override
    protected void doWrite(ByteBuffer byteBuffer) {
        response.write(new Buffer().setBytes(0, byteBuffer));
    }

    @Override
    protected void doEnd() {
        response.end();
    }

    /**
     * {@link HttpServerRequest} is available.
     */
    @Override
    public <T> T unwrap(Class<T> clazz) {
        return HttpServerRequest.class.isAssignableFrom(clazz) ? clazz.cast(request) : null;
    }

}

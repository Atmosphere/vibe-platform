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
package org.atmosphere.vibe.platform.server.netty4;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;

import org.atmosphere.vibe.platform.HttpStatus;
import org.atmosphere.vibe.platform.server.AbstractServerHttpExchange;
import org.atmosphere.vibe.platform.server.ServerHttpExchange;

/**
 * {@link ServerHttpExchange} for Netty 4.
 *
 * @author Donghwan Kim
 */
public class NettyServerHttpExchange extends AbstractServerHttpExchange {

    private final ChannelHandlerContext context;
    private final HttpRequest request;
    private final HttpResponse response;
    private boolean written;
    private Charset charset;

    public NettyServerHttpExchange(ChannelHandlerContext context, HttpRequest request) {
        this.context = context;
        this.request = request;
        this.response = new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.OK, false);
        response.headers().set(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
    }

    void handleError(Throwable cause) {
        errorActions.fire(cause);
    }

    void handleClose() {
        closeActions.fire();
    }

    @Override
    public String uri() {
        return request.getUri();
    }

    @Override
    public String method() {
        return request.getMethod().toString();
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
    protected void readAsText() {
        // HTTP 1.1 says that the default charset is ISO-8859-1
        // http://www.w3.org/International/O-HTTP-charset#charset
        String charsetName = "ISO-8859-1";
        String contentType = request.headers().get("content-type");
        if (contentType != null) {
            int idx = contentType.indexOf("charset=");
            if (idx != -1) {
                charsetName = contentType.substring(idx + "charset=".length());
            }
        }
        charset = Charset.forName(charsetName);
    }

    @Override
    protected void readAsBinary() {}

    void handleChunk(HttpContent chunk) {
        // To determine whether to read as text or binary
        read();
        ByteBuf buf = chunk.content();
        if (buf.isReadable()) {
            if (charset != null) {
                chunkActions.fire(buf.toString(charset));
            } else {
                chunkActions.fire(buf.nioBuffer());
            }
        }
        if (chunk instanceof LastHttpContent) {
            endActions.fire();
        }
    }

    @Override
    protected void doSetStatus(HttpStatus status) {
        response.setStatus(new HttpResponseStatus(status.code(), status.reason()));
    }

    @Override
    protected void doSetHeader(String name, String value) {
        response.headers().set(name, value);
    }

    @Override
    protected void doWrite(String data) {
        byte[] bytes = data.getBytes(Charset.forName("UTF-8"));
        ByteBuf buf = Unpooled.unreleasableBuffer(Unpooled.buffer(bytes.length)).writeBytes(bytes);
        doWrite(buf);
    }

    @Override
    protected void doWrite(ByteBuffer byteBuffer) {
        ByteBuf buf = Unpooled.unreleasableBuffer(Unpooled.wrappedBuffer(byteBuffer));
        doWrite(buf);
    }

    private void doWrite(ByteBuf buf) {
        if (!written) {
            written = true;
            context.write(response);
        }
        context.writeAndFlush(buf);
    }

    @Override
    protected void doEnd() {
        if (!written) {
            written = true;
            context.write(response);
        }
        context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        return ChannelHandlerContext.class.isAssignableFrom(clazz) ? 
            clazz.cast(context) : 
            HttpRequest.class.isAssignableFrom(clazz) ? 
                clazz.cast(request) : 
                HttpResponse.class.isAssignableFrom(clazz) ?
                    clazz.cast(response) :
                    null;
    }

}
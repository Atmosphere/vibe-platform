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
package org.atmosphere.vibe.platform.bridge.atmosphere2;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;

import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResourceEventListenerAdapter;
import org.atmosphere.vibe.platform.websocket.AbstractServerWebSocket;
import org.atmosphere.vibe.platform.websocket.ServerWebSocket;
import org.atmosphere.websocket.WebSocketEventListenerAdapter;

/**
 * {@link ServerWebSocket} for Atmosphere 2.
 *
 * @author Donghwan Kim
 */
public class AtmosphereServerWebSocket extends AbstractServerWebSocket {

    private final AtmosphereResource resource;

    public AtmosphereServerWebSocket(AtmosphereResource resource) {
        this.resource = resource;
        // Uses AtmosphereResourceEventListener because onClose and onDisconnect
        // on WebSocketEventListener are not called. It will be fixed in 2.2.2.
        resource.addEventListener(new AtmosphereResourceEventListenerAdapter() {
            @Override
            public void onClose(AtmosphereResourceEvent event) {
                closeActions.fire();
            }

            @Override
            public void onDisconnect(AtmosphereResourceEvent event) {
                closeActions.fire();
            }

            @Override
            public void onThrowable(AtmosphereResourceEvent event) {
                errorActions.fire(event.throwable());
            }
        })
        .addEventListener(new WebSocketEventListenerAdapter() {
            @SuppressWarnings("rawtypes")
            @Override
            public void onMessage(WebSocketEvent event) {
                Object message = event.message();
                if (message instanceof String) {
                    textActions.fire((String) message);
                } else if (message instanceof byte[]) {
                    binaryActions.fire(ByteBuffer.wrap((byte[]) message));
                } else {
                    // Just to be sure
                    errorActions.fire(new IllegalStateException("Message is neither String nor byte[]"));
                }
            }
        });
    }

    @Override
    public String uri() {
        String uri = resource.getRequest().getRequestURI();
        if (resource.getRequest().getQueryString() != null) {
            uri += "?" + resource.getRequest().getQueryString();
        }
        return uri;
    }

    @Override
    protected void doSend(String data) {
        resource.forceBinaryWrite(false);
        try {
            PrintWriter writer = resource.getResponse().getWriter();
            writer.print(data);
            writer.flush();
        } catch (IOException e) {
            errorActions.fire(e);
        }
    }

    @Override
    protected void doSend(ByteBuffer byteBuffer) {
        resource.forceBinaryWrite(true);
        try {
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            OutputStream outputStream = resource.getResponse().getOutputStream();
            outputStream.write(bytes);
            outputStream.flush();
        } catch (IOException e) {
            errorActions.fire(e);
        }
    }

    @Override
    protected void doClose() {
        try {
            resource.close();
        } catch (IOException e) {
            errorActions.fire(e);
        }
    }

    /**
     * {@link AtmosphereResource} is available.
     */
    @Override
    public <T> T unwrap(Class<T> clazz) {
        return AtmosphereResource.class.isAssignableFrom(clazz) ? clazz.cast(resource) : null;
    }

}

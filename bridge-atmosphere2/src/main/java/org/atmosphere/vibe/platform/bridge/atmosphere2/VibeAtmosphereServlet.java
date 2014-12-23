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

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.handler.AtmosphereHandlerAdapter;
import org.atmosphere.vibe.platform.action.Action;
import org.atmosphere.vibe.platform.http.ServerHttpExchange;
import org.atmosphere.vibe.platform.ws.ServerWebSocket;

/**
 * Servlet to process {@link AtmosphereResource} into {@link ServerHttpExchange}
 * and {@link ServerWebSocket}. When you configure servlet, you must set
 * <strong><code>asyncSupported</code></strong> to <strong><code>true</code>
 * </strong> and set a init param, <strong>
 * <code>org.atmosphere.cpr.AtmosphereInterceptor.disableDefaults</code>
 * </strong>, to <strong><code>true</code></strong>.
 * <p>
 * 
 * <pre>
 * ServletRegistration.Dynamic reg = context.addServlet(VibeAtmosphereServlet.class.getName(), new VibeAtmosphereServlet() {
 *     {@literal @}Override
 *     protected Action&ltServerHttpExchange&gt httpAction() {
 *         return server.httpAction();
 *     }
 *     
 *     {@literal @}Override
 *     protected Action&ltServerWebSocket&gt wsAction() {
 *         return server.wsAction();
 *     }
 * });
 * <strong>reg.setAsyncSupported(true);</strong>
 * <strong>reg.setInitParameter(ApplicationConfig.DISABLE_ATMOSPHEREINTERCEPTOR, Boolean.TRUE.toString())</strong>
 * reg.addMapping("/vibe");
 * </pre>
 *
 * @author Donghwan Kim
 */
@SuppressWarnings("serial")
public abstract class VibeAtmosphereServlet extends AtmosphereServlet {

    @Override
    public void init(ServletConfig sc) throws ServletException {
        super.init(sc);
        framework.addAtmosphereHandler("/", new AtmosphereHandlerAdapter() {
            @Override
            public void onRequest(AtmosphereResource resource) throws IOException {
                if (isWebSocketResource(resource)) {
                    if (resource.getRequest().getMethod().equals("GET")) {
                        wsAction().on(new AtmosphereServerWebSocket(resource));
                    }
                } else {
                    httpAction().on(new AtmosphereServerHttpExchange(resource));
                }
            }
        });
    }

    /**
     * Does the given {@link AtmosphereResource} represent WebSocket resource?
     */
    protected boolean isWebSocketResource(AtmosphereResource resource) {
        // AtmosphereResponse as HttpServletResponseWrapper returns itself on
        // its getResponse method when there was no instance of ServletResponse
        // given by the container. That's exactly the case of WebSocket.
        return resource.getResponse().getResponse() instanceof AtmosphereResponse;
    }

    /**
     * An {@link Action} to consume {@link ServerHttpExchange}.
     */
    protected abstract Action<ServerHttpExchange> httpAction();

    /**
     * An {@link Action} to consume {@link ServerWebSocket}.
     */
    protected abstract Action<ServerWebSocket> wsAction();

}
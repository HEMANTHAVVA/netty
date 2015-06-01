/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * Configures a {@link ChannelPipeline} depending on the application-level protocol negotiation result of
 * {@link SslHandler}.  For example, you could configure your HTTP pipeline depending on the result of ALPN:
 * <pre>
 * public class MyInitializer extends {@link ChannelInitializer}&lt;{@link Channel}&gt; {
 *     private final {@link SslContext} sslCtx;
 *
 *     public MyInitializer({@link SslContext} sslCtx) {
 *         this.sslCtx = sslCtx;
 *     }
 *
 *     protected void initChannel({@link Channel} ch) {
 *         {@link ChannelPipeline} p = ch.pipeline();
 *         p.addLast(sslCtx.newHandler(...)); // Adds {@link SslHandler}
 *         p.addLast(new MyNegotiationHandler());
 *     }
 * }
 *
 * public class MyNegotiationHandler extends {@link ApplicationProtocolNegotiationHandler} {
 *     public MyNegotiationHandler() {
 *         super("http/1.1");
 *     }
 *
 *     protected void configurePipeline({@link ChannelHandlerContext} ctx, String protocol) {
 *         if ("h2".equals(protocol) {
 *             configureHttp2(ctx);
 *         } else if ("http/1.1".equals(protocol)) {
 *             configureHttp1(ctx);
 *         } else {
 *             throw new IllegalStateException("unknown protocol: " + protocol);
 *         }
 *     }
 * }
 * </pre>
 */
public abstract class ApplicationProtocolNegotiationHandler extends ChannelInboundHandlerAdapter {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(ApplicationProtocolNegotiationHandler.class);

    private final String fallbackProtocol;
    private SslHandler sslHandler;
    private boolean configured;

    /**
     * Creates a new instance with the specified fallback protocol name.
     *
     * @param fallbackProtocol the name of the protocol to use when
     *                         ALPN/NPN negotiation fails or the client does not support ALPN/NPN
     */
    protected ApplicationProtocolNegotiationHandler(String fallbackProtocol) {
        this.fallbackProtocol = ObjectUtil.checkNotNull(fallbackProtocol, "fallbackProtocol");
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        // FIXME: There is no way to tell if the SSL handler is placed before the negotiation handler.
        final SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
        if (sslHandler == null) {
            throw new IllegalStateException(
                    "cannot find a SslHandler in the pipeline (required for application-level protocol negotiation)");
        }

        this.sslHandler = sslHandler;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (!configured && evt instanceof SslHandshakeCompletionEvent) {
            configured = true;

            SslHandshakeCompletionEvent handshakeEvent = (SslHandshakeCompletionEvent) evt;
            if (handshakeEvent.isSuccess()) {
                ctx.pipeline().remove(this);

                String protocol = sslHandler.applicationProtocol();
                configurePipeline(ctx, protocol != null? protocol : fallbackProtocol);
            }
        }

        ctx.fireUserEventTriggered(evt);
    }

    /**
     * Invoked on successful initial SSL/TLS handshake. Implement this method to configure your pipeline
     * for the negotiated application-level protocol.
     *
     * @param protocol the name of the negotiated application-level protocol, or
     *                 the fallback protocol name specified in the constructor call if negotiation failed or the client
     *                 isn't aware of ALPN/NPN extension
     */
    protected abstract void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception;

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (!configured) {
            logger.warn("{} Failed to select the application-level protocol:", ctx.channel(), cause);
            ctx.close();
        }
    }
}

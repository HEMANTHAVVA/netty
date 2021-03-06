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
package io.netty.channel.epoll;

import static org.junit.Assert.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;

import java.net.InetSocketAddress;
import java.util.Random;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class EpollSocketChannelConfigTest {

    private static EventLoopGroup group;
    private static EpollSocketChannel ch;
    private static Random rand;

    @BeforeClass
    public static void before() {
        rand = new Random();
        group = new EpollEventLoopGroup(1);
        Bootstrap bootstrap = new Bootstrap();
        ch = (EpollSocketChannel) bootstrap.group(group)
                .channel(EpollSocketChannel.class)
                .handler(new ChannelInboundHandlerAdapter())
                .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();
    }

    @AfterClass
    public static void after() {
        group.shutdownGracefully();
    }

    private long randLong(long min, long max) {
        return min + nextLong(max - min + 1);
    }

    private long nextLong(long n) {
        long bits, val;
        do {
           bits = (rand.nextLong() << 1) >>> 1;
           val = bits % n;
        } while (bits - val + (n - 1) < 0L);
        return val;
     }

    @Test
    public void testRandomTcpNotSentLowAt() {
        final long value = randLong(0, 0xFFFFFFFFL);
        ch.config().setTcpNotSentLowAt(value);
        assertEquals(value, ch.config().getTcpNotSentLowAt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidHighTcpNotSentLowAt() {
        final long value = 0xFFFFFFFFL + 1;
        ch.config().setTcpNotSentLowAt(value);
        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidLowTcpNotSentLowAt() {
        final long value = -1;
        ch.config().setTcpNotSentLowAt(value);
        fail();
    }

    @Test
    public void testTcpCork() {
        ch.config().setTcpCork(false);
        assertFalse(ch.config().isTcpCork());
        ch.config().setTcpCork(true);
        assertTrue(ch.config().isTcpCork());
    }
}

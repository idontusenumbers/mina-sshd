/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sshd;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.apache.sshd.ClientChannel;
import org.apache.sshd.ClientSession;
import org.apache.sshd.SshClient;
import org.apache.sshd.SshServer;
import org.apache.sshd.client.kex.DHG1;
import org.apache.sshd.common.Cipher;
import org.apache.sshd.common.KeyExchange;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.cipher.BlowfishCBC;
import org.apache.sshd.util.BogusPasswordAuthenticator;
import org.apache.sshd.util.EchoShellFactory;
import org.apache.sshd.util.TeeOutputStream;
import org.apache.sshd.util.Utils;
import org.junit.After;

import static org.junit.Assert.assertArrayEquals;
import org.junit.Before;
import org.junit.Test;

public class LoadTest {

    private SshServer sshd;
    private int port;

    @Before
    public void setUp() throws Exception {
        port = Utils.getFreePort();

        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(port);
        sshd.setKeyPairProvider(Utils.createTestHostKeyProvider());
        sshd.setShellFactory(new EchoShellFactory());
        sshd.setPasswordAuthenticator(new BogusPasswordAuthenticator());
        sshd.start();
    }

    @After
    public void tearDown() throws Exception {
        sshd.stop();
    }

    @Test
    public void testLoad() throws Exception {
        test("this is my command", 4, 4);
    }

    @Test
    public void testHighLoad() throws Exception {
        final StringBuilder response = new StringBuilder(1000000);
        for (int i = 0; i < 100000; i++) {
            response.append("0123456789");
        }
        test(response.toString(), 1, 100);
    }

    @Test
    public void testBigResponse() throws Exception {
        final StringBuilder response = new StringBuilder(1000000);
        for (int i = 0; i < 100000; i++) {
            response.append("0123456789");
        }
        test(response.toString(), 1, 1);
    }

    protected void test(final String msg, final int nbThreads, final int nbSessionsPerThread) throws Exception {
        final List<Throwable> errors = new ArrayList<Throwable>();
        final CountDownLatch latch = new CountDownLatch(nbThreads);
        for (int i = 0; i < nbThreads; i++) {
            Runnable r = new Runnable() {
                public void run() {
                    try {
                        for (int i = 0; i < nbSessionsPerThread; i++) {
                            runClient(msg);
                        }
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        latch.countDown();
                    }
                }
            };
            new Thread(r).start();
        }
        latch.await();
        if (errors.size() > 0) {
            throw new Exception("Errors", errors.get(0));
        }
    }

    protected void runClient(String msg) throws Exception {
        SshClient client = SshClient.setUpDefaultClient();
        client.getProperties().put(SshClient.MAX_PACKET_SIZE, Integer.toString(1024 * 16));
        client.getProperties().put(SshClient.WINDOW_SIZE, Integer.toString(1024 * 8));
        client.setKeyExchangeFactories(Arrays.<NamedFactory<KeyExchange>>asList(
                new DHG1.Factory()));
        client.setCipherFactories(Arrays.<NamedFactory<Cipher>>asList(
                new BlowfishCBC.Factory()));
        client.start();
        ClientSession session = client.connect("localhost", port).await().getSession();
        session.authPassword("sshd", "sshd").await().isSuccess();

        ClientChannel channel = session.createChannel(ClientChannel.CHANNEL_SHELL);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        channel.setOut(out);
        channel.setErr(err);
        channel.open().await();
        OutputStream pipedIn = channel.getInvertedIn();

        msg += "\nexit\n";
        pipedIn.write(msg.getBytes());
        pipedIn.flush();

        channel.waitFor(ClientChannel.CLOSED, 0);

        channel.close(false);
        client.stop();

        assertArrayEquals(msg.getBytes(), out.toByteArray());
    }
}

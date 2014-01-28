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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import com.jcraft.jsch.JSch;
import org.apache.sshd.common.Cipher;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.Random;
import org.apache.sshd.common.cipher.AES128CBC;
import org.apache.sshd.common.cipher.AES192CBC;
import org.apache.sshd.common.cipher.AES256CBC;
import org.apache.sshd.common.cipher.BlowfishCBC;
import org.apache.sshd.common.cipher.TripleDESCBC;
import org.apache.sshd.common.random.BouncyCastleRandom;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.util.BogusPasswordAuthenticator;
import org.apache.sshd.util.EchoShellFactory;
import org.apache.sshd.util.JSchLogger;
import org.apache.sshd.util.SimpleUserInfo;
import org.apache.sshd.util.Utils;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test Cipher algorithms.
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class CipherTest {

    private SshServer sshd;
    private int port;

    @Test
    public void testAES128CBC() throws Exception {
        setUp(new AES128CBC.Factory());
        runTest();
    }

    @Test
    public void testAES192CBC() throws Exception {
        if (SecurityUtils.isBouncyCastleRegistered()) {
            setUp(new AES192CBC.Factory());
            runTest();
        }
    }

    @Test
    public void testAES256CBC() throws Exception {
        if (SecurityUtils.isBouncyCastleRegistered()) {
            setUp(new AES256CBC.Factory());
            runTest();
        }
    }

    @Test
    public void testBlowfishCBC() throws Exception {
        setUp(new BlowfishCBC.Factory());
        runTest();
    }

    @Test
    public void testTripleDESCBC() throws Exception {
        setUp(new TripleDESCBC.Factory());
        runTest();
    }

    @Test
    public void loadTest() throws Exception {
        Random random = new BouncyCastleRandom();
        loadTest(new AES128CBC.Factory(), random);
        loadTest(new BlowfishCBC.Factory(), random);
        loadTest(new TripleDESCBC.Factory(), random);
    }

    protected void loadTest(NamedFactory<Cipher> factory, Random random) throws Exception {
        Cipher cipher = factory.create();
        byte[] key = new byte[cipher.getBlockSize()];
        byte[] iv = new byte[cipher.getIVSize()];
        random.fill(key, 0, key.length);
        random.fill(iv, 0, iv.length);
        cipher.init(Cipher.Mode.Encrypt, key, iv);

        byte[] input = new byte[cipher.getBlockSize()];
        random.fill(input, 0, input.length);
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            cipher.update(input, 0, input.length);
        }
        long t1 = System.currentTimeMillis();
        System.err.println(factory.getName() + ": " + (t1 - t0) + " ms");
    }


    protected void setUp(NamedFactory<org.apache.sshd.common.Cipher> cipher) throws Exception {
        port = Utils.getFreePort();

        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(port);
        sshd.setKeyPairProvider(Utils.createTestHostKeyProvider());
        sshd.setCipherFactories(Arrays.<NamedFactory<org.apache.sshd.common.Cipher>>asList(cipher));
        sshd.setShellFactory(new EchoShellFactory());
        sshd.setPasswordAuthenticator(new BogusPasswordAuthenticator());
        sshd.start();
    }

    @After
    public void tearDown() throws Exception {
        if (sshd != null) {
            sshd.stop();
        }
    }

    protected void runTest() throws Exception {
        JSchLogger.init();
        JSch sch = new JSch();
        JSch.setConfig("cipher.s2c", "aes128-cbc,3des-cbc,blowfish-cbc,aes192-cbc,aes256-cbc,none");
        JSch.setConfig("cipher.c2s", "aes128-cbc,3des-cbc,blowfish-cbc,aes192-cbc,aes256-cbc,none");
        com.jcraft.jsch.Session s = sch.getSession("smx", "localhost", port);
        s.setUserInfo(new SimpleUserInfo("smx"));
        s.connect();
        com.jcraft.jsch.Channel c = s.openChannel("shell");
        c.connect();
        OutputStream os = c.getOutputStream();
        InputStream is = c.getInputStream();
        for (int i = 0; i < 10; i++) {
            os.write("this is my command\n".getBytes());
            os.flush();
            byte[] data = new byte[512];
            int len = is.read(data);
            String str = new String(data, 0, len);
            assertEquals("this is my command\n", str);
        }
        c.disconnect();
        s.disconnect();
    }
}

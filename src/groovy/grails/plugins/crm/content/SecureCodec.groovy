/*
 * Copyright (c) 2014 Goran Ehrsson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugins.crm.content

import groovy.transform.CompileStatic

import javax.crypto.spec.SecretKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import sun.misc.BASE64Encoder
import sun.misc.BASE64Decoder
import javax.crypto.CipherOutputStream
import javax.crypto.CipherInputStream

/**
 * Used for encrypting things to store in the database.
 */
class SecureCodec {

    static final BASE64Decoder decoder = new BASE64Decoder()
    static final BASE64Encoder encoder = new BASE64Encoder()

    static final Closure encode = { final String str ->
        Cipher cipher = setupCipher(Cipher.ENCRYPT_MODE, getPassword())
        return encoder.encode(cipher.doFinal(str.getBytes()))
    }

    static final Closure decode = { final String str ->
        final Cipher cipher = setupCipher(Cipher.DECRYPT_MODE, getPassword())
        return new String(cipher.doFinal(decoder.decodeBuffer(str)))
    }

    @CompileStatic
    static void encodeFile(final File inFile, final File outFile) {
        final Cipher cipher = setupCipher(Cipher.ENCRYPT_MODE, getPassword())
        inFile.withInputStream {final InputStream is ->
            final CipherOutputStream cos = new CipherOutputStream(outFile.newOutputStream(), cipher)
            final byte[] buffer = new byte[2048]
            int bytesRead
            while ((bytesRead = is.read(buffer)) != -1) {
                cos.write(buffer, 0, bytesRead);
            }
            cos.close()
        }
    }

    @CompileStatic
    static void decodeFile(final File inFile, final File outFile) {
        final Cipher cipher = setupCipher(Cipher.DECRYPT_MODE, getPassword())
        final InputStream inputStream = new CipherInputStream(inFile.newInputStream(), cipher)
        try {
            outFile.withOutputStream {final OutputStream os ->
                os << inputStream
            }
        } finally {
            inputStream.close()
        }
    }

    static String getPassword() {
        def s = grails.util.Holders.config.crm.content.encryption.password
        if(!s) {
            throw new IllegalStateException("No password configured for SecureCodec")
        }
        return s.toString()
    }

    @CompileStatic
    private static Cipher setupCipher(final int mode, final String password) {
        final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

        // setup a 128 bit key
        final byte[] keyBytes = new byte[16]
        final byte[] b = password.getBytes("UTF-8")
        int len = b.length
        if (len > keyBytes.length)
            len = keyBytes.length
        System.arraycopy(b, 0, keyBytes, 0, len)
        final SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES")

        final IvParameterSpec ivSpec = new IvParameterSpec(keyBytes)
        cipher.init(mode, keySpec, ivSpec)
        return cipher
    }
}

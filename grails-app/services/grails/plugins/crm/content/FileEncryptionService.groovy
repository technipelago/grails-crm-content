/*
 * Copyright 2013 Goran Ehrsson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugins.crm.content

/**
 * File encryption service.
 */
class FileEncryptionService {

    static transactional = false

    /**
     * Encrypt file.
     *
     * @param inFile the unencrypted file to encrypt
     * @param outFile a new file with the same content but encrypted
     * @param algorithm encryption algorithm
     */
    void encrypt(File inFile, File outFile, Integer algorithm = 0) {
        switch (algorithm) {
            case CrmFileResource.AES_ENCRYPTION:
                SecureCodec.encodeFile(inFile, outFile)
                break
            case CrmFileResource.NO_ENCRYPTION:
                outFile.withOutputStream {out ->
                    inFile.withInputStream {is ->
                        out << is
                    }
                }
                break
            default:
                throw new IllegalArgumentException("Unsupported encryption: " + algorithm)
        }
    }

    /**
     * Decrypt file.
     *
     * @param inFile the encrypted file to decrypt
     * @param outFile a new file with unencrypted content
     * @param algorithm encryption algorithm
     */
    void decrypt(File inFile, File outFile, Integer algorithm = 0) {
        switch (algorithm) {
            case CrmFileResource.AES_ENCRYPTION:
                SecureCodec.decodeFile(inFile, outFile)
                break
            case CrmFileResource.NO_ENCRYPTION:
                outFile.withOutputStream {out ->
                    inFile.withInputStream {is ->
                        out << is
                    }
                }
                break
            default:
                throw new IllegalArgumentException("Unsupported encryption: " + algorithm)
        }
    }
}

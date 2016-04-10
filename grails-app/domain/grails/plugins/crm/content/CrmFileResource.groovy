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

import java.security.MessageDigest

class CrmFileResource {

    public static final Integer NO_ENCRYPTION = 0
    public static final Integer AES_ENCRYPTION = 1

    String contentType
    Long length // The unencrypted length
    String hash
    Date dateCreated
    Date lastUpdated
    int encrypted

    // Lazy initialized (see getter below)
    private def _fileEncryptionService

    static constraints = {
        contentType(maxSize: 80, blank: false)
        lastUpdated(nullable: true)
        hash(maxSize: 80, nullable: true)
    }
    static mapping = {
        cache usage: 'read-write'
    }
    static transients = ['contentRoot', 'reader', 'md5']

    def afterDelete() {
        File f = getRawFile()
        if (f.exists() && !f.isDirectory()) {
            f.delete()
        }
    }

    def afterUpdate() {
        hash = getMD5()
    }

    protected File getContentRoot() {
        new File(domainClass.grailsApplication.config.crm.content.file.path ?: 'content-repository')
    }

    // Lazy injection of service.
    private def getFileEncryptionService() {
        if (_fileEncryptionService == null) {
            synchronized (this) {
                if (_fileEncryptionService == null) {
                    _fileEncryptionService = this.getDomainClass().getGrailsApplication().getMainContext().getBean('fileEncryptionService')
                }
            }
        }
        _fileEncryptionService
    }

    /**
     * Returns a File instance that points to the disk file.
     *
     * @return the file
     */
    private File getRawFile() {
        new File(getContentRoot(), getPath())
    }

    private File getFile() {
        if (!encrypted) {
            return getRawFile()
        }
        def decryptedFile = File.createTempFile("crm", ".tmp")
        decryptedFile.deleteOnExit() // If some client forget to remove it, we do it on shutdown.
        try {
            getFileEncryptionService().decrypt(getRawFile(), decryptedFile, encrypted)
        } catch (Exception e) {
            decryptedFile.delete()
            throw e
        }
        return decryptedFile
    }

    /**
     * Returns the relative disk path to the file resource.
     *
     * Path is built from domain instance primary key (id).
     * Example: ID 37621 has the path 0/3/7/6/2/1/37621.dat
     * @return relative disk path to the resource
     */
    private String getPath() {
        if (!id) {
            throw new IllegalStateException("File resource must be persistent before calling getPath()")
        }
        def s = String.format("%06d", (id / 1000).intValue())
        s.toList().join('/') + '/' + id + '.dat'
    }

    /**
     * Return a Reader ready to read content from this resource.
     *
     * @param charsetName
     * @return
     */
    Reader getReader(String charsetName = null) {
        def f = getFile()
        if (charsetName) {
            return encrypted ? new TemporaryFileReader(f, charsetName) : new InputStreamReader(f.newInputStream(), charsetName)
        }
        return encrypted ? new TemporaryFileReader(f) : new InputStreamReader(f.newInputStream())
    }

    /**
     * Read this resource content using a Closure.
     *
     * @param work
     */
    def withInputStream(Closure work) {
        def f = getFile()
        def rval
        try {
            rval = f.withInputStream(work)
        } finally {
            if (encrypted) {
                f.delete()
            }
        }
        rval
    }

    /**
     * Write the content of this resource to a stream.
     *
     * @param out the output stream to write to
     * @return number of bytes written
     */
    long writeTo(OutputStream out) {
        long len = 0L
        def f = getFile()
        try {
            len = f.length()
            f.withInputStream { is ->
                out << is
            }
            out.flush()
        } catch(IOException e) {
            log.error(e.cause?.message ?: e.message)
            len = 0L
        } finally {
            if (encrypted) {
                f.delete()
            }
        }
        return len
    }

    /**
     * Write this file's resource content using a Closure.
     *
     * @param work Closure to execute
     */
    void withOutputStream(Closure work) {
        encrypted = domainClass.grailsApplication.config.crm.content.encryption.algorithm ?: 0
        if (encrypted) {
            def tempFile = File.createTempFile("crm", ".tmp")
            tempFile.deleteOnExit()
            try {
                tempFile.withOutputStream(work)
                def f = getRawFile()
                f.parentFile.mkdirs()
                getFileEncryptionService().encrypt(tempFile, f, encrypted)
                length = tempFile.length() // The unencrypted length
            } finally {
                tempFile.delete()
            }
        } else {
            def f = getRawFile()
            def exists = f.exists()
            try {
                f.parentFile.mkdirs()
                f.withOutputStream(work)
                length = f.length()
            } catch (Exception e) {
                if (!exists) {
                    f.delete()
                }
                throw e
            }
        }
    }

    private String getMD5() {
        MessageDigest digest = MessageDigest.getInstance("MD5")
        withInputStream { is ->
            byte[] buffer = new byte[8192]
            int read = 0
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        new BigInteger(1, digest.digest()).toString(16)
    }

    String toString() {
        "${getClass().getSimpleName()}@$id ($contentType)"
    }
}

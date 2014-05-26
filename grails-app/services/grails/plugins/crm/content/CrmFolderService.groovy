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

import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream

import java.util.zip.ZipInputStream

/**
 * Folder functions.
 */
class CrmFolderService {

    def crmContentService

    void archive(Collection<CrmResourceRef> files, OutputStream out) {
        ZipOutputStream zos = new ZipOutputStream(out)
        /* ZipOutputStream from ANT (org.apache.tools.zip) allows setting of encoding.
         * Without this, filenames with scandinavian characters will be messed up.
         * This was not possible in Sun JDK until JDK7 so we use ANT's implementation here.
         * http://download.java.net/jdk7/docs/api/java/util/zip/ZipFile.html#ZipFile%28java.io.File,%20java.nio.charset.Charset%29
         */
        zos.encoding = "Cp437"
        try {
            for (file in files) {
                zos.putNextEntry(new ZipEntry(file.name))
                file.writeTo(zos)
                zos.closeEntry()
            }
        } finally {
            zos.close()
        }
    }

    def archive(CrmResourceFolder crmResourceFolder, OutputStream out) {
        ZipOutputStream zos = new ZipOutputStream(out)
        /* ZipOutputStream from ANT (org.apache.tools.zip) allows setting of encoding.
         * Without this, filenames with scandinavian characters will be messed up.
         * This was not possible in Sun JDK until JDK7 so we use ANT's implementation here.
         * http://download.java.net/jdk7/docs/api/java/util/zip/ZipFile.html#ZipFile%28java.io.File,%20java.nio.charset.Charset%29
         */
        zos.encoding = "Cp437"
        try {
            final String root = crmContentService.getAbsolutePath(crmResourceFolder, false)
            addFolder(zos, crmResourceFolder, root + "/")
        } finally {
            zos.close()
        }
    }

    private void addFolder(ZipOutputStream zos, CrmResourceFolder folder, String stripPath = null) {
        for (f in folder.folders) {
            addFolder(zos, f)
        }
        for (res in folder.files) {
            def path = crmContentService.getAbsolutePath(res, false)
            if(stripPath) {
                path = path - stripPath
            }
            zos.putNextEntry(new ZipEntry(path))
            res.writeTo(zos)
            zos.closeEntry()
        }
    }

    void extract(InputStream inputStream, CrmResourceFolder parentFolder, Map params = [:]) {
        ZipInputStream zis = new ZipInputStream(inputStream)
        byte[] buffer = new byte[2048]
        try {
            java.util.zip.ZipEntry entry
            while ((entry = zis.getNextEntry()) != null) {
                // We must extract to a temp file because crmContentService.createResource() closes the input stream.
                File tempFile = File.createTempFile("crm", ".zip")
                tempFile.withOutputStream {out->
                    int len
                    while ((len = zis.read(buffer)) > 0)
                    {
                        out.write(buffer, 0, len)
                    }
                }
                tempFile.withInputStream {
                    crmContentService.createResource(it, entry.getName(), tempFile.length(), null, parentFolder, params)
                }
                tempFile.delete()
            }
        } finally {
            zis.close()
        }
        parentFolder.refresh()
    }
}

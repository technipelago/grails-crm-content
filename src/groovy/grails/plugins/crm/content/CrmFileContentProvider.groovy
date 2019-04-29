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

import grails.plugins.crm.core.WebUtils
import groovy.io.FileType
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

/**
 * A content provider that stores it's content in the application server file system.
 */
class CrmFileContentProvider implements CrmContentProvider {

    public static final String scheme = "file"
    public static final String host = "localhost"

    @CompileStatic
    @Override
    boolean handles(URI resourceURI) {
        resourceURI.scheme == scheme
    }

    @CompileStatic
    private CrmFileResource getResource(URI uri) {
        def resource = CrmFileResource.get(uri.path.substring(1))
        if (!resource) {
            throw new IllegalArgumentException("Resource not found [$uri]")
        }
        return resource
    }

    @Override
    Map<String, Object> create(InputStream content, String contentType, String name, String principal) {

        def resource = new CrmFileResource(length: 0L, contentType: contentType)

        // Save the domain instance so we get a primary key and can generate a filename.
        resource.save(failOnError: true, flush: true)

        try {
            // save file to disk
            resource.withOutputStream { out ->
                out << content
            }
        } finally {
            content.close()
        }

        // Force update so searchable can try to index it again.
        resource.save(failOnError: true, flush: true)

        getMetadataForResource(resource)
    }

    /**
     * Update existing content with new bytes.
     *
     * @param uri resource identifier
     * @param content new content, or null if only contentType should be changed
     * @param contentType new content type or null if no change
     * @return
     */
    @Override
    Map<String, Object> update(URI uri, InputStream content, String contentType) {
        def resource = getResource(uri)
        if (content != null) {
            try {
                resource.withOutputStream { out ->
                    out << content
                }
            } finally {
                content.close()
            }
        }
        // TODO: Should it be possible to change contentType without re-writing content???
        // What if different content types has different providers?
        // A safer solution is to always create a new resource (and copy bytes) if content type changes.
        if (contentType != null) {
            resource.contentType = contentType
        }
        resource.save(failOnError: true, flush: true)

        getMetadataForResource(resource)
    }

    @Override
    @CompileStatic
    Reader getReader(URI uri, String charsetName) {
        getResource(uri).getReader(charsetName)
    }

    @Override
    long read(OutputStream out, URI uri) {
        getResource(uri).writeTo(out)
    }

    @Override
    @CompileStatic
    def withInputStream(URI uri,
                        @ClosureParams(value = SimpleType.class, options = "java.io.InputStream") Closure work) {
        getResource(uri).withInputStream { inputStream ->
            work(inputStream)
        }
    }

    @Override
    boolean copy(URI from, URI to) {
        def fromResource = getResource(from)
        def toResource = getResource(to)
        toResource.withOutputStream { out ->
            fromResource.writeTo(out)
        }
        return true
    }

    @Override
    boolean delete(URI uri) {
        def resource = CrmFileResource.get(uri.path.substring(1))
        if (resource) {
            resource.delete(flush: true) // This will delete the physical file.
            return true
        }
        // If no CrmFileResource was found, try to delete the orphan file.
        File file = getFile(uri)
        if (file.exists() && !file.isDirectory()) {
            file.delete()
            return true
        }
        return false
    }

    private URI getUri(Long id) {
        new URI("$scheme://$host/$id")
    }

    // TODO duplicated code, part of it also appear in CrmFileResource.groovy
    private File getFile(URI uri) {
        def id = Long.valueOf(uri.path.substring(1))
        def s = String.format("%06d", (id / 1000).intValue())
        def path = s.toList().join('/') + '/' + id + '.dat'
        return new File(getContentRoot(), path)
    }

    // TODO duplicated code, part of it also appear in CrmFileResource.groovy
    private File getContentRoot() {
        def dummy = new CrmFileResource()
        def root = dummy.getContentRoot()
        dummy.discard()
        return root
    }

    /**
     * Check every file in the repository.
     * The specified closure will be called for every *physical* file in the repository.
     * If the closure returns true the file's size will be included in the total size returned by this method.
     * Example: long totalSize = check({true})
     *
     * @param worker a closure that will be called for every file in the repository, the file is the delegate and the closure parameter is the content URI of the file
     * @return total size (in byte) of all file checked with a positive result from the worker closure.
     */
    @Override
    long check(@ClosureParams(value = SimpleType.class, options = "java.net.URI") Closure<Boolean> worker) {
        long size = 0L
        File root = getContentRoot()
        if (root.exists() && root.isDirectory()) {
            def pattern = ~/^(\d+)\.dat$/
            root.eachFileRecurse(FileType.FILES) { File file ->
                def m = pattern.matcher(file.name)
                if (m.find()) {
                    long length = file.length()
                    def clone = worker.rehydrate(file, this, this)
                    clone.resolveStrategy = Closure.DELEGATE_FIRST
                    // TODO duplicated code, part of it also appear in CrmFileResource.groovy
                    if (clone.call(new URI("$scheme://$host/${m.group(1)}"))) {
                        size += length
                    }
                }
            }
        }
        return size
    }

    @Override
    @CompileStatic
    Map<String, Object> getMetadata(URI uri) {
        getMetadataForResource(getResource(uri))
    }

    @Override
    @CompileStatic
    long getLength(URI uri) {
        getResource(uri).length
    }

    @Override
    @CompileStatic
    long getLastModified(URI uri) {
        def resource = getResource(uri)
        (resource.lastUpdated ?: resource.dateCreated).time
    }

    boolean exists(URI uri) {
        final File file = getFile(uri)
        file.exists() && !file.isDirectory()
    }

    @CompileStatic
    private Map<String, Object> getMetadataForResource(CrmFileResource resource) {
        Map<String, Object> md = [:]
        md.uri = getUri(resource.id)
        md.contentType = resource.contentType
        md.bytes = resource.length
        md['size'] = WebUtils.bytesFormatted(resource.length)
        md.icon = 'page_white' // TODO implement mapping of content type or extension to icon
        md.created = resource.dateCreated
        md.modified = resource.lastUpdated ?: resource.dateCreated
        md.hash = resource.hash
        md.encrypted = resource.encrypted
        return md
    }
}

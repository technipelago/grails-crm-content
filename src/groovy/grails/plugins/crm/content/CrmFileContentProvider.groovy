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

import grails.plugins.crm.core.WebUtils

/**
 * A content provider that stores it's content in the application server file system.
 */
class CrmFileContentProvider implements CrmContentProvider {

    private static final String scheme = "file"
    private static final String host = "localhost"

    private CrmFileResource getResource(URI uri) {
        def resource = CrmFileResource.get(uri.path.substring(1))
        if (!resource) {
            throw new IllegalArgumentException("Resource not found [$uri]")
        }
        return resource
    }

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

    Reader getReader(URI uri, String charsetName) {
        getResource(uri).getReader(charsetName)
    }

    long read(OutputStream out, URI uri) {
        getResource(uri).writeTo(out)
    }

    def withInputStream(URI uri, Closure work) {
        getResource(uri).withInputStream { inputStream ->
            work(inputStream)
        }
    }

    boolean copy(URI from, URI to) {
        def fromResource = getResource(from)
        def toResource = getResource(to)
        toResource.withOutputStream { out ->
            fromResource.writeTo(out)
        }
        return true
    }

    boolean delete(URI uri) {
        getResource(uri).delete(flush: true)
        return true
    }

    Map<String, Object> getMetadata(URI uri) {
        getMetadataForResource(getResource(uri))
    }

    long getLength(URI uri) {
        getResource(uri).length
    }

    long getLastModified(URI uri) {
        def resource = getResource(uri)
        (resource.lastUpdated ?: resource.dateCreated).time
    }

    private Map<String, Object> getMetadataForResource(CrmFileResource resource) {
        def md = [:]
        md.uri = new URI("$scheme://$host/$resource.id")
        md.contentType = resource.contentType
        md.bytes = resource.length
        md.size = WebUtils.bytesFormatted(resource.length)
        md.icon = 'page_white' // TODO implement ext/icon mapping
        md.created = resource.dateCreated
        md.modified = resource.lastUpdated ?: resource.dateCreated
        md.hash = resource.hash
        md.encrypted = resource.encrypted
        return md
    }
}

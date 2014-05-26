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

package grails.plugins.crm.content;


import groovy.lang.Closure;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.util.Map;

/**
 * Content provider interface.
 */
public interface CrmContentProvider {

    /**
     * Write content to persistent storage.
     *
     * @param content     the content to persist
     * @param contentType MIME content type
     * @param name        name of content
     * @param principal   the caller security principal (username)
     * @return Metadata for the newly created resource
     */
    Map<String, Object> create(InputStream content, String contentType, String name, String principal);

    /**
     * Update existing resource with new content
     *
     * @param uri         resource identifier
     * @param content     new content
     * @param contentType new content type or null if no change
     * @return Metadata for the updated resource
     */
    Map<String, Object> update(URI uri, InputStream content, String contentType);

    /**
     * Returns a Reader prepared to read content from this resource.
     *
     * @param uri resource identifier
     * @param charsetName
     * @return Reader for this resource
     */
    Reader getReader(URI uri, String charsetName);

    /**
     * Read content.
     *
     * @param buf output stream to write content to
     * @param uri resource identifier
     * @return number of bytes written to output stream
     */
    long read(OutputStream buf, URI uri);

    /**
     * Execute Closure with resource's InputStream as parameter.
     *
     * @param uri  resource identifier
     * @param work the closure to call with input stream as parameter
     * @return what the closure returns
     */
    Object withInputStream(URI uri, Closure work);

    /**
     * Copy a resource.
     *
     * @param from the resource to copy
     * @param to   destination
     * @return true if resource was successfully copied
     */
    boolean copy(URI from, URI to);

    /**
     * Delete a resource.
     *
     * @param uri resource identifier
     * @return true if the resource was successfully deleted
     */
    boolean delete(URI uri);

    /**
     * Get metadata for a resource.
     *
     * @param uri resource identifier
     * @return A Map with resource metadata
     */
    Map<String, Object> getMetadata(URI uri);

    /**
     * Get length in bytes of resource.
     *
     * @param uri resource identifier
     * @return length in bytes
     */
    long getLength(URI uri);

    /**
     * Returns the time when the resource was last modified.
     *
     * @param uri resource identifier
     * @return milliseconds since 1 January 1970
     */
    long getLastModified(URI uri);
}

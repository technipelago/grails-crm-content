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

package grails.plugins.crm.content;

import java.net.URI;
import java.util.Collection;

/**
 * Factory interface for access to content providers.
 */
public interface CrmContentProviderFactory {
    /**
     * Get content provider for a specific kind of content.
     *
     * @param filename  file name
     * @param length    size of content in bytes
     * @param reference domain instance that the content is attached to
     * @param username  user that wants to store the content
     * @return content provider that accepted to store the content
     */
    CrmContentProvider getProvider(String filename, long length, Object reference, String username);

    /**
     * Get provider for existing content.
     *
     * @param resourceURI a resource identifier
     * @return provide instance that can retrieve content from the specified URI
     */
    CrmContentProvider getProvider(URI resourceURI);

    Collection<CrmContentProvider> getProviders();
}

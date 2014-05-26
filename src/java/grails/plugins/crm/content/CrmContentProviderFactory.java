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

import java.net.URI;

/**
 * Factory interface for access to content providers.
 */
public interface CrmContentProviderFactory {
    /**
     * Get content provider.
     *
     * @param filename file name
     * @param length size of content in bytes
     * @param reference
     * @return content provider instance
     */
    CrmContentProvider getProvider(String filename, long length, Object reference, String username);

    /**
     * Get provider for a specific resource identifier.
     *
     * @param resourceURI a resource identifier
     * @return provide instance that can retrieve content from the specified URI
     */
    CrmContentProvider getProvider(URI resourceURI);
}

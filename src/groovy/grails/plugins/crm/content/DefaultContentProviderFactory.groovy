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
import org.codehaus.groovy.grails.commons.GrailsApplication

/**
 * Default content provider factory.
 */
class DefaultContentProviderFactory implements CrmContentProviderFactory {

    GrailsApplication grailsApplication
    CrmContentRouter crmContentRouter

    /**
     * Get content provider for a specific kind of content.
     *
     * @param filename file name
     * @param length size of content in bytes
     * @param reference domain instance that the content is attached to
     * @param username user that wants to store the content
     * @return content provider that is available to store the content
     */
    @Override
    @CompileStatic
    CrmContentProvider getProvider(String filename, long length, Object reference, String username) {
        def provider = crmContentRouter.getProvider(filename, length, reference, username)
        if (provider == null) {
            throw new RuntimeException("No content provider found for $filename")
        }
        provider
    }

    @Override
    @CompileStatic
    CrmContentProvider getProvider(URI resourceURI) {
        getProviders().find { it.handles(resourceURI) }
    }

    @Override
    @CompileStatic
    Collection<CrmContentProvider> getProviders() {
        grailsApplication.mainContext.getBeansOfType(CrmContentProvider.class).values()
    }
}

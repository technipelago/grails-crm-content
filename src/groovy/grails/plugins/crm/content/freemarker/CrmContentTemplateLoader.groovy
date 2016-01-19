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


package grails.plugins.crm.content.freemarker

import freemarker.cache.TemplateLoader
import grails.plugins.crm.content.CrmResourceRef
import grails.plugins.crm.core.TenantUtils

/**
 * FreeMarker Template Loader that load content from GR8 CRM database.
 */
class CrmContentTemplateLoader implements TemplateLoader {

    private final long tenant

    def crmContentService

    CrmContentTemplateLoader(long tenant) {
        this.tenant = tenant
    }

    Object findTemplateSource(String name) throws IOException {
        TenantUtils.withTenant(tenant) {
            def ref = crmContentService.getContentByPath(name)
            if (ref instanceof CrmResourceRef) {
                return ref.getResource()
            }
            return null
        }
    }

    long getLastModified(Object uri) {
        TenantUtils.withTenant(tenant) {
            crmContentService.getLastModified(uri)
        }
    }

    Reader getReader(Object uri, String charset) throws IOException {
        TenantUtils.withTenant(tenant) {
            crmContentService.getReader(uri, charset)
        }
    }

    void closeTemplateSource(Object uri) throws IOException {
        // When the reader is closed, temporary files are removed.
    }

    String toString() {
        this.class.name + ' for tenant ' + tenant
    }
}

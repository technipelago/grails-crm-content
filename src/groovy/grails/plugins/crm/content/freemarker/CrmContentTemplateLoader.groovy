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

/**
 * FreeMarker Template Loader that load content from Grails CRM database.
 */
class CrmContentTemplateLoader implements TemplateLoader {

    def crmContentService

    Object findTemplateSource(String name) throws IOException {
        def ref = crmContentService.getContentByPath(name)
        if (ref instanceof CrmResourceRef) {
            return ref.getResource()
        }
        return null
    }

    long getLastModified(Object uri) {
        crmContentService.getLastModified(uri)
    }

    Reader getReader(Object uri, String charset) throws IOException {
        crmContentService.getReader(uri, charset)
    }

    void closeTemplateSource(Object uri) throws IOException{
        // When the reader is closed, temporary files are removed.
    }
}

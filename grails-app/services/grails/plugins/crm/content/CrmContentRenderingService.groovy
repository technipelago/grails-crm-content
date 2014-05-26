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

import org.codehaus.groovy.grails.web.pages.GroovyPagesTemplateEngine
import org.springframework.core.io.DescriptiveResource

/**
 * Services for rendering content in different formats.
 */
class CrmContentRenderingService {

    def grailsApplication
    def crmContentService
    def crmFreeMarkerService

    /**
     * Parse the template with GroovyPagesTemplateEngine.
     * @param template
     * @param out
     * @param model
     */
    private void renderGsp(CrmResourceRef template, Writer out, Map model) {
        def res = new DescriptiveResource("crmResourceRef${template.hashCode()}")
        def classLoader = new GroovyClassLoader(grailsApplication.classLoader)
        def engine
        try {
            def applicationContext = grailsApplication.mainContext
            engine = new GroovyPagesTemplateEngine(applicationContext.servletContext)
            engine.setApplicationContext(applicationContext)
            crmContentService.withInputStream(template.getResource()) { is ->
                engine.setClassLoader(classLoader)
                engine.createTemplate(is, res, res.description).make(model).writeTo(out)
            }
        } finally {
            engine?.clearPageCache()
            classLoader.clearCache()
        }
    }

    /**
     * Parse the template with FreeMarker.
     * @param template
     * @param out
     * @param model
     */
    private void renderFreemarker(CrmResourceRef template, Writer out, Map model) {
        crmFreeMarkerService.process(template, model, out)
    }

    /**
     * Output the template in raw format, without any parsing.
     * @param template
     * @param out
     */
    private void renderRaw(CrmResourceRef template, Writer out) {
        crmContentService.withInputStream(template.getResource()) { is ->
            out << is
        }
    }

    def render(CrmResourceRef resourceInstance, Map model, String parser, Writer out) {

        switch (parser) {
            case 'gsp':
                model.tenant = resourceInstance.tenantId
                renderGsp(resourceInstance, out, model)
                break
            case 'freemarker':
            case 'fm':
                model.tenant = resourceInstance.tenantId
                renderFreemarker(resourceInstance, out, model)
                break
            default:
                renderRaw(resourceInstance, out)
                break
        }
    }
}

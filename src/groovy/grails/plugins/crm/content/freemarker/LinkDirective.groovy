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

import freemarker.core.Environment
import freemarker.template.SimpleScalar
import freemarker.template.TemplateDirectiveBody
import freemarker.template.TemplateDirectiveModel
import freemarker.template.TemplateException
import freemarker.template.TemplateModel
import org.codehaus.groovy.grails.web.mapping.LinkGenerator

/**
 * Equivalent of <g:link .../>...</g:link>
 */
class LinkDirective implements TemplateDirectiveModel {

    private LinkGenerator grailsLinkGenerator

    LinkDirective(LinkGenerator grailsLinkGenerator) {
        this.grailsLinkGenerator = grailsLinkGenerator
    }

    void execute(Environment env, Map params, TemplateModel[] models, TemplateDirectiveBody body) throws TemplateException, IOException {

        def linkParams = params.inject([:]) {map, key, value->
            if(value instanceof SimpleScalar) {
                map[key] = value.toString()
            } else {
                map[key] = value
            }
            map
        }

        Writer out = env.getOut()

        out.write('<a href="')
        out.write(grailsLinkGenerator.link(linkParams))

        // Render query string.
        if (linkParams.containsKey('query')) {
            out.write('?')
            out.write(linkParams.remove('query'))
        } else if (linkParams.containsKey('params')) {
            // WARNING Use of params creates a script and can lead to PermGen hell!!!
            // TODO: Implement a String to Map conversion that do not create Groovy scripts!
            def classLoader = new GroovyClassLoader()
            def script
            try {
                script = classLoader.parseClass('params=' + linkParams.remove('params')).newInstance()
                def map = new ConfigSlurper().parse(script)
                def i = 0
                for (entry in map.params) {
                    if (i++) {
                        out.write('&')
                    } else {
                        out.write('?')
                    }
                    out.write(entry.key.encodeAsURL())
                    out.write('=')
                    out.write(entry.value.encodeAsURL())
                }
            } finally {
                if (script != null) {
                    GroovySystem.getMetaClassRegistry().removeMetaClass(script.getClass())
                }
                classLoader.clearCache()
            }
        }
        out.write('"')

        def exclude = ['controller', 'action', 'id', 'mapping', 'absolute']
        // Rendering remaining link attributes.
        for (entry in linkParams) {
            if (!exclude.contains(entry.getKey())) {
                out.write(' ' + entry.getKey() + '="' + entry.getValue() + '"')
            }
        }

        out.write('>')
        body.render(out)
        out.write('</a>')
    }
}

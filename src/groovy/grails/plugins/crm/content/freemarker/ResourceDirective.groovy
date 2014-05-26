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

package grails.plugins.crm.content.freemarker

import freemarker.core.Environment
import freemarker.template.TemplateDirectiveBody
import freemarker.template.TemplateDirectiveModel
import freemarker.template.TemplateException
import freemarker.template.TemplateModel
import freemarker.template.TemplateModelException

/**
 * A Freemarker method that is the equivalent of <g:resource .../>
 *
 * [@resource dir="css" file="mobile.css"]
 */
class ResourceDirective implements TemplateDirectiveModel {

    private String serverURL

    ResourceDirective(String prefix) {
        this.serverURL = prefix
        if (this.serverURL[-1] != '/') {
            this.serverURL += '/'
        }
    }

    @Override
    void execute(Environment env, Map params, TemplateModel[] model, TemplateDirectiveBody body) throws TemplateException, IOException {
        if (params.isEmpty()) {
            throw new TemplateModelException("Parameter [file] is missing.");
        }

        def s = new StringBuilder()
        s.append(serverURL)
        s.append('static/')

        def dir = params.dir
        if (dir) {
            s.append(dir.toString().encodeAsHTML())
            s.append('/')
        }

        if (params.file) {
            s.append(params.file.toString().encodeAsHTML())
        }

        env.getOut().write(s.toString())
    }
}

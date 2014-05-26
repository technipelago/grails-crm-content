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
import freemarker.template.TemplateDirectiveBody
import freemarker.template.TemplateDirectiveModel
import freemarker.template.TemplateException
import freemarker.template.TemplateModel

/**
 * Freemarker method that outputs an &lt;img&gt; tag.
 *
 * [@img dir="images/album" file="flower.jpg" alt="Flower"/]
 */
class ImageDirective implements TemplateDirectiveModel {

    private String serverURL

    ImageDirective(String prefix) {
        this.serverURL = prefix
        if (this.serverURL[-1] != '/') {
            this.serverURL += '/'
        }
    }

    void execute(Environment env, Map params, TemplateModel[] models, TemplateDirectiveBody body) throws TemplateException, IOException {

        StringBuilder s = new StringBuilder()

        s.append('<img src="')
        s.append(serverURL)
        s.append('static/')

        // Render controller, action and optional id.
        if (params.containsKey('dir')) {
            s.append(params.remove('dir').toString().encodeAsHTML())
            s.append('/')
        }
        if (params.containsKey('file')) {
            s.append(params.remove('file').toString().encodeAsHTML())
        }

        s.append('"')

        // Rendering remaining link attributes.
        for (entry in params) {
            s.append(' ' + entry.getKey().encodeAsURL() + '="' + entry.getValue().encodeAsHTML() + '"')
        }

        s.append('/>')

        env.getOut().write(s.toString())
    }
}

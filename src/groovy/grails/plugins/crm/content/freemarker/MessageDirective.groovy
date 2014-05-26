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
import org.springframework.context.MessageSource

/**
 * Created by goran on 2013-12-06.
 */
class MessageDirective implements TemplateDirectiveModel {

    private MessageSource messageSource

    MessageDirective(MessageSource messageSource) {
        this.messageSource = messageSource
    }

    @Override
    void execute(Environment env, Map params, TemplateModel[] model, TemplateDirectiveBody body) throws TemplateException, IOException {
        if (params.isEmpty()) {
            throw new TemplateModelException("Parameter [code] is missing.");
        }
        def locale = env.getLocale() ?: Locale.getDefault()
        def args = params.args ? params.args.toString().split(',').collect { it.trim() } : []
        env.getOut().write(messageSource.getMessage(params.code.toString(), args.toArray(), params.default ? params.default.toString() : params.code.toString(), locale))
    }
}
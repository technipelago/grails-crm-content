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
import freemarker.template.TemplateException
import freemarker.template.TemplateExceptionHandler

/**
 * A FreeMarker exception handler that doe not print the stack trace when running in production mode.
 * It still prints the FreeMarker error message to the client and it could contain template source.
 */
class CrmFreeMarkerExceptionHandler implements TemplateExceptionHandler {
    @Override
    void handleTemplateException(TemplateException te, Environment env, Writer out) throws TemplateException {
        try {
            if (grails.util.Environment.developmentMode) {
                out.write("[ERROR: " + te.getMessage() + "]")
            } else {
                out.write("[ERROR in template: " + env.getTemplate().getName() + "]")
            }
        } catch (IOException e) {
            throw new TemplateException("Failed to print error message. Cause: " + e, env)
        }
    }
}

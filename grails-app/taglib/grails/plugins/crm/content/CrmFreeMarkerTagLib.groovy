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

package grails.plugins.crm.content

import grails.plugins.crm.core.TenantUtils
import grails.util.Environment
import org.springframework.web.servlet.support.RequestContextUtils

import javax.servlet.http.HttpServletRequest

/**
 * This tag library provides direct access to FreeMarker template rendering.
 */
class CrmFreeMarkerTagLib {

    static namespace = "crm"

    def crmFreeMarkerService
    def crmThemeService

    private Long getTemplateTenant(HttpServletRequest request) {
        def theme = request.getAttribute('crmTheme')
        if (theme) {
            if (log.isDebugEnabled()) {
                log.debug("Found CRM theme $theme")
            }
            return theme.tenant
        }
        def tenant = crmThemeService.getThemeTenant()
        if (tenant != null) {
            return tenant
        }
        return grailsApplication.config.crm.content.include.tenant ?: 0L
    }

    def freemarker = { attrs, body ->
        if (!attrs.template) {
            throwTagError("Tag [freemarker] is missing required attribute [template]")
        }
        def forcedTenant = attrs.tenant ? Long.valueOf(attrs.tenant) : 0L
        def includeTenant = getTemplateTenant(request)
        def tenant = forcedTenant ?: includeTenant
        def binding = [:]

        binding.putAll(pageScope.getVariables())
        if (attrs.model) {
            binding.putAll(attrs.model)
        }

        if (attrs.locale) {
            binding.locale = attrs.locale
        } else if (!binding.locale) {
            binding.locale = (RequestContextUtils.getLocale(request) ?: Locale.default)
        }

        def template = attrs.template.toString()
        if (template[0] != '/') {
            def defaultPath = grailsApplication.config.crm.content.include.path
            if (defaultPath) {
                if (defaultPath[-1] == '/') {
                    template = defaultPath + template
                } else {
                    template = defaultPath + '/' + template
                }
            }
        }

        if (crmFreeMarkerService.templateExist(tenant, template)) {
            try {
                crmFreeMarkerService.process(tenant, template, binding, out)
            } catch (Exception e) {
                log.error("Error while rendering template ${template}", e)
                if (Environment.developmentMode) {
                    out << e.message
                } else {
                    out << message(code: 'crm.content.render.template.error', default: 'ERROR in template: {0}',
                            args: [template, e.message])
                }
            }
        } else {
            out << body()
        }
    }
}

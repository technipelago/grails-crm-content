/*
 * Copyright (c) 2015 Goran Ehrsson.
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

import grails.plugins.crm.content.freemarker.CrmContentTemplateLoader
import org.codehaus.groovy.grails.commons.GrailsClassUtils

import org.springframework.web.multipart.commons.CommonsMultipartFile

class CrmContentGrailsPlugin {
    def groupId = ""
    def version = "2.5.0"
    def grailsVersion = "2.4 > *"
    def dependsOn = [:]
    def observe = ['controllers']
    def loadAfter = ['crmSecurity', 'crmTags']
    def pluginExcludes = [
            "grails-app/domain/grails/plugins/crm/content/TestContentEntity.groovy",
            "src/groovy/grails/plugins/crm/content/TestSecurityDelegate.groovy",
            "src/templates/text/**",
            "src/templates/freemarker/*.*",
            "grails-app/views/error.gsp"
    ]
    def title = "Content Management Services for GR8 CRM"
    def author = "Goran Ehrsson"
    def authorEmail = "goran@technipelago.se"
    def description = '''\
This plugin provide storage and services for managing content in GR8 CRM applications.
Content can be any type of media like plain text, Microsoft Word, PDF, and images.
Content can be stored in folders or attached to domain instances.
Content can be shared with users of the application or shared publicly to the world.
'''
    def documentation = "http://gr8crm.github.io/plugins/crm-content/"
    def license = "APACHE"
    def organization = [name: "Technipelago AB", url: "http://www.technipelago.se/"]
    def issueManagement = [system: "github", url: "https://github.com/technipelago/grails-crm-content/issues"]
    def scm = [url: "https://github.com/technipelago/grails-crm-content"]

    def doWithSpring = {
        crmContentRouter(grails.plugins.crm.content.DefaultContentRouter) { bean ->
            bean.autowire = 'byName'
        }
        crmFileContentProvider(grails.plugins.crm.content.CrmFileContentProvider) { bean ->
            bean.autowire = 'byName'
        }
        crmContentProviderFactory(grails.plugins.crm.content.DefaultContentProviderFactory) { bean ->
            bean.autowire = 'byName'
        }

        // One template loader is (lazy) created for each tenant, therefore it must be prototype scoped.
        freeMarkerTemplateLoader(CrmContentTemplateLoader) { bean ->
            bean.scope = 'prototype'
            bean.autowire = 'byName'
        }
    }

    def doWithDynamicMethods = { ctx ->
        def service = ctx.getBean('crmContentService')
        def config = application.config

        // enhance domain classes
        application.domainClasses?.each { d ->
            if (getAttachmentableProperty(d)) {
                addDomainMethods config, d.clazz.metaClass, service
            }
        }
    }

    def doWithApplicationContext = { applicationContext ->
    }

    def onChange = { event ->
        def ctx = event.ctx

        if (event.source && ctx && event.application) {
            def service = ctx.getBean('crmContentService')
            def config = application.config

            // enhance domain class
            if (application.isDomainClass(event.source)) {
                def c = application.getDomainClass(event.source.name)
                if (getAttachmentableProperty(c)) {
                    addDomainMethods config, c.metaClass, service
                }
            }
            // enhance controller
            else if (application.isControllerClass(event.source)) {
                def c = application.getControllerClass(event.source.name)
                addControllerMethods config, c.metaClass, service
            }
        }
    }

    public static final String ATTACHMENTABLE_PROPERTY_NAME = "attachmentable"

    private getAttachmentableProperty(domainClass) {
        GrailsClassUtils.getStaticPropertyValue(domainClass.clazz, ATTACHMENTABLE_PROPERTY_NAME)
    }

    private void addDomainMethods(config, mc, service) {

        mc.addAttachment = { CommonsMultipartFile file, Map params = [:] ->
            service.createResource(file.inputStream, file.originalFilename, file.size, file.contentType, delegate, params)
        }

        mc.getAttachments = { params = [:] ->
            service.findResourcesByReference(delegate, params)
        }
        mc.deleteAttachments = {->
            service.deleteAllResources(delegate)
        }
    }

    private void addControllerMethods(config, mc, service) {
        // Nothing yet.
    }

}

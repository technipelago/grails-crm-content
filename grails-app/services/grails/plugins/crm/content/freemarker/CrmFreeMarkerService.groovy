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
import freemarker.template.Configuration
import freemarker.template.SimpleNumber
import freemarker.template.Template
import freemarker.template.Version
import grails.events.Listener
import grails.plugins.crm.content.CrmResourceRef
import grails.plugins.crm.core.TenantUtils

/**
 * FreeMarker Service.
 */
class CrmFreeMarkerService {

    public static final Version FREEMARKER_FEATURE_LEVEL = new Version(2, 3, 21)

    private static final List<String> EXTENSIONS = ['', '.html', '.ftl']

    def grailsApplication
    def crmCoreService
    def crmContentService

    private Map<Long, Configuration> configurations = [:].withDefault { tenant ->
        def grailsConfig = grailsApplication.config.crm.content.freemarker.template
        def cfg = new Configuration(FREEMARKER_FEATURE_LEVEL)

        // Set to 0-60 for debugging and higher value in production environment.
        int seconds = grailsConfig?.containsKey('updateDelay') ? grailsConfig.updateDelay : 60
        cfg.setTemplateUpdateDelayMilliseconds(seconds * 1000)

        log.debug "FreeMarker template update delay set to $seconds seconds for tenant $tenant"

        // Use beans wrapper (recommended for most applications)
        //cfg.setObjectWrapper(ObjectWrapper.DEFAULT_WRAPPER)

        // Set the default charset of the template files
        cfg.setDefaultEncoding(grailsConfig.defaultEncoding ?: "UTF-8")

        // Set the charset of the output. This is actually just a hint, that
        // templates may require for URL encoding and for generating META
        // element that uses http-equiv="Content-type".
        cfg.setOutputEncoding(grailsConfig.outputEncoding ?: "UTF-8")

        //cfg.setLocalizedLookup(false)

        def templateLoader = grailsApplication.mainContext.getBean('freeMarkerTemplateLoader', tenant)
        cfg.setTemplateLoader(templateLoader)

        // Set an exception handler that does not print the stack trace in production.
        cfg.setTemplateExceptionHandler(new CrmFreeMarkerExceptionHandler())

        // We change the default tag syntax from angle brackets to square brackets to avoid CKEditor HTML encoding of <>.
        cfg.setTagSyntax(Configuration.SQUARE_BRACKET_TAG_SYNTAX)

        cfg.setSharedVariable('tenant', new SimpleNumber(tenant))

        def grailsLinkGenerator = grailsApplication.mainContext.getBean('grailsLinkGenerator')

        String serverURL = grailsLinkGenerator.serverBaseURL

        cfg.setSharedVariable('serverURL', serverURL)

        cfg.setSharedVariable('resource', new ResourceDirective(serverURL))
        cfg.setSharedVariable('img', new ImageDirective(serverURL))
        cfg.setSharedVariable('link', new LinkDirective(grailsLinkGenerator))
        cfg.setSharedVariable('message', new MessageDirective(grailsApplication.mainContext.getBean('messageSource')))

        return cfg
    }

    /**
     * Set a shared variable for a tenant. This variable can then be used in FreeMarker templates.
     *
     * @param name name of variable
     * @param value the variable's value. This can be things like a simple scalar, an object or a FreeMarker Directive instance.
     * @param tenant the tenant ID where the variable should be available, default is current tenant.
     */
    void setSharedVariable(String name, Object value, Long tenant = null) {
        configurations.get(tenant != null ? tenant : TenantUtils.tenant).setSharedVariable(name, value)
    }

    /**
     * Check if a freemarker template exists.
     * @param name template name
     * @return true if the template exists
     */
    boolean templateExist(String name) {
        templateExist(TenantUtils.tenant, name)
    }

    /**
     * Check if a freemarker template exists.
     * @param tenant the tenant to use for template lookup
     * @param name template name
     * @return true if the template exists
     */
    boolean templateExist(Long tenant, String name) {
        if (tenant == null) {
            return false
        }
        if (TenantUtils.withTenant(tenant) {
            def cfg = configurations.get(tenant)
            TemplateLoader loader = cfg.getTemplateLoader()
            Object templateSource = EXTENSIONS.find { ext ->
                if (ext && !name.endsWith(ext)) {
                    return loader.findTemplateSource(name + ext)
                }
                loader.findTemplateSource(name)
            }
            if (templateSource != null) {
                loader.closeTemplateSource(templateSource)
                return true
            }
            false
        }) {
            return true
        }

        def fallbackTenant = grailsApplication.config.crm.content.include.tenant ?: 1L
        if (tenant != fallbackTenant) {
            return templateExist(fallbackTenant, name)
        }
        return false
    }

    /**
     * Process FreeMarker template with binding.
     *
     * @param templatePath name of template
     * @param binding Map with values that can be used in template
     * @param out output writer
     */
    void process(String templatePath, Map binding, Writer out) {
        process(TenantUtils.tenant, templatePath, binding, out)
    }

    /**
     * Process FreeMarker template with binding.
     *
     * @param tenant the tenant to use for template lookup
     * @param templatePath name of template
     * @param binding Map with values that can be used in template
     * @param out output writer
     */
    void process(Long tenant, String templatePath, Map binding, Writer out) {
        Template template = findTemplate(tenant, templatePath, binding.locale)
        if (template == null) {
            def fallbackTenant = grailsApplication.config.crm.content.include.tenant ?: 1L
            if (tenant != fallbackTenant) {
                template = findTemplate(fallbackTenant, templatePath, binding.locale)
                if (template) {
                    log.debug "Found FreeMarker template [$templatePath] in fallback tenant [$fallbackTenant]"
                }
            }
        } else {
            log.debug "Found FreeMarker template [$templatePath] in requested tenant [$tenant]"
        }
        if (template) {
            if (binding.locale) {
                def env = template.createProcessingEnvironment(binding, out)
                env.setLocale(binding.locale)
            }
            template.process(binding, out)
        } else {
            throw new FileNotFoundException("FreeMarker template [$templatePath] not found in tenant [$tenant]")
        }
    }

    private Template findTemplate(Long tenant, String path, Locale locale) {
        Template template
        if (tenant != null) {
            try {
                def cfg = configurations.get(tenant)
                def name = EXTENSIONS.find { ext ->
                    if (ext && !path.endsWith(ext)) {
                        return templateExist(tenant, path + ext) ? path + ext : null
                    }
                    templateExist(tenant, path) ? path : null
                }
                template = cfg.getTemplate(name ?: path, (Locale)locale)
            } catch (FileNotFoundException fnfe) {
                log.debug "Freemarker template [$path] not found in tenant [$tenant]"
            }
        }
        template
    }

    /**
     * Process FreeMarker template with binding.
     *
     * @param ref resource instance that points to the template
     * @param binding Map with values that can be used in template
     * @param out output writer
     */
    void process(CrmResourceRef ref, Map binding, Writer out) {
        def tenant = ref.tenantId
        TenantUtils.withTenant(tenant) {
            def path = crmContentService.getAbsolutePath(ref)
            def template = findTemplate(tenant, path, binding.locale)
            if (template && binding.locale) {
                def env = template.createProcessingEnvironment(binding, out)
                env.setLocale(binding.locale)
            }
            template
        }?.process(binding, out)
    }

    /**
     * Remove template from cache, this will force reload from DB on next access.
     * @param templateName template name/path
     */
    void removeFromCache(String templateName) {
        def tenant = TenantUtils.tenant
        Configuration configuration = configurations.get(tenant)
        if (configuration) {
            configuration.removeTemplateFromCache(templateName)
            log.debug "FreeMarker template [$templateName] removed from cache in tenant [$tenant]"
        }
    }

    /**
     * Removes all templates from the cache, this will force reload from DB on next template access.
     */
    void clearCache() {
        def tenant = TenantUtils.tenant
        Configuration configuration = configurations.get(tenant)
        if (configuration) {
            configuration.clearTemplateCache()
            log.debug "FreeMarker cache cleared for tenant [$tenant]"
        }
    }

    /**
     * Parse FreeMarker template located at event.template.
     * The event payload is used as binding for the template.
     *
     * @param config event payload, a Map with configuration parameters.
     * @return Result after parsing the template with FreeMarker.
     */
    @Listener(namespace = 'crm', topic = 'parseTemplate')
    def parseFreemarkerTemplate(Map config) {
        def binding = [:]
        binding.putAll(config)
        if (config.reference) {
            binding.bean = crmCoreService.getReference(config.reference)
        }
        def out = new StringWriter()
        process(config.template, binding, out)
        return out.toString()
    }
}

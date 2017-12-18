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

import grails.plugins.crm.core.TenantUtils
import grails.util.Environment
import org.springframework.web.servlet.support.RequestContextUtils

import javax.servlet.http.HttpServletRequest

/**
 * Tags for CRM Content.
 */
class CrmContentTagLib {

    private static final String INDEX_FILE = 'index.html'

    static namespace = "crm"

    def grailsApplication
    def crmContentService
    def crmContentRenderingService
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

    /**
     * Render a template stored as a CrmResourceRef instance, optional parsed with GSP or FreeMarker.
     * @attr template REQUIRED template path or CrmResourceRef instance or CrmResourceRef.id
     * @attr parser one of 'gsp', 'freemarker' or 'raw' (default).
     * @attr tenant the template tenant
     * @attr required true if an exception should be thrown if template cannot be found
     * @attr model the model to use when parsing template
     */
    def render = { attrs, body ->
        def template = attrs.template
        if (!template) {
            throwTagError("Tag [render] is missing required attribute [template]")
        }
        if (template instanceof Closure) {
            template = template.call()
        }
        def forcedTenant = attrs.tenant ? Long.valueOf(attrs.tenant) : 0L
        def includeTenant = forcedTenant ?: getTemplateTenant(request)
        def resourceList = []
        if (template instanceof CrmResourceRef) {
            resourceList << template
        } else if (template instanceof Long) {
            def tmp = CrmResourceRef.get(template)
            if (tmp) {
                resourceList << tmp
            } else {
                throwTagError("Tag [render] cannot find resource with id [${template}]")
            }
        } else {
            template = template.toString()
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
            // Find out what tenant to use for template lookup.
            def tenant = includeTenant
            def extensions = attrs.extensions
            def lang = (attrs.locale ?: (RequestContextUtils.getLocale(request) ?: Locale.default)).language
            def result
            if (extensions) {
                for (String ext in extensions) {
                    result = crmContentService.getContentByPath("${template}_$lang$ext", tenant)
                    if (!result) {
                        result = crmContentService.getContentByPath(template + ext, tenant)
                    }
                    if (result) {
                        break
                    }
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Looking for template $template in tenant $tenant")
                }
                result = crmContentService.getContentByPath("${template}_$lang", tenant)
                if (!result) {
                    result = crmContentService.getContentByPath(template, tenant)
                }
                if (log.isDebugEnabled() && result) {
                    log.debug("Found template $result")
                }
            }
            if (result instanceof Collection) {
                resourceList.addAll(result)
            } else if (result) {
                resourceList.add(result)
            }
        }

        // Remove resources that are not shared (public or by logged in users).
        // This can be disabled with the 'authorized' attribute.
        if (!attrs.authorized) {
            resourceList = resourceList.findAll {
                ((it.restricted || it.published) && (it.tenantId == includeTenant)) || crmContentService.hasViewPermission(it, true)
            }
        }

        if (resourceList) {
            // Find out what template parser to use.
            def parser = attrs.parser
            if (parser) {
                parser = parser.toLowerCase()
            } else if (!attrs.raw) {
                // For backward compatibility use 'GSP' parser as default.
                parser = grailsApplication.config.crm.content.include.parser ?: 'gsp'
            }
            // Lazy initialize the template model.
            def createModel = { r ->
                def m = [:]
                m.putAll(pageScope.getVariables())
                if (attrs.model) {
                    m.putAll(attrs.model)
                }
                return m
            }
            def model
            def max = attrs.max ? Integer.valueOf(attrs.max.toString()) : 0
            if (max && (resourceList.size() > max)) {
                resourceList = reduceSize(resourceList, max, Boolean.valueOf(attrs.random.toString()))
            }
            for (resourceInstance in resourceList) {
                try {
                    if (!model) {
                        model = createModel(resourceInstance)
                    }
                    crmContentRenderingService.render(resourceInstance, model, parser, out)
                } catch (Exception e) {
                    log.error("Error while rendering template $resourceInstance", e)
                    if (Environment.developmentMode) {
                        out << e.message
                    } else {
                        out << message(code: 'crm.content.render.template.error', default: 'ERROR in template: {0}',
                                args: [resourceInstance.toString(), e.message])
                    }
                }
            }
        } else if (Boolean.valueOf(attrs.required)) {
            throwTagError("Tag [render] cannot find resource [$template]")
        } else {
            out << body()
        }
    }

    private List reduceSize(List list, int max, boolean random) {
        if (random) {
            Collections.shuffle(list)
        }
        return list[0..(max - 1)]
    }

    def image = { attrs ->
        def params = takeAttributes(attrs, ['resource', 'reference', 'statusFilter', 'titleFilter', 'nameFilter'])
        if (params.resource) {
            out << """<img src="${createResourceLink(resource: params.resource)}"${renderAttributes(attrs)}/>"""
        } else if (params.reference) {
            def r = crmContentService.findResourcesByReference(params.reference,
                    [status: params.statusFilter, title: params.titleFilter, name: params.nameFilter]).find {
                crmContentService.isImage(it)
            }
            if (r) {
                out << """<img src="${createResourceLink(resource: r)}"${renderAttributes(attrs)}/>"""
            }
        }
    }

    private Map takeAttributes(Map takeFrom, List attributeNames) {
        def result = [:]
        for (a in attributeNames) {
            def v = takeFrom.remove(a)
            if (v != null) {
                result[a] = v
            }
        }
        return result
    }

    private String renderAttributes(Map attrs) {
        def s = new StringBuilder()
        attrs.each { key, value ->
            if (value != null) {
                s << " ${key.encodeAsURL()}=\"${value}\""
            }
        }
        s.toString()
    }

    def resource = { attrs, body ->
        def ref = attrs.remove('resource')
        if (!ref) {
            throwTagError("Tag [resource] is missing required attribute [resource]")
        }

        def forcedTenant = attrs.tenant ? Long.valueOf(attrs.tenant) : 0L
        def includeTenant = forcedTenant ?: getTemplateTenant(request)

        if (ref instanceof Long) {
            def tmp = CrmResourceRef.findByIdAndTenantId(ref, includeTenant)
            if (!tmp) {
                throwTagError("Tag [resource] cannot find resource with id [$ref]")
            }
            ref = tmp
        } else if(ref instanceof String) {
            def tmp = crmContentService.getContentByPath(ref, includeTenant)
            if (!tmp && !attrs.quiet) {
                throwTagError("Tag [resource] cannot find resource with path [$ref]")
            }
            ref = tmp
        }

        if (ref instanceof CrmResourceRef) {
            if (!(ref.shared || ((ref.restricted || ref.published) && (ref.tenantId == TenantUtils.tenant)))) {
                throwTagError("Can't access non-shared resource [$ref]")
            }
        } else if (ref instanceof CrmResourceFolder) {
            def tmp = ref.files.find { it.name == INDEX_FILE }
            if (tmp) {
                ref = tmp
            } else {
                ref = ref.first()
            }
        }

        if (ref) {
            Map map = [(attrs.var ?: 'it'): ref]
            out << body(map)
        }
    }

    /**
     * Generates a hyperlink to a public resource.
     * @attr resource REQUIRED CrmResourceRef instance or CrmResourceRef.id (Long).
     * @attr absolute true if an absolute link should be generated
     * @attr base URL prefix to use instead of the configured serverURL for this application
     */
    def resourceLink = { attrs, body ->
        if (!attrs.resource) {
            throwTagError("Tag [resourceLink] is missing required attribute [resource]")
        }
        def url = createResourceLink(attrs)
        def s = new StringBuilder()
        attrs.each { key, value ->
            s << " $key=\"${value.encodeAsURL()}\""
        }

        out << "<a href=\"${url}\"$s>${body()}</a>"
    }

    def createResourceLink = { attrs ->
        def ref = attrs.remove('resource')
        if (!ref) {
            throwTagError("Tag [createResourceLink] is missing required attribute [resource]")
        }
        def ctrl
        if (ref instanceof Long) {
            def tmp = CrmResourceRef.get(ref)
            if (!tmp) {
                throwTagError("Tag [createResourceLink] cannot find resource with id [$ref]")
            }
            ref = tmp
        }
        if (ref instanceof CrmResourceRef) {
            def parent = ref.reference
            if (parent instanceof CrmResourceFolder) {
                if (!(parent.sharedPath || ref.shared || ((ref.restricted || ref.published) && (ref.tenantId == TenantUtils.tenant)))) {
                    throwTagError("Can't link to a non-shared resource [$ref]")
                }
                ctrl = 'r'
            } else {
                if (!(ref.shared || ((ref.restricted || ref.published) && (ref.tenantId == TenantUtils.tenant)))) {
                    throwTagError("Can't link to a non-shared resource [$ref]")
                }
                ctrl = 's'
            }
        } else if (ref instanceof CrmResourceFolder) {
            ctrl = 'f'
        }
        def url
        if (attrs.base) {
            url = attrs.base + '/' + ctrl
        } else {
            def webUrl = grailsApplication.config.crm.web.url
            if (webUrl) {
                url = webUrl + '/' + ctrl
            } else {
                def absolute = attrs.absolute == false ? false : true
                url = g.createLink(absolute: absolute, controller: ctrl).toString()
            }
        }
        def path = crmContentService.getAbsolutePath(ref, true)
        if (path) {
            out << "${url}/${ref.tenantId}/${path}"
        } else {
            throwTagError("Trying to use tag [createResourceLink] with a non-shared resource [$ref]")
        }
    }

    /**
     * Renders a famfamfam icon (http://www.famfamfam.com/) depending on MIME type.
     *
     * @attr contentType REQUIRED the MIME content type to select icon for.
     * @attr default the default icon if MIME type is not recognized. Default is 'page_white'.
     */
    def fileIcon = { attrs ->
        def contentType = attrs.contentType
        if (!contentType) {
            throwTagError("Tag [fileIcon] is missing required attribute [contentType]")
        }
        def icon = crmContentService.getContentIcon(contentType, attrs.default)
        out << fam.icon(name: icon)
    }

    /**
     * Iterate over files attached to a domain instance and render tag body for each instance.
     *
     * @attr bean REQUIRED domain instance
     * @attr type file extension filter, or "image" to filter on all image types.
     * @attr status filter attachments by status, see CrmResourceRef.STATUS_LIST for possible values.
     * @attr title filter attachments by title, wildcards can be used.
     * @attr name filter attachments by name, wildcards can be used.
     * @attr tags comma separated list of tags to filter files with (prefix with ! to negate the filter)
     * @attr sort CrmResourceRef property to sort by (default = "name").
     * @attr order sort order "asc" or "desc" (default = "asc")
     * @attr var name of iteration variable (default = "it")
     * @attr index name of iteration index variable
     */
    def attachments = { attrs, body ->
        def bean = attrs.bean
        if (!bean) {
            out << "Tag [attachments] missing required attribute [bean]"
            return
        }
        List result = crmContentService.findResourcesByReference(bean, attrs)
        def type = attrs.type
        if (type) {
            boolean rval = true
            if (type[0] == '!') {
                rval = false
                type = type.substring(1)
            }
            switch (type) {
                case 'image':
                    result = result.findAll { isImage(it) ? rval : !rval }
                    break
                default:
                    result = result.findAll { it.ext == type ? rval : !rval }
                    break
            }
        }
        def tags = attrs.tags?.toString()
        if (tags) {
            boolean rval = true
            if (tags[0] == '!') {
                rval = false
                tags = tags.substring(1)
            }
            tags = tags.split(',').toList()*.trim()
            Closure filter
            if (tags.size() == 1) {
                filter = { tag, obj ->
                    obj.isTagged(tag) ? rval : !rval
                }.curry(tags[0])
            } else if (tags.size() > 1) {
                filter = { list, obj ->
                    for (t in list) {
                        if (obj.isTagged(t)) {
                            return rval
                        }
                    }
                    !rval
                }.curry(tags)
            } else {
                filter = { obj ->
                    obj.getTagValue() ? rval : !rval
                }
            }
            result = result.findAll { filter(it) }
        }
        def variableName = attrs.var ?: 'it'
        int i = 0
        for (r in result) {
            Map map = [(variableName): r]
            if (attrs.index) {
                map[attrs.index] = i++
            }
            out << body(map)
        }
    }

}

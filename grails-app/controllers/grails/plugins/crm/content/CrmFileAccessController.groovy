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

import javax.servlet.http.HttpServletResponse

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR
import static javax.servlet.http.HttpServletResponse.SC_OK
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT
import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED

import grails.converters.JSON
import java.text.SimpleDateFormat
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import grails.converters.XML

import org.springframework.web.context.request.RequestContextHolder

/**
 * Render content made available to external visitors.
 */
class CrmFileAccessController {

    private static final int DEFAULT_CACHE_SECONDS = 60 * 60

    def grailsApplication
    def crmContentService
    def crmSecurityService

    LinkGenerator grailsLinkGenerator

    def show(Long t, String domain, Long id, String file, String uri) {
        if (log.isDebugEnabled()) {
            log.debug "show($t, $domain, $id, $file, $uri)"
        }
        def status = SC_NOT_FOUND
        try {
            def ref
            if (file) {
                ref = CrmResourceRef.findByTenantIdAndRefAndName(t, domain + '@' + id, file, [cache: true])
            } else if (uri) {
                ref = crmContentService.getContentByPath(uri, t)
                if (ref instanceof Collection) {
                    redirect action: "list", params: [t: t, uri: uri]
                    return
                }
            }

            if (ref) {
                if (!crmContentService.hasViewPermission(ref, crmSecurityService.isAuthenticated())) {
                    log.warn("User [${crmSecurityService.currentUser?.username}] is not permitted to view content [$ref] with status [${ref.statusText}]")
                    response.sendError(SC_FORBIDDEN)
                    return
                }
                def metadata = ref.metadata
                def etag = metadata.hash
                def modified = metadata.modified.time
                modified = modified - (modified % 1000) // Remove milliseconds.
                if (metadata.contentType) {
                    response.setContentType(metadata.contentType)
                } else {
                    if (log.isDebugEnabled()) {
                        log.error("Resource $ref has no content type")
                    }
                    response.setContentType('application/octet-stream')
                }
                response.setDateHeader("Last-Modified", modified)

                def requestETag = request.getHeader("If-None-Match")
                if (requestETag && (requestETag == etag)) {
                    if (log.isDebugEnabled()) {
                        log.debug "Not modified (ETag)"
                    }
                    response.setStatus(SC_NOT_MODIFIED)
                    response.outputStream.flush()
                    return null
                } else {
                    def ms = request.getDateHeader("If-Modified-Since")
                    if (modified <= ms) {
                        if (log.isDebugEnabled()) {
                            log.debug "Not modified (If-Modified-Since)"
                        }
                        response.setStatus(SC_NOT_MODIFIED)
                        response.outputStream.flush()
                        return null
                    }
                }

                def len = metadata.bytes
                response.setContentLength(len.intValue())
                response.setHeader("ETag", etag)

                if (request.method != "HEAD") {
                    def encoding = ref.encoding
                    if (encoding) {
                        response.setCharacterEncoding(encoding)
                    }
                    response.setHeader("Content-Disposition", "inline; filename=${ref.name}; size=$len")

                    def seconds = grailsApplication.config.crm.content.cache.expires ?: DEFAULT_CACHE_SECONDS
                    cacheThis(response, seconds, ref.shared)

                    if (log.isDebugEnabled()) {
                        log.debug "Rendering resource $ref (cache $seconds s)"
                    }

                    def out = response.outputStream
                    ref.writeTo(out)
                    out.flush()
                    out.close()
                }
                status = SC_OK
            } else if (log.isDebugEnabled()) {
                log.debug "Resource not found"
            }
        } catch (SocketException e) {
            log.error("Client aborted while rendering resource: ${t}/${uri ?: file}: ${e.message}")
            status = SC_NO_CONTENT
        } catch (IOException e) {
            log.error("IOException while rendering resource: ${t}/${uri ?: file}: ${e.message}")
            status = SC_NO_CONTENT
        } catch (Exception e) {
            log.error("Error while rendering resource: ${t}/${uri ?: file}", e)
            status = SC_INTERNAL_SERVER_ERROR
        }

        if (status >= 200 && status < 300) {
            response.setStatus(status)
            RequestContextHolder.currentRequestAttributes().setRenderView(false)
        } else {
            if (log.isDebugEnabled()) {
                log.error "Rendering of resource failed with status code $status"
            }
            response.sendError(status)
        }

        return null
    }

    private void cacheThis(HttpServletResponse response, int seconds, boolean shared) {
        response.setHeader("Pragma", "")
        response.setHeader("Cache-Control", "${shared ? 'public' : 'private,no-store'},max-age=$seconds")
        Calendar cal = Calendar.getInstance()
        cal.add(Calendar.SECOND, seconds)
        response.setDateHeader("Expires", cal.getTimeInMillis())
    }

    def list(Long t, String uri) {
        try {
            def dir = crmContentService.getFolder(uri, t)
            if (dir) {
                if (!dir.sharedPath) {
                    response.sendError(SC_FORBIDDEN)
                    return
                }
                if (dir.passwordHash && !crmSecurityService.authenticated) {
                    response.sendError(SC_UNAUTHORIZED)
                    return
                }
                def files = dir.files.findAll { it.shared }
                def folders = dir.folders.findAll { it.shared }
                withFormat {
                    html {
                        def dateFormat = new SimpleDateFormat('dd-MMM-yyyy HH:mm', Locale.ENGLISH)
                        render(contentType: "text/html") {
                            h1("Index of ${dir.name}")
                            table {
                                thead {
                                    tr {
                                        th("")
                                        th("Name")
                                        th("Last modified")
                                        th("Size")
                                        th("Description")
                                    }
                                    tr {
                                        th(colspan: 5) {
                                            hr()
                                        }
                                    }
                                }
                                tbody {
                                    if (dir.parent?.sharedPath) {
                                        def url = crmContentService.getAbsolutePath(dir.parent, false)
                                        tr {
                                            td {
                                                delegate.img(src: fam.icon(name: 'arrow_turn_left'))
                                            }
                                            td(colspan: 4) {
                                                a(href: grailsLinkGenerator.link(controller: 'crmFileAccess', action: 'list', params: [t: t, uri: url]), "Parent Directory")
                                            }
                                        }
                                    }
                                    for (folder in folders) {
                                        def url = crmContentService.getAbsolutePath(folder, false)
                                        tr {
                                            td {
                                                delegate.img(src: fam.icon(name: 'folder'))
                                            }
                                            td(colspan: 4) {
                                                a(href: grailsLinkGenerator.link(controller: 'crmFileAccess', action: 'list', params: [t: t, uri: url]), folder.name)
                                            }
                                        }
                                    }
                                    for (file in files) {
                                        def info = file.dao
                                        def metadata = file.metadata
                                        def url = crmContentService.getAbsolutePath(file, false)
                                        tr {
                                            td {
                                                delegate.img(src: fam.icon(name: metadata.icon))
                                            }
                                            td {
                                                a(href: grailsLinkGenerator.link(controller: 'crmFileAccess', action: 'show', params: [t: t, uri: url]), file.name)
                                            }
                                            td(dateFormat.format(metadata.modified))
                                            td(metadata.size)
                                            td(info.title)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    xml {
                        def result = files.collect {
                            def md = it.metadata + it.dao
                            // Remove internal stuff
                            md.remove('uri')
                            md.remove('resource')
                            return md
                        }
                        render result as XML
                    }
                    json {
                        def result = files.collect {
                            def md = it.metadata + it.dao
                            // Remove internal stuff
                            md.remove('uri')
                            md.remove('resource')
                            return md
                        }
                        render result as JSON
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error while listing files: ${t}/${uri}", e)
            response.sendError(SC_NOT_FOUND)
        }
    }

}

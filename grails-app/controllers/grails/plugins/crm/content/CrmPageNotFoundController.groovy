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

import org.springframework.web.context.request.RequestContextHolder

import javax.servlet.http.HttpServletResponse

import static javax.servlet.http.HttpServletResponse.*

/**
 * Custom 404 handler that looks for persisted resources with the given name.
 * If no resource is found it renders a page-not-found page using a GSP layout template.
 */
class CrmPageNotFoundController {

    private static final int DEFAULT_CACHE_SECONDS = 60 * 60

    def grailsApplication
    def crmContentService
    def crmSecurityService

    def index() {
        String wantedPage = request.forwardURI - request.contextPath
        if (wantedPage == '/') {
            wantedPage = 'index.html'
        }

        // Render HTML content using the crmPageNotFound/index view. It embeds the HTML in a GSP layout template.
        if (wantedPage.endsWith('.html') || wantedPage.endsWith('.htm')) {
            response.setStatus(200)
            return [uri: wantedPage]
        }

        def status = SC_NOT_FOUND

        try {
            def root = grailsApplication.config.crm.content.cms.path ?: '/wwwroot'
            def tenant = grailsApplication.config.crm.content.include.tenant ?: 1L
            def ref = crmContentService.getContentByPath("$root/$wantedPage", tenant)
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

                response.setStatus(SC_OK)

                def len = metadata.bytes
                response.setContentLength(len.intValue())
                response.setHeader("ETag", etag)

                if (request.method != "HEAD") {
                    def encoding = ref.encoding
                    if (encoding) {
                        response.setCharacterEncoding(encoding)
                    }
                    response.setHeader("Content-Disposition", "inline; filename=${CrmContentUtils.encodeFilename(ref.name)}; size=$len")

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
                log.debug "Resource not found: $wantedPage"
            }
        } catch (SocketException e) {
            log.error("Client aborted while rendering resource: $wantedPage")
            status = SC_NO_CONTENT
        } catch (IOException e) {
            log.error("IOException while rendering resource: $wantedPage")
            status = SC_NO_CONTENT
        } catch (Exception e) {
            log.error("Error while rendering resource: $wantedPage", e)
            status = SC_INTERNAL_SERVER_ERROR
        }

        if (status >= 200 && status < 300) {
            response.setStatus(status)
            RequestContextHolder.currentRequestAttributes().setRenderView(false)
        } else {
            if (log.isDebugEnabled()) {
                log.error "Rendering of resource [$wantedPage] failed with status code $status"
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
}

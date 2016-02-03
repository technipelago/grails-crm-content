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

/**
 * Custom 404 handler that looks for a text template (code/tag in the GSP) with the given name.
 * If no text template is found it renders a nice page-not-found page.
 */
class CrmPageNotFoundController {

    def crmContentService

    def index() {
        String wantedPage = request.forwardURI - request.contextPath
        if (wantedPage[0] == '/') {
            wantedPage = wantedPage.substring(1)
        }
        // Do not render a HTML page if it was an image or AJAX request.
        if (crmContentService.isImage(wantedPage) || request.xhr) {
            RequestContextHolder.currentRequestAttributes().setRenderView(false)
            response.setStatus(404)
        } else {
            response.setStatus(200)
            return [uri: wantedPage] // index.gsp will try to lookup the text template.
        }
    }
}

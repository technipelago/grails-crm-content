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

/**
 * FreeMarker Cache Controller.
 */
class CrmFreeMarkerController {

    static allowedMethods = [clear: 'POST']

    def crmFreeMarkerService

    def clear(Long t, String path) {
        log.debug "Clearing content cache in tenant $t (path=$path)"
        TenantUtils.withTenant(t) {
            if (path) {
                crmFreeMarkerService.removeFromCache(path)
            } else {
                crmFreeMarkerService.clearCache()
            }
        }
        render path ? path : 'success'
    }
}

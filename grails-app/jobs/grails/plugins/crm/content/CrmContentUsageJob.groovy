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

import grails.plugins.crm.core.WebUtils
import grails.plugins.crm.security.CrmAccount

class CrmContentUsageJob {

    static triggers = {
        //simple name: 'crmContentUsage', startDelay: 1000 * 60 * 3, repeatInterval: 1000 * 60 * 3 // every 3 minutes
        cron name: 'crmContentUsage', cronExpression: "0 45 3 * * ?" // every day at 03:45
    }

    def group = 'crmContent'

    def grailsApplication
    def crmContentService

    def execute() {
        if (grailsApplication.config.crm.content.job.usage.enabled) {
            log.info "Calculating resource usage..."
            def accounts = CrmAccount.createCriteria().list() {
                gt('status', CrmAccount.STATUS_CLOSED)
            }
            //def accounts = CrmAccount.activeAccounts.list()
            def total = 0L
            for (a in accounts) {
                CrmAccount.withTransaction { tx ->
                    def usage = calculateUsage(a.tenants*.ident())
                    if (usage) {
                        log.info "Usage for account [${a.id}] is ${WebUtils.bytesFormatted(usage)}"
                    } else {
                        log.debug "No resources used by account [${a.id}]"
                    }
                    a.setOption('resourceUsage', usage)
                    total += usage
                }
            }
            log.info "Total resource usage by ${accounts.size()} accounts: ${WebUtils.bytesFormatted(total)}"
        }
    }

    private long calculateUsage(List<Long> tenants) {
        def total = 0L
        for (t in tenants) {
            def size = crmContentService.getResourceUsage(t)
            total += size
            log.debug "Usage for tenant [$t] is ${WebUtils.bytesFormatted(size)}"
        }
        return total
    }
}

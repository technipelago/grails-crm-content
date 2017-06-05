/*
 * Copyright (c) 2017 Goran Ehrsson.
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

import grails.plugins.crm.core.CrmCoreService
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import java.util.regex.Pattern

/**
 * Route content based on metadata like filename, length and reference object.
 */
class PatternContentRouter implements CrmContentRouter {

    private static final Log log = LogFactory.getLog(PatternContentRouter.class)

    CrmContentProvider defaultProvider
    CrmContentProvider provider
    Pattern pattern
    Class referenceClass
    Integer minLength

    private final CrmCoreService crmCoreService

    PatternContentRouter(CrmCoreService crmCoreService) {
        this.crmCoreService = crmCoreService
    }

    void setPattern(Pattern pattern) {
        this.pattern = pattern
    }

    void setPattern(String pattern) {
        this.pattern = Pattern.compile(pattern)
    }

    CrmContentProvider getProvider(String filename, long length, Object ref, String username) {
        if (log.isDebugEnabled()) {
            log.debug "Get content provider for $filename"
        }
        if (minLength != null && length < minLength) {
            if (log.isDebugEnabled()) {
                log.debug "Length $length < $minLength"
            }
            return defaultProvider
        }
        if (pattern != null) {
            def matcher = pattern.matcher(filename.toLowerCase())
            if (!matcher.find()) {
                if (log.isDebugEnabled()) {
                    log.debug "Pattern $pattern did not match $filename"
                }
                return defaultProvider
            }
        }
        if (referenceClass != null) {
            def reference = (ref instanceof String) ? crmCoreService.getReference(ref) : ref
            if (!referenceClass.isAssignableFrom(reference.getClass())) {
                if (log.isDebugEnabled()) {
                    log.debug "${reference.getClass()} is not a $referenceClass"
                }
                return defaultProvider
            }
        }
        if (log.isDebugEnabled()) {
            log.debug "Using content provider $provider for $filename"
        }
        provider
    }
}

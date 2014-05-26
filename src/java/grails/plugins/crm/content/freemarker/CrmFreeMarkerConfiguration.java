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

package grails.plugins.crm.content.freemarker;

import freemarker.template.Configuration;
import freemarker.template.Template;
import grails.plugins.crm.core.TenantUtils;

import java.io.IOException;
import java.util.Locale;

/**
 * A Grails CRM tenant-aware FreeMarker Configuration implementation.
 */
public class CrmFreeMarkerConfiguration extends Configuration {

    private long tenant;

    public CrmFreeMarkerConfiguration(long tenant) {
        this.tenant = tenant;
    }

    @Override
    public Template getTemplate(String name, Locale locale, String encoding, boolean parse) throws IOException {
        Template template = null;
        final Long previousTenantId = TenantUtils.getTenant();
        if ((previousTenantId != null) && !previousTenantId.equals(tenant)) {
            try {
                TenantUtils.setTenant(tenant);
                template = super.getTemplate(name, locale, encoding, parse);
            } finally {
                TenantUtils.setTenant(previousTenantId);
            }
        } else {
            template = super.getTemplate(name, locale, encoding, parse);
        }
        return template;
    }
}

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

import grails.plugins.crm.core.CrmCoreService
import grails.plugins.crm.core.TenantEntity
import groovy.transform.CompileStatic
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang.StringUtils

import java.text.Normalizer
import java.text.Normalizer.Form

/**
 * This domain links a CrmFileResource (typically a document) to another domain class.
 *
 * @author Goran Ehrsson
 * @since 0.1
 */
@TenantEntity
class CrmResourceRef implements CrmContentNode {

    public static final Integer STATUS_ARCHIVED = -1
    public static final Integer STATUS_DRAFT = 0
    public static final Integer STATUS_PUBLISHED = 1
    public static final Integer STATUS_SHARED = 5
    public static final List<Integer> STATUS_LIST = [STATUS_ARCHIVED, STATUS_DRAFT, STATUS_PUBLISHED, STATUS_SHARED]
    public static final Map<String, Integer> STATUS_TEXTS = [archived: STATUS_ARCHIVED, draft: STATUS_DRAFT, published: STATUS_PUBLISHED, shared: STATUS_SHARED]

    // Lazy initialized (see getters below)
    private def _crmCoreService
    private def _crmContentService

    String title
    String description
    String name
    String ref // entityName@id
    String res
    int status

    static constraints = {
        title(maxSize: 255, blank: false)
        description(maxSize: 2000, nullable: true, widget: 'textarea')
        name(maxSize: 255, blank: false, unique: 'ref')
        res(maxSize: 80, blank: false, unique: 'ref')
        ref(maxSize: 80, blank: false)
        status(inList: STATUS_LIST)
    }

    static mapping = {
        columns {
            name index: 'crm_content_name'
            res index: 'crm_content_res'
            ref index: 'crm_content_ref'
        }
        cache usage: 'read-write'
    }

    static transients = ['icon', 'path', 'ext', 'reference', 'folder', 'resource', 'metadata', 'lastModified',
            'archived', 'draft', 'published', 'shared', 'statusText', 'dao', 'encoding', 'reader', 'text', 'bytes']

    static taggable = true

    @CompileStatic
    public static String removeAccents(String text) {
        return text != null ? Normalizer.normalize(text, Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "") : null
    }

    @CompileStatic
    public static String normalizeName(String name) {
        name != null ? StringUtils.replaceChars(FilenameUtils.normalize(removeAccents(name)), '\\/|"\':?*<>', '---__...()') : null
    }

    // Lazy injection of service.
    private CrmCoreService getCrmCoreService() {
        if (_crmCoreService == null) {
            synchronized(this) {
                if (_crmCoreService == null) {
                    _crmCoreService = this.getDomainClass().getGrailsApplication().getMainContext().getBean('crmCoreService')
                }
            }
        }
        return (CrmCoreService)_crmCoreService
    }

    // Lazy injection of service.
    private CrmContentService getCrmContentService() {
        if (_crmContentService == null) {
            synchronized(this) {
                if (_crmContentService == null) {
                    _crmContentService = this.getDomainClass().getGrailsApplication().getMainContext().getBean('crmContentService')
                }
            }
        }
        return (CrmContentService)_crmContentService
    }

    def beforeValidate() {
        if (!name) {
            name = title
        }
        if(name != null) {
            name = normalizeName(name)
        }
    }

    transient String getIcon() {
        getCrmContentService().getContentIcon(metadata?.contentType)
    }

    transient List getPath() {
        def r = getReference()
        if (r instanceof CrmResourceFolder) {
            return r.path + [this]
        }
        return null
    }

    @CompileStatic
    transient String getExt() {
        FilenameUtils.getExtension(name)
    }

    @CompileStatic
    transient void setReference(object) {
        ref = getCrmCoreService().getReferenceIdentifier(object)
    }

    @CompileStatic
    transient boolean isFolder() {
        return false
    }

    @CompileStatic
    transient Object getReference() {
        getCrmCoreService().getReference(ref)
    }

    @CompileStatic
    transient URI getResource() {
        new URI(res)
    }

    @CompileStatic
    transient Map getDao() {
        [name: name, ext: ext, title: title, description: description, status: status, resource: res]
    }

    @CompileStatic
    transient String getEncoding() {
        if (name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".txt")) {
            return "UTF-8"
        }
        return null
    }

    @CompileStatic
    transient boolean isArchived() {
        status == STATUS_ARCHIVED
    }

    @CompileStatic
    transient boolean isDraft() {
        status == STATUS_DRAFT
    }

    @CompileStatic
    transient boolean isPublished() {
        status == STATUS_PUBLISHED
    }

    @CompileStatic
    transient boolean isShared() {
        status == STATUS_SHARED
    }

    @CompileStatic
    transient String getStatusText() {
        STATUS_TEXTS.find { Map.Entry s -> s.value == status }?.key
    }

    @CompileStatic
    void setStatusText(String arg) {
        def s = STATUS_TEXTS[arg?.toLowerCase()]
        if (s == null) {
            throw new IllegalArgumentException("[$arg] is not a valid status")
        }
        status = s
    }

    @CompileStatic
    transient Map<String, Object> getMetadata() {
        getCrmContentService().getMetadata(resource)
    }

    @CompileStatic
    transient long getLastModified() {
        getCrmContentService().getLastModified(resource)
    }

    @CompileStatic
    def withInputStream(Closure work) {
        getCrmContentService().withInputStream(resource, work)
    }

    @CompileStatic
    long writeTo(OutputStream out) {
        getCrmContentService().writeTo(resource, out)
    }

    @CompileStatic
    Reader getReader(String charsetName) {
        getCrmContentService().getReader(resource, charsetName)
    }

    @CompileStatic
    String getText() {
        new String(getBytes())
    }

    @CompileStatic
    byte[] getBytes() {
        final Map md = getMetadata()
        final Long length = (Long)md.bytes
        final ByteArrayOutputStream out = new ByteArrayOutputStream(length.intValue())
        writeTo(out)
        out.toByteArray()
    }

    @CompileStatic
    @Override
    String toString() {
        name.toString()
    }

    boolean equals(o) {
        if (this.is(o)) return true;
        if (getClass() != o.class) return false

        CrmResourceRef that = (CrmResourceRef) o

        if (tenantId != that.tenantId) return false
        if (name != that.name) return false
        if (ref != that.ref) return false
        if (res != that.res) return false

        return true
    }

    int hashCode() {
        int result
        result = (tenantId != null ? tenantId.hashCode() : 0)
        result = 31 * result + (name != null ? name.hashCode() : 0)
        result = 31 * result + (ref != null ? ref.hashCode() : 0)
        result = 31 * result + (res != null ? res.hashCode() : 0)
        return result
    }
}


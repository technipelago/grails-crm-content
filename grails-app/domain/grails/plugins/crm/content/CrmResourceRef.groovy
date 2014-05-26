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

import grails.plugins.crm.core.TenantEntity
import org.apache.commons.io.FilenameUtils
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

    public static String removeAccents(String text) {
        return text == null ? null : Normalizer.normalize(text, Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
    }

    // Lazy injection of service.
    private def getCrmCoreService() {
        if (_crmCoreService == null) {
            synchronized(this) {
                if (_crmCoreService == null) {
                    _crmCoreService = this.getDomainClass().getGrailsApplication().getMainContext().getBean('crmCoreService')
                }
            }
        }
        _crmCoreService
    }

    // Lazy injection of service.
    private def getCrmContentService() {
        if (_crmContentService == null) {
            synchronized(this) {
                if (_crmContentService == null) {
                    _crmContentService = this.getDomainClass().getGrailsApplication().getMainContext().getBean('crmContentService')
                }
            }
        }
        _crmContentService
    }

    def beforeValidate() {
        if (!name) {
            name = title
        }
        name = FilenameUtils.normalize(removeAccents(name)).replaceAll('/', '-')
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

    transient String getExt() {
        FilenameUtils.getExtension(name)
    }

    transient void setReference(object) {
        ref = getCrmCoreService().getReferenceIdentifier(object)
    }

    transient boolean isFolder() {
        return false
    }

    transient Object getReference() {
        getCrmCoreService().getReference(ref)
    }

    transient URI getResource() {
        new URI(res)
    }

    transient Map getDao() {
        [name: name, ext: ext, title: title, description: description, status: status, resource: res]
    }

    transient String getEncoding() {
        if (name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".txt")) {
            return "UTF-8"
        }
        return null
    }

    transient boolean isArchived() {
        status == STATUS_ARCHIVED
    }

    transient boolean isDraft() {
        status == STATUS_DRAFT
    }

    transient boolean isPublished() {
        status == STATUS_PUBLISHED
    }

    transient boolean isShared() {
        status == STATUS_SHARED
    }

    transient String getStatusText() {
        STATUS_TEXTS.find { it.value == status }?.key
    }

    void setStatusText(String arg) {
        def s = STATUS_TEXTS[arg?.toLowerCase()]
        if (s == null) {
            throw new IllegalArgumentException("[$arg] is not a valid status")
        }
        status = s
    }

    transient Map<String, Object> getMetadata() {
        getCrmContentService().getMetadata(resource)
    }

    transient long getLastModified() {
        getCrmContentService().getLastModified(resource)
    }

    def withInputStream(Closure work) {
        getCrmContentService().withInputStream(resource, work)
    }

    long writeTo(OutputStream out) {
        getCrmContentService().writeTo(resource, out)
    }

    Reader getReader(String charsetName) {
        getCrmContentService().getReader(resource, charsetName)
    }

    String getText() {
        new String(getBytes())
    }

    byte[] getBytes() {
        final Map md = getMetadata()
        final ByteArrayOutputStream out = new ByteArrayOutputStream(md.bytes.intValue())
        writeTo(out)
        out.toByteArray()
    }

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


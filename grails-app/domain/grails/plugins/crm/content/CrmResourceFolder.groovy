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

import grails.plugins.crm.core.TenantEntity
import grails.plugins.crm.core.UuidEntity
import groovy.transform.CompileStatic
import org.apache.commons.lang.StringUtils

import java.text.Normalizer
import java.text.Normalizer.Form
import org.apache.commons.io.FilenameUtils
import grails.plugins.crm.core.AuditEntity
import java.security.MessageDigest

/**
 * Shared folder.
 */
@TenantEntity
@AuditEntity
class CrmResourceFolder implements CrmContentNode {

    def crmCoreService

    String name
    String title
    String description
    String username
    String passwordHash
    String passwordSalt
    CrmResourceFolder parent
    boolean shared // TODO remove/deprecate

    static hasMany = [folders: CrmResourceFolder]

    static constraints = {
        name(maxSize: 255, blank: false, unique: 'parent')
        title(maxSize: 255, blank: false)
        description(maxSize: 2000, nullable: true, widget: 'textarea')
        username(size: 3..80, maxSize: 80, nullable: true)
        passwordSalt(maxSize: 80, nullable: true)
        passwordHash(maxSize: 255, nullable: true)
        parent(nullable:true)
    }

    static mapping = {
        sort 'name'
        folders sort: 'name'
        columns {
            name index: 'crm_folder_idx'
        }
    }

    static transients = ['icon', 'files', 'sharedPath', 'path', 'subFolders', 'folder', 'lastModified']

    static taggable = true

    @CompileStatic
    public static String removeAccents(String text) {
        return text != null ? Normalizer.normalize(text, Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "") : null
    }

    @CompileStatic
    public static String normalizeName(String name) {
        name != null ? StringUtils.replaceChars(FilenameUtils.normalize(removeAccents(name)), '\\/|"\':?*<>', '---__...()') : null
    }

    def beforeValidate() {
        if (!title) {
            title = name
        }
        if (!name) {
            name = title
        }

        if(name != null) {
            name = normalizeName(name)
        }
    }

    def beforeInsert() {
        checkCircularReference()
    }

    def beforeUpdate() {
        checkCircularReference()
    }

    private void checkCircularReference() {
        def folder = this
        def visited = []
        while (folder) {
            if (visited.contains(folder)) {
                throw new RuntimeException("Circular reference found in folder ${folder.name}")
            }
            visited << folder
            folder = folder.parent
        }
    }

    transient Set<CrmResourceFolder> getSubFolders() {
        def tree = [this] as Set
        for(f in folders) {
            tree.addAll(f.getSubFolders())
        }
        tree
    }

    transient String getIcon() {
        return 'folder'
    }

    transient List<CrmResourceRef> getFiles(Map params = [:]) {
        if (!params.sort) params.sort = 'name'
        if (!params.order) params.order = 'asc'
        CrmResourceRef.findAllByRef(crmCoreService.getReferenceIdentifier(this), params)
    }

    transient boolean isSharedPath() {
        shared ?: parent?.isSharedPath()
    }

    transient List<CrmResourceFolder> getPath() {
        def list = []
        def folder = this
        while(folder) {
            list << folder
            folder = folder.parent
        }
        return list.reverse()
    }

    transient boolean isFolder() {
        return true
    }

    transient long getLastModified() {
        (lastUpdated ?: dateCreated).time
    }

    Map<String, Object> getMetadata() {
        def md = [:]
        md.uri = null
        md.contentType = null
        md.bytes = 0L
        md.size = ''
        md.icon = 'folder'
        md.created = dateCreated
        md.modified = lastUpdated ?: dateCreated
        md.hash = getMD5()
        md.encrypted = 0
        return md
    }

    String getMD5() {
        MessageDigest digest = MessageDigest.getInstance("MD5")
        digest.update([id:id, name:name, modified:(lastUpdated ?: dateCreated)].toString().getBytes("UTF-8"))
        for(f in folders) {
            digest.update([id:f.id, name:f.name, modified:(f.lastUpdated ?: f.dateCreated)].toString().getBytes("UTF-8"))
        }
        for(f in files) {
            digest.update(f.getMetadata().toString().getBytes("UTF-8"))
        }
        new BigInteger(1, digest.digest()).toString(16)
    }

    String toString() {
        name.toString()
    }

}

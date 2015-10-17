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

import grails.events.Listener
import grails.plugin.cache.CacheEvict
import grails.plugin.cache.Cacheable
import grails.plugins.crm.core.CrmCoreService
import grails.plugins.crm.core.PagedResultList
import grails.plugins.crm.core.SearchUtils
import grails.plugins.crm.core.TenantUtils
import grails.plugins.selection.Selectable
import grails.util.GrailsNameUtils
import groovy.transform.CompileStatic
import org.apache.commons.io.FilenameUtils
import org.springframework.web.multipart.MultipartFile

import java.util.regex.Pattern

class CrmContentService {

    CrmContentProviderFactory crmContentProviderFactory

    def grailsApplication
    CrmCoreService crmCoreService
    def crmSecurityService
    def crmTagService
    def grailsCacheManager

    public static final String CONTENT_PATH_CACHE = 'crmContentPath'

    private static final Pattern DOMAIN_PATH_PATTERN = ~/^(\w+)\/(\d+)\/(.+)/
    private static final String FILE_SEPARATOR_PATTERN = '[/\\\\]+'

    @Listener(namespace = "crmContent", topic = "enableFeature")
    def enableFeature(event) {
        def tenant = event.tenant // [feature: feature, tenant: tenant, role:role, expires:expires]
        TenantUtils.withTenant(tenant) {
            crmTagService.createTag(name: CrmResourceFolder.name, multiple: true)
            crmTagService.createTag(name: CrmResourceRef.name, multiple: true)
        }
    }

    @Listener(namespace = "crmTenant", topic = "requestDelete")
    def requestDeleteTenant(event) {
        def tenant = event.id
        def count = 0
        count += CrmResourceFolder.countByTenantId(tenant)
        count += CrmResourceRef.countByTenantId(tenant)
        return count ? [namespace: 'crmContent', topic: 'deleteTenant'] : null
    }

    @Listener(namespace = "crmContent", topic = "deleteTenant")
    def deleteTenant(event) {
        def tenant = event.id
        def n = 0
        for (f in CrmResourceFolder.findAllByTenantIdAndParentIsNull(tenant)) {
            deleteFolder(f)
            n++
        }
        for (f in CrmResourceFolder.findAllByTenantId(tenant)) {
            deleteFolder(f)
            n++
        }
        log.warn("Deleted $n folder trees in tenant $tenant")

        n = 0
        def proxy = grailsApplication.mainContext.crmContentService
        for (m in CrmResourceRef.findAllByTenantId(tenant)) {
            if (proxy.deleteReference(m)) {
                n++
            }
        }
        log.warn("Deleted $n resources in tenant $tenant")
    }

    @CompileStatic
    protected String getReferenceIdentifier(object) {
        crmCoreService.getReferenceIdentifier(object)
    }

    @CompileStatic
    def getReference(String identifier) {
        crmCoreService.getReference(identifier)
    }

    /**
     * Empty query = search all records.
     *
     * @param params pagination parameters
     * @return List of CrmResourceFolder domain instances
     */
    @Selectable
    def list(Map params = [:]) {
        list([:], params)
    }

    /**
     * Find CrmResourceFolder instances filtered by query.
     *
     * @param query filter parameters
     * @param params pagination parameters
     * @return List of CrmResourceFolder domain instances
     */
    @Selectable
    def list(Map query, Map params) {
        def tenant = TenantUtils.tenant
        def result = []
        def nameQuery = query.name ? SearchUtils.wildcard(query.name) : null
        def offset = params.offset ? Integer.valueOf(params.offset.toString()) : 0
        def max = params.max ? Integer.valueOf(params.max.toString()) : null
        def taggedFolders
        def taggedFiles

        if (query.tags) {
            taggedFolders = crmTagService.findAllIdByTag(CrmResourceFolder, query.tags) ?: [0L]
            taggedFiles = crmTagService.findAllIdByTag(CrmResourceRef, query.tags) ?: [0L]
        }

        def folders = CrmResourceFolder.createCriteria().list(params.subMap(['sort', 'order'])) {
            eq('tenantId', tenant)
            if (taggedFolders) {
                inList('id', taggedFolders)
            }
            if (nameQuery) {
                or {
                    ilike('name', nameQuery)
                    ilike('title', nameQuery)
                }
            } else if (!query.tags) {
                isNull('parent')
            }
        }
        if (folders) {
            result.addAll(folders)
        }

        if (nameQuery || query.tags) {
            def files = CrmResourceRef.createCriteria().list(params.subMap(['sort', 'order'])) {
                eq('tenantId', tenant)
                if (taggedFiles) {
                    inList('id', taggedFiles)
                }
                like('ref', GrailsNameUtils.getPropertyName(CrmResourceFolder) + '@%')
                if (nameQuery) {
                    or {
                        ilike('name', nameQuery)
                        ilike('title', nameQuery)
                    }
                }
            }
            if (files) {
                result.addAll(files)
            }
        }

        result = result.sort { it[params.sort ?: 'name'] }

        def size = result.size()
        if (size && max) {
            result = paginate(result, max, offset)
        }
        return new PagedResultList(result, size)
    }

    @CompileStatic
    private List paginate(List list, Integer max, Integer offset) {
        offset = Math.min(Math.max(0, offset), list.size() - 1)
        list.subList(offset, Math.min(offset + max, list.size()))
    }

    /**
     * Check if current user is authorized to view this content.
     *
     * A CrmResourceRef is requested, then it must either be shared (public access)
     * or published (user logged in to the requested tenant).
     *
     * @param ref resource reference
     * @param authenticated if user is authenticated or not
     * @return true if user has permission to view content, false otherwise
     */
    boolean hasViewPermission(CrmResourceRef ref, boolean authenticated = false) {
        if (ref.shared) {
            return true
        }
        if (ref.published && (ref.tenantId == TenantUtils.tenant) && authenticated) {
            return true
        }
        return false
    }

    /**
     * Create a new resource and attach it to a reference.
     *
     * @param inputStream InputStream to read from
     * @param filename original file name
     * @param length file size in bytes
     * @param contentType MIME content type of file
     * @param reference a domain instance or a String
     * @param params optional parameters, like name and status
     * @return the created CrmResourceRef instance
     */
    @CacheEvict(value = "content", allEntries = true)
    CrmResourceRef createResource(InputStream inputStream, String filename, Long length, String contentType, Object reference, Map params = [:]) {
        if (reference == null) {
            throw new IllegalArgumentException("Parameter [reference] cannot be null")
        }
        def referenceIsDomain = crmCoreService.isDomainClass(reference)
        if (referenceIsDomain && !reference.ident()) {
            throw new RuntimeException(
                    "You must save the domain instance [$reference] before calling createResource")
        }
        def title = params.title ?: FilenameUtils.getBaseName(filename)
        def name = params.name ?: FilenameUtils.getName(filename)
        def normalizedName = CrmResourceRef.normalizeName(name)
        def username = params.username ?: crmSecurityService.currentUser?.username
        def provider = crmContentProviderFactory.getProvider(normalizedName, length, reference, username)
        def referenceIdentifier = crmCoreService.getReferenceIdentifier(reference)
        def tenant = TenantUtils.tenant
        def resource
        def uri
        try {
            resource = CrmResourceRef.createCriteria().get() {
                eq('tenantId', tenant)
                eq('name', normalizedName)
                eq('ref', referenceIdentifier)
            }
            if (!contentType) {
                contentType = guessContentType(name)
            }
            if (resource) {
                // TODO Is it better to create a new resource and update CrmResourceRef.ref ???
                // That way, other references to this resource will still point to the old content.
                // Immutable resources sounds more clean, but I'm not sure yet. Needs more thinking.
                if (params.overwrite) {
                    provider.update(resource.resource, inputStream, contentType)
                } else {
                    def metadata = provider.create(inputStream, contentType, normalizedName, username)
                    uri = metadata.uri
                    normalizedName = new Date().format("yyMMdd") + '-' + normalizedName // TODO make a sequence number
                    resource = new CrmResourceRef(tenantId: tenant, title: (title ?: name), name: normalizedName,
                            description: params.description, res: uri.toString(), ref: referenceIdentifier)
                }
            } else {
                def metadata = provider.create(inputStream, contentType, normalizedName, username)
                uri = metadata.uri
                resource = new CrmResourceRef(tenantId: tenant, title: (title ?: name), name: normalizedName,
                        description: params.description, res: uri.toString(), ref: referenceIdentifier)
            }
            def status = params.status != null ? params.status : CrmResourceRef.STATUS_PUBLISHED
            if (status instanceof Number) {
                resource.status = status
            } else if (status.isNumber()) {
                resource.status = Integer.valueOf(status.toString())
            } else {
                resource.statusText = status.toString()
            }
            resource.save(failOnError: true, flush: true)
        } catch (Exception e) {
            log.error("Failed to create resource [${filename}] in tenant $tenant", e)
            if (uri) {
                provider.delete(uri)
            }
            throw e
        }
        log.debug "${uri ? 'Created' : 'Updated'} resource [${resource.name}] in tenant $tenant"
        event(for: "crmContent", topic: (uri ? "created" : "updated"),
                data: [tenant: resource.tenantId, id: resource.id, name: resource.name])
        return resource
    }

    /**
     * Create a new resource and attach it to a reference.
     *
     * @param file file to read from
     * @param contentType MIME content type of file
     * @param reference a domain instance or a String
     * @param params optional parameters, like name and status
     * @return the created CrmResourceRef instance
     */
    CrmResourceRef createResource(File file, String contentType, Object reference, Map params = [:]) {
        def filename = file.name
        if (!contentType) {
            contentType = guessContentType(filename)
        }
        if (params.extension == false) {
            filename = FilenameUtils.removeExtension(filename)
        }
        def proxy = grailsApplication.mainContext.crmContentService
        def resource
        file.withInputStream { inputStream ->
            resource = proxy.createResource(inputStream, filename, file.length(), contentType, reference, params)
        }
        event(for: "crmContent", topic: "created", data: [tenant: resource.tenantId, id: resource.id, name: resource.name])
        return resource
    }

    @CompileStatic
    String guessContentType(String filename) {
        def ext = FilenameUtils.getExtension(filename)
        def contentType
        switch (ext) {
            case 'html':
            case 'htm':
            case 'ftl':
                contentType = 'text/html'
                break
            case 'xml':
                contentType = 'text/xml'
                break
            case 'txt':
            case 'ini':
                contentType = 'text/plain'
                break
            case 'png':
                contentType = 'image/png'
                break
            case 'jpg':
            case 'jpeg':
                contentType = 'image/jpeg'
                break
            case 'gif':
                contentType = 'image/gif'
                break
            default:
                contentType = 'application/octet-stream'
                break
        }
        contentType
    }

    /**
     * Create a new resource and attach it to a reference from a MultiPartFile instance.
     *
     * @param file the MultiPartFile content to save
     * @param reference a domain instance or a String
     * @param params optional parameters, like name and status
     * @return the created CrmResourceRef instance
     */
    CrmResourceRef createResource(MultipartFile file, Object reference, Map params = [:]) {
        def proxy = grailsApplication.mainContext.crmContentService
        def resource = proxy.createResource(file.inputStream, file.originalFilename, file.size, file.contentType, reference, params)
        event(for: "crmContent", topic: "created", data: [tenant: resource.tenantId, id: resource.id, name: resource.name])
        return resource
    }

    /**
     * Create a new text resource and attach it to a reference.
     *
     * @param text the text content to save
     * @param filename name of content when rendered as a file
     * @param reference a domain instance or a String
     * @param params optional parameters, like name and status
     * @return the created CrmResourceRef instance
     */
    CrmResourceRef createResource(String text, String filename, Object reference, Map params = [:]) {
        def contentType = params.contentType
        if (!contentType) {
            if (filename.endsWith(".html") || filename.endsWith(".htm")) {
                contentType = "text/html"
            } else {
                contentType = "text/plain"
            }
        }
        def proxy = grailsApplication.mainContext.crmContentService
        def resource = proxy.createResource(new ByteArrayInputStream(text.getBytes('UTF-8')), filename, text.length(), contentType, reference, params)
        event(for: "crmContent", topic: "created", data: [tenant: resource.tenantId, id: resource.id, name: resource.name])
        return resource
    }

    @CacheEvict(value = "content", allEntries = true)
    long updateResource(CrmResourceRef resource, InputStream inputStream, String contentType = null) {
        def uri = resource.resource
        def provider = crmContentProviderFactory.getProvider(uri)
        if (!provider) {
            throw new IllegalArgumentException("No content provider found for [$uri]")
        }
        // Update file content.
        long newLength = provider.update(uri, inputStream, contentType).bytes

        event(for: "crmContent", topic: "updated", data: [tenant: resource.tenantId, id: resource.id, name: resource.name])

        return newLength
    }

    /**
     * Add a reference to an existing resource.
     *
     * @param resource reference to an existing resource
     * @param reference a domain instance or a String
     * @param params optional parameters, like name and status
     * @return the created CrmResourceRef instance
     */
    CrmResourceRef addReference(CrmResourceRef resource, Object reference, Map params = [:]) {
        def referenceIsDomain = crmCoreService.isDomainClass(reference)
        if (referenceIsDomain && !reference.ident()) {
            throw new RuntimeException(
                    "You must save the domain instance [$reference] before calling addReference.")
        }
        def name = params.name ?: resource.name
        def title = params.title ?: name
        def ref = new CrmResourceRef(tenantId: TenantUtils.tenant, title: title, name: name,
                description: params.description, res: resource.resource.toString())
        def status = params.status ?: CrmResourceRef.STATUS_PUBLISHED
        if (status instanceof Number) {
            ref.status = status
        } else if (status.isNumber()) {
            ref.status = Integer.valueOf(status.toString())
        } else {
            ref.statusText = status.toString()
        }
        ref.reference = reference

        // interceptors
        if (reference.respondsTo('onAddResource')) {
            reference.onAddResource(ref)
        }
        log.debug "Added new reference [${ref.name}] to existing resource [${resource.name}]"
        ref.save(failOnError: true, flush: true)
    }

    /**
     * Lookup a CrmResourceRef instance by id.
     * @param id the primary key to lookup
     * @return CrmResourceRef instance
     */
    def getResourceRef(id) {
        CrmResourceRef.get(id)
    }

    String getUniqueName(String basename, Long tenant = null) {
        if (tenant == null) {
            tenant = TenantUtils.tenant
        }
        int revision = 0
        String name = basename
        while (CrmResourceRef.createCriteria().count() {
            eq('tenantId', tenant)
            eq('name', name)
        }) {
            if (revision) {
                name = "Kopia($revision) av $basename"
            } else {
                name = "Kopia av $basename"
            }
            revision++
        }
        return name
    }

    @CompileStatic
    def withInputStream(URI uri, Closure work) {
        def provider = crmContentProviderFactory.getProvider(uri)
        if (!provider) {
            throw new IllegalArgumentException("No content provider found for [$uri]")
        }
        provider.withInputStream(uri, work)
    }

    @CompileStatic
    long writeTo(URI uri, OutputStream out) {
        def provider = crmContentProviderFactory.getProvider(uri)
        if (!provider) {
            throw new IllegalArgumentException("No content provider found for [$uri]")
        }
        return provider.read(out, uri)
    }

    @CompileStatic
    Reader getReader(URI uri, String charsetName = null) {
        def provider = crmContentProviderFactory.getProvider(uri)
        if (!provider) {
            throw new IllegalArgumentException("No content provider found for [$uri]")
        }
        provider.getReader(uri, charsetName)
    }

    /**
     * Get metadata for a resource.
     * @param resource resource identifier
     * @return A Map with resource metadata
     */
    @CompileStatic
    @Cacheable("content")
    Map<String, Object> getMetadata(URI resource) {
        def provider = crmContentProviderFactory.getProvider(resource)
        if (!provider) {
            throw new IllegalArgumentException("No content provider found for [$resource]")
        }
        Map<String, Object> md = provider.getMetadata(resource)
        md.icon = getContentIcon((String) md.contentType)
        return md
    }

    @CompileStatic
    @Cacheable("content")
    long getLastModified(URI resource) {
        def provider = crmContentProviderFactory.getProvider(resource)
        if (!provider) {
            throw new IllegalArgumentException("No content provider found for [$resource]")
        }
        provider.getLastModified(resource)
    }

    /**
     * List all resources attached to a reference object.
     *
     * @param reference domain instance or reference name
     * @param params optional pagination or sorting parameters
     * @return List of CrmResourceRef instances
     */
    List<CrmResourceRef> findResourcesByReference(reference, params = [:]) {
        if (!reference) {
            throw new RuntimeException("Reference is null.")
        }
        def referenceIsDomain = crmCoreService.isDomainClass(reference)
        if (referenceIsDomain && !reference.ident()) {
            throw new RuntimeException("You must save the domain instance [$reference] before calling findResourcesByReference.")
        }

        if (!params.sort) params.sort = 'name'
        if (!params.order) params.order = 'asc'
        if (params.cache == null) params.cache = true

        def ident = crmCoreService.getReferenceIdentifier(reference)

        def result = CrmResourceRef.createCriteria().list(params) {
            eq('ref', ident)
            if (params.status) {
                if (params.status instanceof Collection) {
                    inList('status', params.status)
                } else {
                    eq('status', params.status)
                }
            }
            if (params.title) {
                ilike('title', SearchUtils.wildcard(params.title))
            }
            if (params.name) {
                ilike('name', SearchUtils.wildcard(params.name))
            }
        }
        return result
    }

    @CompileStatic
    CrmResourceRef getAttachedResource(Object reference, String name, Integer status = null) {
        findResourcesByReference(reference, [name: name, status: status]).find { it }
    }

    /**
     * Return content as byte array
     * @param reference reference domain instance or reference name
     * @param name name of resource
     * @return resource content bytes
     */
    @CompileStatic
    byte[] getBytes(Object reference, String name) {
        def result = findResourcesByReference(reference, [name: name])
        if (!result) {
            return null
        }
        def res = result[0]
        def uri = res.resource
        def provider = crmContentProviderFactory.getProvider(uri)
        def length = provider.getLength(uri)
        def out = new ByteArrayOutputStream(length.intValue())
        provider.read(out, uri)
        return out.toByteArray()
    }

    /**
     * List all resource references that refer to the same resource identifier.
     *
     * @param resource resource identifier
     * @param params optional pagination or sorting parameters
     * @return List of CrmResourceRef instances
     */
    List findReferences(URI resource, params = [:]) {
        if (!resource) {
            throw new RuntimeException("Parameter [resource] cannot be null")
        }

        if (!params.sort) params.sort = 'ref'
        if (!params.order) params.order = 'asc'
        if (params.cache == null) params.cache = true

        CrmResourceRef.findAllByRes(resource.toString(), params)
    }

    /**
     * Delete all resource links associated with a reference object.
     *
     * If removeFile is true and reference count is zero, then the resource will also be removed.
     * @param reference domain instance or reference name
     * @return number of resources deleted
     */
    int deleteAllResources(def reference) {
        def proxy = grailsApplication.mainContext.crmContentService
        def links = findResourcesByReference(reference, [cache: false])
        int filesDeleted = 0
        for (l in links) {
            if (proxy.deleteReference(l)) {
                filesDeleted++
            }
        }
        log.trace "Deleted ${filesDeleted} resources attached to reference [${reference}]"
        return filesDeleted
    }

    /**
     * Delete a resource reference and it's associated resource.
     *
     * @param resourceRef CrmResourceRef instance to delete.
     * @return true if the resource was deleted, false if only the reference was deleted.
     */
    @CacheEvict(value = "content", allEntries = true)
    boolean deleteReference(CrmResourceRef resourceRef) {
        def resource = resourceRef.resource
        def provider = crmContentProviderFactory.getProvider(resource)
        def res = resourceRef.res
        resourceRef.delete(flush: true)
        if (CrmResourceRef.countByRes(res) == 0) {
            log.debug "Deleting [${resourceRef.name}] and it's resource"
            boolean deleted = provider.delete(resource)
            event(for: "crmContent", topic: "deleted", data: [tenant: resourceRef.tenantId, id: resourceRef.id, name: resourceRef.name])
            return deleted
        }
        log.debug "Deleted resource [${resourceRef.name}] but other references exists"
        return false
    }

    @CompileStatic
    CrmResourceFolder getFolder(String path, Long tenantId = TenantUtils.tenant) {
        getFolderByList(trimPath(path).split(FILE_SEPARATOR_PATTERN).toList(), tenantId)
    }

    @CompileStatic
    private String trimPath(String path) {
        // Trim leading and trailing slashes.
        while (path.startsWith('/') || path.startsWith('\\')) {
            path = path.substring(1)
        }
        while (path.endsWith('/') || path.endsWith('\\')) {
            path = path[0..-2]
        }
        return path
    }

    private CrmResourceFolder getFolderByList(List<String> path, Long tenantId) {
        def folder
        for (name in path) {
            def normalizedName = CrmResourceFolder.normalizeName(name)
            folder = CrmResourceFolder.createCriteria().get {
                eq('tenantId', tenantId)
                eq('name', normalizedName)
                if (folder) {
                    eq('parent.id', folder.ident())
                } else {
                    isNull('parent')
                }
                cache true
            }
            if (!folder) {
                break
            }
        }
        return folder
    }

    CrmResourceFolder createFolder(CrmResourceFolder parent, String name, String title = null, String description = null, String password = null) {
        // Use same tenant as parent, otherwise we screw up things completely.
        def tenant = parent?.tenantId ?: TenantUtils.tenant
        def normalizedName = CrmResourceFolder.normalizeName(name)
        def folder = CrmResourceFolder.createCriteria().get {
            eq('tenantId', tenant)
            eq('name', normalizedName)
            if (parent) {
                eq('parent.id', parent.ident())
            } else {
                isNull('parent')
            }
        }
        if (folder) {
            log.debug "Folder [${folder.name}] already exists in tenant $tenant"
        } else {
            folder = new CrmResourceFolder(tenantId: tenant, parent: parent, shared: parent?.shared == true, name: name, title: title, description: description)
            if (password != null) {
                if (password != "") {
                    def pair = crmSecurityService.hashPassword(password)
                    folder.passwordHash = pair.left
                    folder.passwordSalt = pair.right
                }
                folder.shared = true
            }
            folder.save(failOnError: true, flush: true)
            log.debug "Created folder [${folder.name}] in tenant $tenant"
        }
        if (parent) {
            parent.refresh()
        }
        return folder
    }

    CrmResourceFolder createFolders(String path) {
        def parts = FilenameUtils.normalize(trimPath(path)).split(FILE_SEPARATOR_PATTERN)
        def folder
        if (parts) {
            for (part in parts) {
                folder = createFolder(folder, part)
            }
        }
        return folder
    }

    String deleteFolder(CrmResourceFolder folder) {
        def tenant = folder.tenantId
        def tombstone = folder.toString()
        // Copy collection to avoid ConcurrentModificationException when removing.
        def children = []
        if (folder.folders) {
            children.addAll(folder.folders)
        }
        for (subFolder in children) {
            deleteFolder(subFolder)
        }
        def proxy = grailsApplication.mainContext.crmContentService
        for (file in folder.files) {
            proxy.deleteReference(file)
        }
        if (folder.parent) {
            folder.parent.removeFromFolders(folder)
        }
        folder.delete()
        log.debug "Deleted folder [${tombstone}] in tenant $tenant"
        return tombstone
    }

    def getContentByPath(String path, Long tenantId = TenantUtils.tenant) {
        if (path == null) {
            throw new IllegalArgumentException("CrmContentService#getContentByPath(String, Long) called with null path parameter")
        }
        path = trimPath(path)

        def pathAsList = path.split(FILE_SEPARATOR_PATTERN).toList()
        if (pathAsList.isEmpty()) {
            throw new IllegalArgumentException("Invalid content path: " + path)
        }

        def resource
        def cacheKey = tenantId.toString() + path
        def pathCache = grailsCacheManager.getCache(CONTENT_PATH_CACHE)
        def id = pathCache.get(cacheKey)?.get()
        if (id == 0L) {
            return null
        } else if (id != null) {
            resource = CrmResourceRef.get(id)
            if (resource) {
                return resource
            }
        }

        def ownerIdentifier
        def filename
        def matcher = path =~ DOMAIN_PATH_PATTERN
        if (matcher.matches()) {
            def domain = matcher.group(1)
            if (grailsApplication.domainClasses.find { it.propertyName == domain }) {
                ownerIdentifier = domain + '@' + matcher.group(2)
                filename = matcher.group(3)
                log.debug "$path is an attachment to $ownerIdentifier"
            } else {
                log.debug "$path looks like a domain path, but $domain is not a domain"
            }
        }

        if (!ownerIdentifier) {
            def folder = getFolderByList(pathAsList, tenantId)
            if (folder) {
                return folder.getFiles() // Content was a folder, return all it's files.
            }
            filename = pathAsList.pop()
            folder = getFolderByList(pathAsList, tenantId)
            if (folder) {
                ownerIdentifier = crmCoreService.getReferenceIdentifier(folder)
            }
        }

        if (ownerIdentifier) {
            resource = CrmResourceRef.createCriteria().get() {
                eq('tenantId', tenantId)
                eq('name', CrmResourceRef.normalizeName(filename))
                eq('ref', ownerIdentifier)
                cache true
            }
        }

        if (resource) {
            pathCache.put(cacheKey, resource.ident())
        } else {
            if (log.isDebugEnabled()) {
                log.debug "Content [$path] not found in tenant $tenantId"
            }
            pathCache.put(cacheKey, 0L)
        }

        return resource
    }

    String getAbsolutePath(Object content, boolean urlEncoded = false) {
        def parent
        if (content instanceof CrmResourceRef) {
            if (content.ref.startsWith('crmResourceFolder@')) {
                parent = content.reference
            } else {
                if (content.isShared() || content.isPublished()) {
                    def path = content.ref.split('@').toList()
                    path << content.name
                    if (urlEncoded) {
                        path = path.collect { it.encodeAsURL() }
                    }
                    return path.join('/')
                } else {
                    return null // This resource is not shared
                }
            }
        } else if (content instanceof CrmResourceFolder) {
            parent = content.parent
        } else {
            return null
        }
        def path = [content.name]
        def visited = [content]
        while (parent instanceof CrmResourceFolder) {
            if (visited.contains(parent)) {
                throw new RuntimeException("Circular reference found in content hierarchy $path")
            }
            path << parent.name
            visited << parent
            parent = parent.parent
        }
        if (urlEncoded) {
            path = path.collect { it.encodeAsURL() }
        }
        path.reverse().join('/')
    }

    /**
     * Copy single resource to another folder.
     * @param from the resource to copy
     * @param toFolder destination folder
     * @param newName new name or null to keep same name
     * @return the created copy
     */
    CrmResourceRef copy(CrmResourceRef from, CrmResourceFolder toFolder, String newName = null) {
        def metadata = getMetadata(from.resource)
        def ref
        if (!newName) {
            newName = from.name
        }
        def proxy = grailsApplication.mainContext.crmContentService
        withInputStream(from.resource) { inputStream ->
            ref = proxy.createResource(inputStream, newName, metadata.bytes, metadata.contentType, toFolder,
                    [title: from.title, description: from.description, status: from.status])
        }
        if (log.isDebugEnabled()) {
            log.debug "Resource [${from.name}] copied to [${getAbsolutePath(ref)}] content after copy: ${toFolder.files}"
        }
        return ref
    }

    /**
     * Copy a folder and all it's content, including sub-folders to a new folder.
     * @param fromFolder the folder to copy
     * @param toFolder where to copy
     * @param newName new name of folder or null is keep same name
     * @param newTitle new title of folder or null is keep same title
     * @return the created destination folder
     */
    CrmResourceFolder copy(CrmResourceFolder fromFolder, CrmResourceFolder toFolder, String newName = null, String newTitle = null) {
        if (!newName) {
            newName = fromFolder.name
        }
        def destinationFolder = createFolder(toFolder, newName, newTitle ?: fromFolder.title, fromFolder.description)
        destinationFolder.username = fromFolder.username
        destinationFolder.passwordHash = fromFolder.passwordHash
        destinationFolder.passwordSalt = fromFolder.passwordSalt
        destinationFolder.shared = fromFolder.shared
        for (subFolder in fromFolder.folders) {
            copy(subFolder, destinationFolder, subFolder.name)
        }
        for (file in fromFolder.files) {
            copy(file, destinationFolder, file.name)
        }
        destinationFolder.refresh()
        if (log.isDebugEnabled()) {
            log.debug "Folder [${getAbsolutePath(fromFolder)}] copied to [${getAbsolutePath(destinationFolder)}] content after copy: ${destinationFolder.folders} ${destinationFolder.files}"
        }
        return destinationFolder
    }

    public static final Map<String, String> mimeTypeIconMap = [
            'text'                                                    : 'page_white_text',
            'image'                                                   : 'image',
            'video'                                                   : 'film',
            'audio'                                                   : 'cd',
            'message'                                                 : 'email',
            'application/pdf'                                         : 'page_white_acrobat',
            'application/zip'                                         : 'page_white_compressed',
            'application/javascript'                                  : 'page_white_code',
            'application/json'                                        : 'page_white_code',
            'application/rtf'                                         : 'page_white_text',
            'application/msword'                                      : 'page_white_word',
            'application/vnd.ms-excel'                                : 'page_white_excel',
            'application/vnd.ms-powerpoint'                           : 'page_white_powerpoint',
            'application/vnd.ms-project'                              : 'page_white_office',
            'application/vnd.oasis.opendocument.chart'                : 'chart_line',
            'application/vnd.oasis.opendocument.chart-template'       : 'chart_line',
            'application/vnd.oasis.opendocument.database'             : 'database',
            'application/vnd.oasis.opendocument.formula'              : 'sum',
            'application/vnd.oasis.opendocument.formula-template'     : 'sum',
            'application/vnd.oasis.opendocument.graphics'             : 'image',
            'application/vnd.oasis.opendocument.graphics-template'    : 'image',
            'application/vnd.oasis.opendocument.image'                : 'image',
            'application/vnd.oasis.opendocument.image-template'       : 'image',
            'application/vnd.oasis.opendocument.presentation'         : 'page_white_powerpoint',
            'application/vnd.oasis.opendocument.presentation-template': 'page_white_powerpoint',
            'application/vnd.oasis.opendocument.spreadsheet'          : 'page_white_excel',
            'application/vnd.oasis.opendocument.spreadsheet-template' : 'page_white_excel',
            'application/vnd.oasis.opendocument.text'                 : 'page_white_text',
            'application/vnd.oasis.opendocument.text-master'          : 'page_white_text',
            'application/vnd.oasis.opendocument.text-template'        : 'page_white_text',
            'application/vnd.oasis.opendocument.text-web'             : 'page_white_world'
    ]

    /**
     * Returns a famfamfam (http://www.famfamfam.com/) icon name depending on MIME type.
     *
     * @param contentType the MIME content type to select icon for.
     * @param defaultIcon (optional) the default icon if MIME type is not recognized. Default is 'page_white'.
     */
    @CompileStatic
    String getContentIcon(String contentType, String defaultIcon = null) {
        if (!contentType) {
            return defaultIcon ?: 'page_white'
        }

        // Exact match?
        def icon = mimeTypeIconMap[contentType]
        if (icon) {
            return icon
        }

        // Major type match?
        icon = mimeTypeIconMap[contentType.split('/')[0]]
        if (icon) {
            return icon
        }

        // Microsoft Office
        if (contentType.startsWith('application/vnd.openxmlformats-officedocument')) {
            if (contentType.contains('presentationml')) {
                icon = 'page_white_powerpoint'
            } else if (contentType.contains('spreadsheetml')) {
                icon = 'page_white_excel'
            } else if (contentType.contains('wordprocessingml')) {
                icon = 'page_white_word'
            } else {
                icon = 'page_white_office'
            }
        }

        return icon ?: (defaultIcon ?: 'page_white')
    }

    long getResourceUsage(Long tenant = TenantUtils.tenant) {
        long total = 0L
        def result = CrmResourceRef.createCriteria().list() {
            projections {
                property('res')
            }
            eq('tenantId', tenant)
        }
        for (res in result) {
            def uri = new URI(res)
            def provider = crmContentProviderFactory.getProvider(uri)
            if (provider) {
                total += provider.getLength(uri)
            } else {
                log.warn("No content provider found for [$uri]")
            }
        }
        return total
    }

    /**
     * Check all files in all available content repositories that they really exists.
     * If no CrmResourceRef instance of found pointing to the file, the file will be deleted from the repository.
     * @return number of bytes deleted
     */
    long cleanup() {
        long size = 0L;
        for (p in crmContentProviderFactory.getProviders()) {
            size += p.check({ URI uri ->
                boolean rval
                try {
                    getLength(uri) // Throws exception if no CrmResourceRef instance is found.
                } catch (IllegalArgumentException e) {
                    delete(uri)
                    rval = true
                }
                return rval
            })
        }
        return size;
    }
}

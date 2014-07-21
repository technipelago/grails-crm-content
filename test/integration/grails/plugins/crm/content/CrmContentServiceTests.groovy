package grails.plugins.crm.content

import grails.plugins.crm.core.TenantUtils
import org.springframework.mock.web.MockMultipartFile

/**
 * Tests for CrmContentService.
 */
class CrmContentServiceTests extends GroovyTestCase {

    def grailsApplication
    def crmContentService

    void testCreateResource() {
        def entity = new TestContentEntity(name: "Foo").save(failOnError: true, flush: true)
        def ref = crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1.txt", "text/plain", "This is a test".getBytes()), entity)
        assert ref.title == "test1"
        assert ref.name == "test1.txt"
        assert ref.reference == entity
        assert ref.status == CrmResourceRef.STATUS_PUBLISHED
        assert ref.published
        assert !ref.archived
        assert !ref.draft
        assert ref.statusText == 'published'

        def ref2 = crmContentService.getResourceRef(ref.ident())
        assert ref2?.id == ref.id
        assert ref2?.title == ref.title
        assert ref2?.name == ref.name

        // Cleanup.
        crmContentService.deleteReference(ref)
        entity.delete()
    }

    void testCreateResourceFromInputStream() {
        def bytes = "This is a test".getBytes()
        def inputStream = new ByteArrayInputStream(bytes)
        def entity = new TestContentEntity(name: "Foo").save(failOnError: true, flush: true)
        def ref = crmContentService.createResource(inputStream, "test1.txt", bytes.length, "text/plain", entity)
        assert ref.title == "test1"
        assert ref.name == "test1.txt"
        assert ref.text == "This is a test"
        assert ref.reference == entity
        assert ref.status == CrmResourceRef.STATUS_PUBLISHED
        assert ref.published
        assert !ref.archived
        assert !ref.draft
        assert ref.statusText == 'published'

        def ref2 = crmContentService.getResourceRef(ref.ident())
        assert ref2?.id == ref.id
        assert ref2?.title == ref.title
        assert ref2?.name == ref.name

        // Cleanup.
        crmContentService.deleteReference(ref)
        entity.delete()
    }

    void testResourceFolder() {
        crmContentService.createFolder(null, "pub")
        def folder = crmContentService.getFolder("pub", 0)
        assert folder != null
        assert folder.name == "pub"
        crmContentService.createFolder(folder, "grails")
        folder = crmContentService.getFolder("pub/grails", 0)
        assert folder != null
        assert folder.name == "grails"
        crmContentService.createFolder(folder, "2.0.3")
        crmContentService.createFolder(folder, "2.0.3") // should return existing folder
        folder = crmContentService.getFolder("pub/grails/2.0.3", 0)
        assert folder != null
        assert folder.name == "2.0.3"
        assert crmContentService.getFolder("pub/grails/", 0)?.name == "grails"
        assert crmContentService.getFolder("/pub/grails/2.0.3", 0)?.name == "2.0.3"
        assert crmContentService.getFolder("/pub/grails/2.0.3/", 0)?.name == "2.0.3"
        assert crmContentService.getFolder("pub/grails/2.0.3/", 0)?.name == "2.0.3"
        // Test Windows paths
        assert crmContentService.getFolder("pub\\grails\\2.0.3", 0)?.name == "2.0.3"
    }

    void testCreateFolders() {
        def folder = crmContentService.createFolders("/pub/grails/2.0.4")
        assert folder.name == "2.0.4"
        def parent = folder.parent
        parent.refresh()
        assert parent?.name == "grails"
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1.txt", "text/plain", "This is file 1".getBytes()), folder)
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test2.txt", "text/plain", "This is file 2".getBytes()), folder)
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test3.txt", "text/plain", "This is file 3".getBytes()), folder)
        assert folder.files.size() == 3
        assert !folder.folders
        assert parent.folders.size() == 1
        crmContentService.deleteFolder(folder)
    }

    void testCreateWindowsFolders() {
        def folder = crmContentService.createFolders("pub\\grails\\2.0.4")
        assert folder.name == "2.0.4"
        assert folder.path.join('/') == 'pub/grails/2.0.4'
        def parent = folder.parent
        parent.refresh()
        assert parent?.name == "grails"
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1.ini", "text/plain", "This is windows file 1".getBytes()), folder)
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test2.ini", "text/plain", "This is windows file 2".getBytes()), folder)
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test3.ini", "text/plain", "This is windows file 3".getBytes()), folder)
        assert folder.files.size() == 3
        assert !folder.folders
        assert parent.folders.size() == 1
        crmContentService.deleteFolder(folder)
    }

    void testDeleteFolders() {
        crmContentService.createFolders("/multiple/folders/to/be/deleted")
        def folder = crmContentService.getFolder("/multiple/folders/to/")
        assert folder != null
        assert folder.name == "to"
        crmContentService.deleteFolder(folder)
        assert crmContentService.getFolder("/multiple/folders/to/") == null
        assert crmContentService.getFolder("/multiple/folders")?.name == "folders"
    }

    void testResourceFolderMetadata() {
        def folder = crmContentService.createFolders("/pub/grails/2.0.4")
        def hash1 = folder.getMD5()
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test.txt", "text/plain", "This is a test file".getBytes()), folder)
        def hash2 = folder.getMD5()
        assert hash1 != hash2

        def md = folder.metadata
        assert md.bytes == 0
        assert md.size == ''
        crmContentService.deleteFolder(folder)
    }

    void testResourceStatus() {
        def entity = new TestContentEntity(name: "Status Tester").save(failOnError: true, flush: true)

        assert crmContentService.createResource(new MockMultipartFile("file", "/tmp/file1.txt", "text/plain", "File 1".getBytes()), entity, [status: 0]).statusText == 'draft'
        assert crmContentService.createResource(new MockMultipartFile("file", "/tmp/file2.txt", "text/plain", "File 2".getBytes()), entity, [status: 'draft']).statusText == 'draft'
        assert crmContentService.createResource(new MockMultipartFile("file", "/tmp/file3.txt", "text/plain", "File 3".getBytes()), entity, [status: 'DRAFT']).statusText == 'draft'

        assert crmContentService.createResource(new MockMultipartFile("file", "/tmp/file4.txt", "text/plain", "File 4".getBytes()), entity, [status: 1]).statusText == 'published'
        assert crmContentService.createResource(new MockMultipartFile("file", "/tmp/file5.txt", "text/plain", "File 5".getBytes()), entity, [status: 'published']).statusText == 'published'
        assert crmContentService.createResource(new MockMultipartFile("file", "/tmp/file6.txt", "text/plain", "File 6".getBytes()), entity, [status: 'PUBLISHED']).statusText == 'published'

        assert crmContentService.createResource(new MockMultipartFile("file", "/tmp/file7.txt", "text/plain", "File 7".getBytes()), entity, [status: -1]).statusText == 'archived'
        assert crmContentService.createResource(new MockMultipartFile("file", "/tmp/file8.txt", "text/plain", "File 8".getBytes()), entity, [status: 'archived']).statusText == 'archived'
        assert crmContentService.createResource(new MockMultipartFile("file", "/tmp/file9.txt", "text/plain", "File 9".getBytes()), entity, [status: 'ARCHIVED']).statusText == 'archived'

        shouldFail {
            crmContentService.createResource(new MockMultipartFile("file", "/tmp/fail1.txt", "text/plain", "Fail 1".getBytes()), entity, [status: 9])
        }
        shouldFail {
            crmContentService.createResource(new MockMultipartFile("file", "/tmp/fai12.txt", "text/plain", "Fail 2".getBytes()), entity, [status: 'foo'])
        }

        assert crmContentService.findResourcesByReference(entity).size() == 9
        assert crmContentService.deleteAllResources(entity) == 9
        assert crmContentService.findResourcesByReference(entity).size() == 0
        entity.delete()
    }

    void testAddReference() {
        def entity1 = new TestContentEntity(name: "Project 1").save(failOnError: true, flush: true)
        def entity2 = new TestContentEntity(name: "Project 2").save(failOnError: true, flush: true)
        def entity3 = new TestContentEntity(name: "Project 3").save(failOnError: true, flush: true)
        def ref = crmContentService.createResource(new MockMultipartFile("file", "/tmp/process.txt", "text/plain", "Description of our business process".getBytes()), entity1)
        crmContentService.addReference(ref, entity2)
        crmContentService.addReference(ref, entity3)

        assert crmContentService.getContentByPath('/testContentEntity/' + entity1.ident() + '/process.txt')
        assert crmContentService.getContentByPath('/testContentEntity/' + entity2.ident() + '/process.txt')
        assert crmContentService.getContentByPath('/testContentEntity/' + entity3.ident() + '/process.txt')

        assert crmContentService.findResourcesByReference(entity1).size() == 1
        assert crmContentService.findResourcesByReference(entity2).size() == 1
        assert crmContentService.findResourcesByReference(entity3).size() == 1

        assert crmContentService.findReferences(ref.resource).size() == 3

        assert crmContentService.deleteAllResources(entity1) == 0
        assert crmContentService.deleteAllResources(entity2) == 0
        assert crmContentService.deleteAllResources(entity3) == 1
    }

    void testReadResource() {
        def entity = new TestContentEntity(name: "Foo").save(failOnError: true, flush: true)
        def ref = crmContentService.createResource(new MockMultipartFile("file", "/tmp/README.txt", "text/plain", "Read Me".getBytes()), entity)
        def file = File.createTempFile("grails-integration", "test")
        file.withOutputStream { out ->
            ref.writeTo(out)
        }
        assert file.text == "Read Me"
    }

    void testReader() {
        def entity = new TestContentEntity(name: "Reader Test").save(failOnError: true, flush: true)
        def ref = crmContentService.createResource(new MockMultipartFile("file", "/tmp/README.txt", "text/plain",
                "The quick brown fox jumps over the lazy dog".getBytes()), entity)
        def reader = crmContentService.getReader(ref.resource)
        def file = File.createTempFile("grails-integration", "test")
        file.withOutputStream { out ->
            int b
            while ((b = reader.read()) != -1) {
                out.write(b)
            }
        }
        assert file.text == "The quick brown fox jumps over the lazy dog"
    }

    void testMetadata() {
        def start = new Date()
        def entity = new TestContentEntity(name: "Foo").save(failOnError: true, flush: true)
        def bytes = ('0' * 1632).getBytes("UTF-8")
        def ref = crmContentService.createResource(new MockMultipartFile("file", "/tmp/zeroes.txt", "text/plain", bytes), entity)
        def metadata = ref.metadata
        def end = new Date()
        assert metadata.contentType == 'text/plain'
        assert metadata.bytes == 1632
        assert metadata.size == "2 kB"
        assert metadata.hash != null
        assert metadata.icon == 'page_white_text'
        def created = metadata.created
        assert created >= start && created <= end
        assert crmContentService.deleteAllResources(entity) == 1
        entity.delete()
    }

    void testMD5() {
        def entity = new TestContentEntity(name: "MD5 Tester").save(failOnError: true, flush: true)
        def res1 = crmContentService.createResource(new MockMultipartFile("file", "/tmp/hello1.txt", "text/plain", "Hello World!".getBytes()), entity)
        def md1 = res1.metadata
        assert md1.hash == "ed076287532e86365e841e92bfc50d8c"
        def res2 = crmContentService.createResource(new MockMultipartFile("file", "/tmp/hello2.txt", "text/plain", "Hello World!".getBytes()), entity)
        def md2 = res2.metadata
        assert md1.hash == md2.hash
        assert crmContentService.deleteAllResources(entity) == 2
        entity.delete()
    }

    void testEncryptedStorage() {
        // Configure AES encryption.
        grailsApplication.config.crm.content.encryption.algorithm = CrmFileResource.AES_ENCRYPTION
        def entity = new TestContentEntity(name: "Secret").save(failOnError: true, flush: true)
        def secretMessage = "This is the secret message"
        def ref = crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1.txt", "text/plain", secretMessage.getBytes("UTF-8")), entity)
        def md = ref.metadata
        // Make sure metadata says it's encrypted
        assert md.encrypted == CrmFileResource.AES_ENCRYPTION
        def resource = CrmFileResource.get(ref.resource.path.substring(1))
        assert resource != null
        def file = resource.getRawFile()
        assert md.bytes == 26
        assert file.length() == 32
        assert file.text != secretMessage

        def baos = new ByteArrayOutputStream()
        def bytesWritten = ref.writeTo(baos)
        assert bytesWritten == 26
        assert baos.toString() == secretMessage

        assert crmContentService.deleteAllResources(entity) == 1
        entity.delete()
    }

    void testCreateOverwriteResource() {
        def entity = new TestContentEntity(name: "Foo").save(failOnError: true, flush: true)
        def bytes = "This is a test".getBytes()
        def inputStream = new ByteArrayInputStream(bytes)
        def ref1 = crmContentService.createResource(inputStream, "test1.txt", bytes.length, "text/plain", entity)
        bytes = "This is an updated test".getBytes()
        inputStream = new ByteArrayInputStream(bytes)
        def ref2 = crmContentService.createResource(inputStream, "test1.txt", bytes.length, "text/plain", entity, [overwrite: true])
        assert ref1.res == ref2.res
        def result = new ByteArrayOutputStream()
        ref2.writeTo(result)
        def s = new String(result.toByteArray())
        assert s == "This is an updated test"
    }

    void testOverwriteWithEmptyResource() {
        def entity = new TestContentEntity(name: "Nothing").save(failOnError: true, flush: true)
        def bytes = "This is a test".getBytes()
        def inputStream = new ByteArrayInputStream(bytes)
        def ref1 = crmContentService.createResource(inputStream, "zerome.txt", bytes.length, "text/plain", entity)
        bytes = "".getBytes()
        inputStream = new ByteArrayInputStream(bytes)
        def ref2 = crmContentService.createResource(inputStream, "zerome.txt", bytes.length, "text/plain", entity, [overwrite: true])
        assert ref1.res == ref2.res
        def result = new ByteArrayOutputStream()
        ref2.writeTo(result)
        def s = new String(result.toByteArray())
        assert s == ""
    }

    void testCreateDuplicateResource() {
        def entity = new TestContentEntity(name: "Foo").save(failOnError: true, flush: true)
        def bytes = "This is a test".getBytes()
        def inputStream = new ByteArrayInputStream(bytes)
        def ref1 = crmContentService.createResource(inputStream, "test1.txt", bytes.length, "text/plain", entity)
        bytes = "This is an updated test".getBytes()
        inputStream = new ByteArrayInputStream(bytes)
        def ref2 = crmContentService.createResource(inputStream, "test1.txt", bytes.length, "text/plain", entity)
        assert ref1.res != ref2.res
        def result = new ByteArrayOutputStream()
        ref2.writeTo(result)
        def s = new String(result.toByteArray())
        assert s == "This is an updated test"
    }

    void testUpdateContent() {
        def entity = new TestContentEntity(name: "Hello World").save(failOnError: true, flush: true)
        def bytes = "This is a test".getBytes()
        def inputStream = new ByteArrayInputStream(bytes)
        def ref1 = crmContentService.createResource(inputStream, "test1.txt", bytes.length, "text/plain", entity)
        assert ref1.getMetadata().bytes == bytes.length
        bytes = "This is an updated test".getBytes()
        inputStream = new ByteArrayInputStream(bytes)
        assert crmContentService.updateResource(ref1, inputStream) == bytes.length
        assert ref1.getMetadata().bytes == bytes.length
        def result = new ByteArrayOutputStream()
        ref1.writeTo(result)
        def s = new String(result.toByteArray())
        assert s == "This is an updated test"
    }

    void testUpdateContentWithNoData() {
        def entity = new TestContentEntity(name: "Hello World").save(failOnError: true, flush: true)
        def bytes = "This is a test".getBytes()
        def inputStream = new ByteArrayInputStream(bytes)
        def ref1 = crmContentService.createResource(inputStream, "test1.txt", bytes.length, "text/plain", entity)
        assert ref1.getMetadata().bytes == bytes.length
        bytes = "".getBytes()
        inputStream = new ByteArrayInputStream(bytes)
        assert crmContentService.updateResource(ref1, inputStream) == 0L
        assert ref1.getMetadata().bytes == 0
        def result = new ByteArrayOutputStream()
        ref1.writeTo(result)
        def s = new String(result.toByteArray())
        assert s == ""
    }

    void testCopyResource() {
        def sourceFolder = crmContentService.createFolder(null, "source")
        def destinationFolder = crmContentService.createFolder(null, "dest")
        def sourceFile = crmContentService.createResource(new MockMultipartFile("file", "/tmp/test.txt", "text/plain", "This is the file".getBytes("UTF-8")), sourceFolder)
        def destFile = crmContentService.copy(sourceFile, destinationFolder, "copy.txt")
        assert sourceFile.name == "test.txt"
        assert destFile.name == "copy.txt"
        assert sourceFile.title == destFile.title

        crmContentService.deleteFolder(sourceFolder)
        crmContentService.deleteFolder(destinationFolder)
    }

    void testCopyFolder() {
        def sourceFolder = crmContentService.createFolder(null, "source")
        def destinationFolder = crmContentService.createFolder(null, "dest")
        def file1 = crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1.txt", "text/plain", "This is the first file".getBytes("UTF-8")), sourceFolder)
        def file2 = crmContentService.createResource(new MockMultipartFile("file", "/tmp/test2.txt", "text/plain", "This is the second file".getBytes("UTF-8")), sourceFolder)
        def file3 = crmContentService.createResource(new MockMultipartFile("file", "/tmp/test3.txt", "text/plain", "This is the third file".getBytes("UTF-8")), sourceFolder)
        def newFolder = crmContentService.copy(sourceFolder, destinationFolder, "newFolder")
        assert newFolder.name == "newFolder"
        def newFileFound = crmContentService.getContentByPath("dest/newFolder/test2.txt")
        assert newFileFound.name == file2.name
        assert newFileFound != file2
        def sourceFiles = sourceFolder.files
        def destFiles = newFolder.files
        assert sourceFiles.size() == 3
        assert destFiles.size() == 3
        for (f in destFiles) {
            assert f.metadata.contentType == "text/plain"
            assert crmContentService.getAbsolutePath(f).startsWith("dest/newFolder/test")
        }
        crmContentService.deleteFolder(sourceFolder)
        crmContentService.deleteFolder(destinationFolder)
    }

    void testCopyFoldersExtended() {
        def sourceFolder = crmContentService.createFolder(null, "source")
        crmContentService.createFolder(sourceFolder, "sub")
        def destinationFolder = crmContentService.createFolder(null, "dest")
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1.txt", "text/plain", "This is the first file".getBytes("UTF-8")), sourceFolder)
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test2.txt", "text/plain", "This is the second file".getBytes("UTF-8")), sourceFolder)
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test3.txt", "text/plain", "This is the third file".getBytes("UTF-8")), sourceFolder)
        crmContentService.copy(sourceFolder, destinationFolder, "ccdest")
        def newFolder1 = crmContentService.getFolder("dest/ccdest")
        assert crmContentService.getFolder("dest/ccdest/sub") != null
        assert crmContentService.getContentByPath("dest/ccdest/test1.txt") != null
        crmContentService.copy(newFolder1, destinationFolder, "ccdest2")
        def newFolder2 = crmContentService.getFolder("dest/ccdest2")
        assert crmContentService.getFolder("dest/ccdest2/sub") != null
        assert crmContentService.getContentByPath("dest/ccdest2/test1.txt") != null

        crmContentService.deleteFolder(newFolder1)
        crmContentService.copy(newFolder2, destinationFolder, "ccdest")
        assert crmContentService.getFolder("dest/ccdest/sub") != null
        assert crmContentService.getContentByPath("dest/ccdest/test1.txt") != null

        crmContentService.deleteFolder(sourceFolder)
        crmContentService.deleteFolder(destinationFolder)
    }

    void testResourceUsage() {
        TenantUtils.withTenant(50) {
            def folder = crmContentService.createFolder(null, "myfolder")
            crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1.txt", "text/plain", "This is the first file".getBytes("UTF-8")), folder)
            crmContentService.createResource(new MockMultipartFile("file", "/tmp/test2.txt", "text/plain", "This is the second file".getBytes("UTF-8")), folder)
            crmContentService.createResource(new MockMultipartFile("file", "/tmp/test3.txt", "text/plain", "This is the third file".getBytes("UTF-8")), folder)
        }
        assert crmContentService.getResourceUsage(50) == 67
    }
}

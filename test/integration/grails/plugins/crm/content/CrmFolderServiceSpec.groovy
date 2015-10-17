/*
 * Copyright 2014 Goran Ehrsson.
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

import org.springframework.mock.web.MockMultipartFile

/**
 * Test folder functions.
 */
class CrmFolderServiceSpec extends grails.test.spock.IntegrationSpec {

    def crmContentService
    def crmFolderService

    def "make zip archive of archive folder"() {
        given:
        def zipFile = new File("target/test-archive.zip")
        def folder = crmContentService.createFolder(null, "importantStuff")
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1.txt", "text/plain", "This is the first file".getBytes("UTF-8")), folder)
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test2.txt", "text/plain", "This is the second file".getBytes("UTF-8")), folder)
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test3.txt", "text/plain", "This is the third file".getBytes("UTF-8")), folder)

        when:
        zipFile.parentFile.mkdirs()
        zipFile.withOutputStream { out ->
            crmFolderService.archive(folder, out)
        }

        then:
        zipFile.length() > 400 && zipFile.length() < 500

        when:
        def backupFolder = crmContentService.createFolder(null, "backup")
        zipFile.withInputStream { inputStream ->
            crmFolderService.extract(inputStream, backupFolder, [status: CrmResourceRef.STATUS_SHARED])
        }

        then:
        backupFolder.files.size() == 3

        when:
        crmContentService.deleteFolder(folder)
        crmContentService.deleteFolder(backupFolder)

        then:
        crmContentService.getFolder("importantStuff") == null
    }
}

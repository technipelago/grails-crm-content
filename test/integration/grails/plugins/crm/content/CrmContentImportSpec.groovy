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

/**
 * Test spec for CrmContentImportService.
 */
class CrmContentImportSpec extends grails.plugin.spock.IntegrationSpec {

    def crmContentImportService
    def crmContentService

    def "files in src/templates should be imported"() {
        given: "create some files in src/templates/test-nnnnnnnn/html"
        def timestamp = System.currentTimeMillis()
        def folder = new File("./src/templates/test-${timestamp}/html")
        def indexContent = "<html><head><title>Test</title></head><body><h1>Test</h1></body></html>"
        def aboutContent = "<html><head><title>About</title></head><body><h1>Integration Test</h1></body></html>"

        when:
        folder.mkdirs()
        new File(folder, "index.html") << indexContent
        new File(folder, "about.html") << aboutContent

        then: "content folder html is empty"
        crmContentService.getFolder("html") == null

        when: "files are imported"
        try {
            crmContentImportService.importFiles("templates/test-$timestamp", "nobody")
        } finally {
            new File(folder, "about.html").delete()
            new File(folder, "index.html").delete()
            folder.delete() // html
            folder.parentFile.delete() // test-<timestamp>
            folder.parentFile.parentFile.delete() // templates
        }

        then: "files are stored in the html content folder"
        crmContentService.getFolder("html").files.size() == 2
        crmContentService.getContentByPath("html/index.html").text == indexContent
        crmContentService.getContentByPath("html/about.html").text == aboutContent
    }
}

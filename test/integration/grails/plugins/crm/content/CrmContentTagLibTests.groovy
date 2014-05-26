/*
 * Copyright (c) 2012 Goran Ehrsson.
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
 * under the License.
 */

package grails.plugins.crm.content

import grails.plugins.crm.core.TenantUtils
import grails.test.GroovyPagesTestCase
import org.springframework.mock.web.MockMultipartFile

/**
 * Tests for CrmContentTagLib
 */
class CrmContentTagLibTests extends GroovyPagesTestCase {

    def crmContentService

    void testResourceLink() {
        // Empty password creates a public shared older.
        def folder = crmContentService.createFolder(null, "test", "Test folder", "This folder is used by integration tests", "")
        def ref = crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1.txt", "text/plain", "File 1".getBytes()), folder, [status: 'draft'])
        assert ref.draft

        def template = '<crm:resourceLink class="btn" resource="\${ref}">View</crm:resourceLink>'
        assert applyTemplate(template, [ref: ref]) == "<a href=\"http://localhost:8080/crm-content/r/${ref.tenantId}/test/test1.txt\" class=\"btn\">View</a>".toString()

        // Cleanup.
        crmContentService.deleteFolder(folder)
    }

    void testCreateResourceLink() {
        // Empty password creates a public shared older.
        def folder = crmContentService.createFolder(null, "test", "Test folder", "This folder is used by integration tests", "")
        def ref = crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1.txt", "text/plain", "File 1".getBytes()), folder, [status: 'published'])
        assert ref.published

        def template = '<crm:createResourceLink resource="\${ref}"/>'
        assert applyTemplate(template, [ref: ref]) == "http://localhost:8080/crm-content/r/${ref.tenantId}/test/test1.txt".toString()

        // Cleanup.
        crmContentService.deleteFolder(folder)
    }

    void testRenderTagWithResource() {
        // Empty password creates a public shared older.
        def folder = crmContentService.createFolder(null, "foo", "Test folder", "This folder is used by integration tests", "")
        def ref = crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1.txt", "text/plain", "Hello \${who}".getBytes()), folder, [status: 'published'])
        assert ref.published

        def template = '<crm:render template="\${ref}" raw="true"/>'
        assert applyTemplate(template, [ref: ref, who: 'Grails']) == "Hello \${who}"

        template = '<crm:render template="\${ref}"/>'
        assert applyTemplate(template, [ref: ref, who: 'Grails']) == "Hello Grails"

        template = '<crm:render template="\${ref}" parser="freemarker"/>'
        assert applyTemplate(template, [ref: ref, who: 'Grails']) == "Hello Grails"

        // Cleanup.
        crmContentService.deleteFolder(folder)
    }

    void testRenderTagWithPath() {
        // Empty password creates a public shared older.
        def folder = crmContentService.createFolder(null, "bar", "Test folder", "This folder is used by integration tests", "")
        def ref = crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1.txt", "text/plain", "Hello \${who}".getBytes()), folder, [status: 'published'])
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1_no.txt", "text/plain", "Hei \${who}".getBytes()), folder, [status: 'published'])
        assert ref.published

        def template = '<crm:render template="/bar/test1.txt" raw="true"/>'
        assert applyTemplate(template, [who: 'Grails']) == "Hello \${who}"

        template = '<crm:render template="/bar/test1.txt"/>'
        assert applyTemplate(template, [who: 'Grails']) == "Hello Grails"

        template = '<crm:render template="/bar/test1.txt" parser="freemarker"/>'
        assert applyTemplate(template, [who: 'Grails']) == "Hello Grails"

        template = '<crm:render template="/bar/test1.txt" parser="freemarker"/>'
        assert applyTemplate(template, [locale: new Locale("no", "NO"), who: 'Grails']) == "Hei Grails"

        // Cleanup.
        crmContentService.deleteFolder(folder)
    }

    void testRenderTagWithInvalidPath() {
        assert applyTemplate('<crm:render template="/nonexisting/template.ftl"/>') == ""
        assert applyTemplate('<crm:render template="/nonexisting/template.ftl">default text</crm:render>') == "default text"
    }

    void testRenderTagWithFolder() {
        // Empty password creates a public shared older.
        def folder = crmContentService.createFolder(null, "bar", "Test folder", "This folder is used by integration tests", "")
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1.txt", "text/plain", "Hello \${who}".getBytes()), folder, [status: 'published'])
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test2.txt", "text/plain", "Hej \${who}".getBytes()), folder, [status: 'published'])
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test3.txt", "text/plain", "Bonjour \${who}".getBytes()), folder, [status: 'published'])
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test4.txt", "text/plain", "Guten Tag \${who}".getBytes()), folder, [status: 'archived'])

        def template = '<crm:render template="/bar" raw="true"/>'
        assert applyTemplate(template, [who: 'Grails']) == "Hello \${who}Hej \${who}Bonjour \${who}"

        template = '<crm:render template="/bar"/>'
        assert applyTemplate(template, [who: 'Grails']) == "Hello GrailsHej GrailsBonjour Grails"

        template = '<crm:render template="/bar" parser="freemarker"/>'
        assert applyTemplate(template, [who: 'Grails']) == "Hello GrailsHej GrailsBonjour Grails"

        // Cleanup.
        crmContentService.deleteFolder(folder)
    }

    void testRandomTemplate() {
        // Empty password creates a public shared older.
        def folder = crmContentService.createFolder(null, "bar", "Test folder", "This folder is used by integration tests", "")
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1.txt", "text/plain", "1".getBytes()), folder, [status: 'published'])
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test2.txt", "text/plain", "2".getBytes()), folder, [status: 'published'])
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test3.txt", "text/plain", "3".getBytes()), folder, [status: 'published'])
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test4.txt", "text/plain", "4".getBytes()), folder, [status: 'published'])
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test5.txt", "text/plain", "5".getBytes()), folder, [status: 'published'])

        assert applyTemplate('<crm:render template="/bar" parser="raw"/>') == "12345"
        assert applyTemplate('<crm:render template="/bar" parser="raw" max="3"/>') == "123"
        assert applyTemplate('<crm:render template="/bar" parser="raw" max="\${n}"/>', [n: 3]) == "123"
        assert applyTemplate('<crm:render template="/bar" parser="raw" max="9"/>') == "12345"
        assert applyTemplate('<crm:render template="/bar" parser="raw" max="0"/>') == "12345"
        def result = applyTemplate('<crm:render template="/bar" parser="raw" max="3" random="true"/>')
        assert result.length() == 3
        assert result != "123" // TODO This test could fail if the random result equals original.

        // Cleanup.
        crmContentService.deleteFolder(folder)
    }

    void testRenderTagMultiTenancy() {
        // Empty password creates a public shared older.
        TenantUtils.withTenant(1L) {
            def folder = crmContentService.createFolder(null, "bar", "Test folder in tenant #1", "This folder is used by tenant #1 integration tests", "")
            def ref = crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1.txt", "text/plain", "Hello \${who} in tenant \${tenant}".getBytes()), folder, [status: 'published'])
            def tenant = TenantUtils.tenant
            assert ref.published

            def template = '<crm:render tenant="\${tenant}" template="/bar/test1.txt" raw="true"/>'
            assert applyTemplate(template, [who: 'Grails', tenant: tenant]) == "Hello \${who} in tenant \${tenant}"

            template = '<crm:render tenant="\${tenant}" template="/bar/test1.txt"/>'
            assert applyTemplate(template, [who: 'Grails', tenant: tenant]) == "Hello Grails in tenant 1"

            template = '<crm:render tenant="\${tenant}" template="/bar/test1.txt" parser="freemarker"/>'
            assert applyTemplate(template, [who: 'Grails', tenant: tenant]) == "Hello Grails in tenant 1"
        }

        TenantUtils.withTenant(2L) {
            def folder = crmContentService.createFolder(null, "bar", "Test folder in tenant #2", "This folder is used by tenant #2 integration tests", "")
            def ref = crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1.txt", "text/plain", "Hello \${who} in tenant \${tenant}".getBytes()), folder, [status: 'published'])
            def tenant = TenantUtils.tenant
            assert ref.published

            def template = '<crm:render tenant="\${tenant}" template="/bar/test1.txt" raw="true"/>'
            assert applyTemplate(template, [who: 'Grails', tenant: tenant]) == "Hello \${who} in tenant \${tenant}"

            template = '<crm:render tenant="\${tenant}" template="/bar/test1.txt"/>'
            assert applyTemplate(template, [who: 'Grails', tenant: tenant]) == "Hello Grails in tenant 2"

            template = '<crm:render tenant="\${tenant}" template="/bar/test1.txt" parser="freemarker"/>'
            assert applyTemplate(template, [who: 'Grails', tenant: tenant]) == "Hello Grails in tenant 2"

            // Test cross-tenant rendering.
            template = '<crm:render tenant="1" template="/bar/test1.txt" parser="freemarker"/>'
            assert applyTemplate(template, [who: 'Grails']) == "Hello Grails in tenant 1"
        }

        TenantUtils.withTenant(1L) {
            // Test cross-tenant rendering.
            def template = '<crm:render tenant="2" template="/bar/test1.txt" parser="freemarker"/>'
            assert applyTemplate(template, [who: 'Grails']) == "Hello Grails in tenant 2"
            def folder = crmContentService.getFolder("bar")
            crmContentService.deleteFolder(folder) // Cleanup.
        }

        TenantUtils.withTenant(2L) {
            def folder = crmContentService.getFolder("bar")
            crmContentService.deleteFolder(folder) // Cleanup.
        }
    }

    void testFreemarker() {
        // Empty password creates a public shared older.
        def folder = crmContentService.createFolder(null, "freemarker", "Freemarker templates", "This folder is used by integration tests", "")
        def ref = crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1.ftl", "text/plain", "Hello \${who}".getBytes()), folder, [status: 'published'])
        crmContentService.createResource(new MockMultipartFile("file", "/tmp/test1_no.ftl", "text/plain", "Hei \${who}".getBytes()), folder, [status: 'published'])
        assert ref.published

        def template = '<crm:freemarker template="/freemarker/test1.ftl">This should not be printed</crm:freemarker>'
        assert applyTemplate(template, [who: 'Grails']) == "Hello Grails"

        template = '<crm:freemarker template="/freemarker/test1.ftl">This should not be printed</crm:freemarker>'
        assert applyTemplate(template, [locale: new Locale("no", "NO"), who: 'Grails']) == "Hei Grails"

        template = '<crm:freemarker template="/freemarker/crap.ftl">Template not found</crm:freemarker>'
        assert applyTemplate(template, [:]) == "Template not found"

        // Cleanup.
        crmContentService.deleteFolder(folder)
    }

    void testFreemarkerWithInvalidTemplate() {
        // Empty password creates a public shared older.
        def folder = crmContentService.createFolder(null, "freemarker", "Freemarker templates", "This folder is used by integration tests", "")
        def ref = crmContentService.createResource(new MockMultipartFile("file", "/tmp/fail.ftl", "text/plain", "Hello \${who}".getBytes()), folder, [status: 'published'])
        assert ref.published

        def template = '<crm:freemarker template="/freemarker/fail.ftl"/>'
        assert applyTemplate(template, [who: 'Grails']) == "Hello Grails"
        assert applyTemplate(template, [:]) == "Hello [ERROR in template: freemarker/fail.ftl]"

        // Cleanup.
        crmContentService.deleteFolder(folder)
    }
}

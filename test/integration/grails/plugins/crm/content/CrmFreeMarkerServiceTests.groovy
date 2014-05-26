package grails.plugins.crm.content

import grails.plugins.crm.core.TenantUtils
import grails.test.GroovyPagesTestCase
import org.springframework.mock.web.MockMultipartFile

/**
 * Test CrmFreeMakerService.
 */
class CrmFreeMarkerServiceTests extends GroovyPagesTestCase {

    def crmContentService
    def crmFreeMarkerService

    def testFreeMarker() {

        assert TenantUtils.withTenant(1L) {
            def folder = crmContentService.createFolders("/templates/freemarker")
            crmContentService.createResource(new MockMultipartFile("file", "/tmp/test.ftl", "text/plain",
                    "The quick brown \${animal1} jumps over the lazy \${animal2}".getBytes()), folder)

            def binding = [animal1: "fox", animal2: "dog"]

            Writer out = new StringWriter()

            crmFreeMarkerService.process("/templates/freemarker/test.ftl", binding, out)
            return out.toString()
        } == "The quick brown fox jumps over the lazy dog"
    }

    def testMessageDirectiveWithEnglishLocale() {
        assert TenantUtils.withTenant(1L) {
            def folder = crmContentService.createFolders("/templates/freemarker")
            crmContentService.createResource(new MockMultipartFile("file", "/tmp/msg.html", "text/plain",
                    """[@message code="crm.content.render.template.error" default="Fuckup in template {0}" args="foo"/]""".getBytes()), folder)

            def binding = [locale: new Locale("en")]

            Writer out = new StringWriter()

            crmFreeMarkerService.process("/templates/freemarker/msg.html", binding, out)
            return out.toString()
        } == """Error in template: foo"""
    }

    def testMessageDirectiveWithSwedishLocale() {
        assert TenantUtils.withTenant(1L) {
            def folder = crmContentService.createFolders("/templates/freemarker")
            crmContentService.createResource(new MockMultipartFile("file", "/tmp/msg.html", "text/plain",
                    """[@message code="crm.content.render.template.error" default="Fuckup in template {0}" args="foo"/]""".getBytes()), folder)

            def binding = [locale: new Locale("sv")]

            Writer out = new StringWriter()

            crmFreeMarkerService.process("/templates/freemarker/msg.html", binding, out)
            return out.toString()
        } == """FEL i mallen: foo"""
    }

    def testResourceDirective() {
        assert TenantUtils.withTenant(1L) {
            def folder = crmContentService.createFolders("/templates/freemarker")
            crmContentService.createResource(new MockMultipartFile("file", "/tmp/album.html", "text/html",
                    """<img src="[@resource dir='images/album' file='flower.jpg'/]" alt="Flower"/>""".getBytes()), folder)

            def binding = [image: "flower.jpg"]

            Writer out = new StringWriter()

            crmFreeMarkerService.process("/templates/freemarker/album.html", binding, out)
            return out.toString()
        } == """<img src="http://localhost:8080/crm-content/static/images/album/flower.jpg" alt="Flower"/>"""
    }

    def testLinkDirectiveRelative() {
        assert TenantUtils.withTenant(1L) {
            def folder = crmContentService.createFolders("/templates/freemarker")
            crmContentService.createResource(new MockMultipartFile("file", "/tmp/links1.html", "text/html",
                    "[@link controller='foo' action='bar' id='\${id}']Test[/@link]".getBytes()), folder)

            def binding = [id: 42]

            Writer out = new StringWriter()

            crmFreeMarkerService.process("/templates/freemarker/links1.html", binding, out)
            return out.toString()
        } == """<a href="/crm-content/foo/bar/42">Test</a>"""
    }

    def testLinkDirectiveAbsolute() {
        assert TenantUtils.withTenant(1L) {
            def folder = crmContentService.createFolders("/templates/freemarker")
            crmContentService.createResource(new MockMultipartFile("file", "/tmp/links2.html", "text/html",
                    "[@link controller='foo' action='bar' id='\${id}' absolute='true']Test[/@link]".getBytes()), folder)

            def binding = [id: 42]

            Writer out = new StringWriter()

            crmFreeMarkerService.process("/templates/freemarker/links2.html", binding, out)
            return out.toString()
        } == """<a href="http://localhost:8080/crm-content/foo/bar/42">Test</a>"""
    }

    def testLinkDirectiveMapping() {
        assert TenantUtils.withTenant(1L) {
            def folder = crmContentService.createFolders("/templates/freemarker")
            crmContentService.createResource(new MockMultipartFile("file", "/tmp/links3.html", "text/html",
                    "[@link mapping='test' id='\${id}']Test[/@link]".getBytes()), folder)

            def binding = [id: 42]

            Writer out = new StringWriter()

            crmFreeMarkerService.process("/templates/freemarker/links3.html", binding, out)
            return out.toString()
        } == """<a href="/crm-content/hej/hopp/42">Test</a>"""
    }

    def testImageDirective() {
        assert TenantUtils.withTenant(1L) {
            def folder = crmContentService.createFolders("/templates/freemarker")
            crmContentService.createResource(new MockMultipartFile("file", "/tmp/flowers.html", "text/html",
                    """[@img dir="images/album" file="\${image}" alt="Flower"/]""".getBytes()), folder)

            def binding = [image: "flower.jpg"]

            Writer out = new StringWriter()

            crmFreeMarkerService.process("/templates/freemarker/flowers.html", binding, out)
            return out.toString()
        } == """<img src="http://localhost:8080/crm-content/static/images/album/flower.jpg" alt="Flower"/>"""
    }

    def testXSSPrevention() {
        assert TenantUtils.withTenant(1L) {
            def folder = crmContentService.createFolders("/templates/freemarker")
            crmContentService.createResource(new MockMultipartFile("file", "/tmp/hacker.html", "text/html",
                    """[@img dir="images" file="\${image}"/]""".getBytes()), folder)

            def binding = [image: """flower.jpg"/><script>alert('Gotcha!')</script>"""]

            Writer out = new StringWriter()

            crmFreeMarkerService.process("/templates/freemarker/hacker.html", binding, out)
            return out.toString()
        } == """<img src="http://localhost:8080/crm-content/static/images/flower.jpg&quot;/&gt;&lt;script&gt;alert(&#39;Gotcha!&#39;)&lt;/script&gt;"/>"""
    }
}

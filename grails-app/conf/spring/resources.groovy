// Beans used in integration tests.
//
beans = {
    crmSecurityDelegate(grails.plugins.crm.content.TestSecurityDelegate)

    grailsLinkGenerator(org.codehaus.groovy.grails.web.mapping.DefaultLinkGenerator, 'http://localhost:8080/crm-content', '/crm-content')
}
// configuration for plugin testing - will not be included in the plugin zip

log4j = {
    // Example of changing the log pattern for the default console
    // appender:
    //
    //appenders {
    //    console name:'stdout', layout:pattern(conversionPattern: '%c{2} %m%n')
    //}

    error  'org.codehaus.groovy.grails.web.servlet',  //  controllers
           'org.codehaus.groovy.grails.web.pages', //  GSP
           'org.codehaus.groovy.grails.web.sitemesh', //  layouts
           'org.codehaus.groovy.grails.web.mapping.filter', // URL mapping
           'org.codehaus.groovy.grails.web.mapping', // URL mapping
           'org.codehaus.groovy.grails.commons', // core / classloading
           'org.codehaus.groovy.grails.plugins', // plugins
           'org.codehaus.groovy.grails.orm.hibernate', // hibernate integration
           'org.springframework',
           'org.hibernate',
           'net.sf.ehcache.hibernate'

    warn   'org.mortbay.log'

    //debug  'grails.app.services.grails.plugins.crm.content'
    //debug  'freemarker.cache'
}
grails.views.default.codec="none" // none, html, base64
grails.views.gsp.encoding="UTF-8"

grails.cache.config = {
    cache {
        name 'content'
    }

    //this is not a cache, it's a set of default configs to apply to other caches
    defaults {
        eternal false
        overflowToDisk false
        maxElementsInMemory 5000
        maxElementsOnDisk 0
        timeToLiveSeconds 300
        timeToIdleSeconds 0
    }
}

grails.doc.authors = "Göran Ehrsson, Technipelago AB"
grails.doc.license = "Licensed under the Apache License, Version 2.0 (the \"License\")"
grails.doc.copyright = "Copyright 2016 Technipelago AB"
grails.doc.footer = "- GR8 CRM - Content Management Plugin (crm-content)"
grails.doc.images = new File("src/docs/images")

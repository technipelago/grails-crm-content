import grails.plugins.crm.content.CrmFileResource
import grails.plugins.crm.content.CrmResourceFolder
import grails.plugins.crm.content.CrmResourceRef

config = {
// Cache used by CrmContentService to map a path (String) to a CrmResourceRef
    cache {
        name 'crmContentPath'
        eternal false
        overflowToDisk false
        maxElementsInMemory 1000
        maxElementsOnDisk 0
        timeToLiveSeconds 600
        timeToIdleSeconds 300
    }

// Hibernate domain class second-level caches.
    domain {
        name CrmResourceFolder
        eternal false
        overflowToDisk false
        maxElementsInMemory 1000
        maxElementsOnDisk 0
        timeToLiveSeconds 600
        timeToIdleSeconds 300
    }
    domain {
        name CrmResourceRef
        eternal false
        overflowToDisk false
        maxElementsInMemory 1000
        maxElementsOnDisk 0
        timeToLiveSeconds 600
        timeToIdleSeconds 300
    }
    domain {
        name CrmFileResource
        eternal false
        overflowToDisk false
        maxElementsInMemory 1000
        maxElementsOnDisk 0
        timeToLiveSeconds 600
        timeToIdleSeconds 300
    }
}

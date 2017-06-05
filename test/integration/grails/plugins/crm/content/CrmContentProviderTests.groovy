/*
 * Copyright (c) 2015 Goran Ehrsson.
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

import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters

/**
 * Created by goran on 15-10-17.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CrmContentProviderTests extends GroovyTestCase {

    def crmContentProviderFactory
    def crmContentService

    void test1CreateFileAndRememberCleanup() {
        def folder = crmContentService.createFolder(null, "hello")
        def resource = crmContentService.createResource("Hello World!", "hello.txt", folder)
        assert crmContentService.getContentByPath("/hello/hello.txt") != null
        def provider = crmContentProviderFactory.getProvider(resource.resource)
        assert provider.exists(resource.resource) == true
        crmContentService.deleteFolder(folder)
        assert provider.exists(resource.resource) == false
    }

    void test2RepositorySizeZero() {
        def providers = crmContentProviderFactory.getProviders()
        assert providers.size() > 0
        for (p in providers) {
            assert p.check({ true }) == 0 // Should be zero if all previous tests have removed its files
        }
    }

    void test3CreateFileAndForgetCleanup() {
        def folder = crmContentService.createFolder(null, "hello")
        crmContentService.createResource("Hello World!", "hello.txt", folder)
        assert crmContentService.getContentByPath("/hello/hello.txt") != null
    }

    void test4ReclaimSpace() {
        def folder = crmContentService.createFolder(null, "test")
        crmContentService.createResource("Test!", "test.txt", folder)

        assert crmContentService.getContentByPath("/hello/hello.txt") == null
        assert crmContentService.cleanup() == 12 // "Hello World!"

        def providers = crmContentProviderFactory.getProviders()
        assert providers.size() > 0
        for (p in providers) {
            assert p.check({ true }) == 5 // "Test!"
        }

        crmContentService.deleteFolder(folder)
        for (p in providers) {
            assert p.check({ true }) == 0 // Should be zero if all test files are removed
        }
    }
}

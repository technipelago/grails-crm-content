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

import spock.lang.Specification

/**
 * Unit tests for the domain class CrmResourceRef.
 */
class CrmResourceRefSpec extends Specification {

    def "if name is not set it should be set from title"() {

        when:
        def r = new CrmResourceRef(title: "Hello World.pdf")

        then:
        r.name == null

        when: "beforeValidate should copy title to name"
        r.beforeValidate()

        then: "name equals title"
        r.name == "Hello World.pdf"
    }

    def "illegal characters in filenames should be removed"() {
        given:
        def r = new CrmResourceRef(name: "*Göran***Ehrsson/\"Hello\\World\"|Foo':<test>?")

        when: "beforeValidate should normalize the filename"
        r.beforeValidate()

        then:
        r.name == ".Goran.Ehrsson-_Hello-World_-Foo_.(test)"

        when: "repeated calls to beforeValidate"
        r.beforeValidate()
        r.beforeValidate()
        r.beforeValidate()

        then: "should not change the name anymore"
        r.name == ".Goran.Ehrsson-_Hello-World_-Foo_.(test)"
    }
}

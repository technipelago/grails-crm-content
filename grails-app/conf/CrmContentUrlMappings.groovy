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

class CrmContentUrlMappings {
    static mappings = {
        /**
         * http://www.mycompany.com/s/1/crmProduct/1/icon-144.png
         */
        "/s/$t/$domain/$id/$file" {
            controller = "crmFileAccess"
            action = "show"
            constraints {
                t(matches: /\d+/)
                domain(matches: /\w+/)
                id(matches: /\d+/)
            }
        }
        /**
         * http://www.mycompany.com/r/1/folder/subfolder/file.pdf
         */
        "/r/$t/$uri**" {
            controller = "crmFileAccess"
            action = "show"
            constraints {
                t(matches: /\d+/)
            }
        }
        /**
         * http://www.mycompany.com/f/1/folder/subfolder
         */
        "/f/$t/$uri**" {
            controller = "crmFileAccess"
            action = "list"
            constraints {
                t(matches: /\d+/)
            }
        }
        "/freemarker/$t/clear/$path**?" {
            controller = "crmFreeMarker"
            action = "clear"
            constraints {
                t(matches: /\d+/)
            }
        }
    }
}

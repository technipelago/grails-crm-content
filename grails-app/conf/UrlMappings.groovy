class UrlMappings {

    static mappings = {
        name 'test': "/hej/hopp/$id?" {
            controller = 'hej'
            action = 'hopp'
            constraints {
                id(matches: /\d+/)
            }
        }
        "/$controller/$action?/$id?" {
            constraints {
                // apply constraints here
            }
        }

        "/"(view: "/index")
        "500"(view: '/error')
    }
}

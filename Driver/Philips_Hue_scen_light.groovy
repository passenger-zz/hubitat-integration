/**
 *  Philips Hue scen light
 *
 *  Author: Magnus Forslund
 *
 *  Date: 2023-11-03
 */
 metadata {
	definition (name: "Philips Hue scen light", namespace: "HUE", author: "magnus") {
		capability "Light"
	}

    preferences {
        input (
            name: "HueHubIP",
            type: "string",
            title: "Hue hub IP-adress",
            description: "Enter the Hue hub local IP-adress",
            required: true,
            displayDuringSetup: true
        )
        input (
            name: "HueHubApplicationKey",
            type: "string",
            title: "Hue hub appliation key",
            description: "Enter the Hue hub application key",
            required: true,
            displayDuringSetup: true
        )
        input (
            name: "OnSceneID",
            type: "string",
            title: "Scen ID to set for on",
            description: "Enter the scene ID to set for on",
            required: true,
            displayDuringSetup: true
        )
        input (
            name: "OffSceneID",
            type: "string",
            title: "Scen ID to set for off",
            description: "Enter the scene ID to set for off",
            required: true,
            displayDuringSetup: true
        )

        input name: "debug", type: "bool", title: "Enable debug logging", defaultValue: false
    }    
}

def on() {
    def url = "${getBasePath()}/${OnSceneID}".toURL()
    if(debug) {
        log.debug("ON URL: ${url}")
        log.debug("Application key: ${HueHubApplicationKey}")
    }
    try {
        def http = (HttpURLConnection) url.openConnection()
        http.setRequestMethod('PUT')
        //http.setRequestProperty("Host", HueHubIP)
        http.setRequestProperty("hue-application-key", HueHubApplicationKey)
        http.setRequestProperty("Content-Type", "application/json")
        //http.setRequestProperty("Accept", "application/json")
        http.setDoOutput(true)

        def payload = "{\"recall\":{\"action\":\"active\"}}"
        def bytes = payload.getBytes("utf-8")
        //http.setRequestProperty("Content-Length", bytes.length.toString())
            
        def out = http.getOutputStream()
        out.write(bytes)
        out.flush()
        out.close() 

        if(http.responseCode != 200) {
            def errorStream = http.getErrorStream()
            log.error("response: ${errorStream.text}")
        }
        
    } catch (e) {
        log.error "something went wrong: $e"
    }
}

def off() {
    def url = "${getBasePath()}/${OffSceneID}".toURL()
    if(debug) {
        log.debug("OFF URL: ${url}")
        log.debug("Application key: ${HueHubApplicationKey}")
    }
    try {
        def http = (HttpURLConnection) url.openConnection()
        http.setRequestMethod('PUT')
        //http.setRequestProperty("Host", HueHubIP)
        http.setRequestProperty("hue-application-key", HueHubApplicationKey)
        http.setRequestProperty("Content-Type", "application/json")
        //http.setRequestProperty("Accept", "application/json")
        http.setDoOutput(true)
        
        def payload = "{\"recall\":{\"action\":\"static\"}}"
        def bytes = payload.getBytes("utf-8")
        //http.setRequestProperty("Content-Length", bytes.length.toString())
        
        def out = http.getOutputStream()
        out.write(bytes)
        out.flush()
        out.close() 

        if(http.responseCode != 200) {
            def errorStream = http.getErrorStream()
            log.error("response: ${errorStream.text}")
        }
        
    } catch (e) {
        log.error "something went wrong: $e"
    }
}

def version() {
    return "0.1"
}

def initialize() {
	if(debug) log.debug("init")
}

def installed() {
	log.info "Installed"
    initialize()
}

def uninstalled() {
    log.info "Uninstalled."
}

def updated() {
	log.info "Updated."
    state.version = version()
    initialize()
}

private getBasePath() {
    def basePath = "http://${HueHubIP}/clip/v2/resource/scene";
    if(debug) log.debug("BasePath: ${basePath}");
    return basePath;
}
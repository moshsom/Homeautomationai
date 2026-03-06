/**
 *  Chatomation - Parent App
 *
 *  AI-powered home automations through natural conversation.
 *  The parent app owns all device references and handles ALL
 *  event subscriptions and command execution directly.
 *  Child apps manage only the conversation UI and rule creation.
 */

definition(
    name: "Chatomation",
    namespace: "chatomation",
    author: "Chatomation",
    description: "AI-powered home automations through natural conversation",
    category: "Automation",
    singleInstance: true,
    iconUrl: "",
    iconX2Url: "",
    importUrl: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Chatomation", install: true, uninstall: true) {

        section("<b>AI Configuration</b>") {
            input "aiProvider", "enum", title: "AI Provider",
                options: ["OpenAI", "Anthropic"], required: true, submitOnChange: true
            input "apiKey", "password", title: "API Key", required: true

            if (aiProvider == "OpenAI") {
                input "aiModel", "enum", title: "Model",
                    options: ["gpt-4o", "gpt-4o-mini", "gpt-4-turbo"],
                    defaultValue: "gpt-4o", required: true
            } else if (aiProvider == "Anthropic") {
                input "aiModel", "enum", title: "Model",
                    options: [
                        "claude-sonnet-4-5-20250929",
                        "claude-opus-4-6",
                        "claude-haiku-4-5-20251001"
                    ],
                    defaultValue: "claude-sonnet-4-5-20250929", required: true
            }
        }

        section("<b>Devices</b>") {
            input "useAllDevices", "bool",
                title: "Grant access to ALL devices (recommended)",
                defaultValue: true, submitOnChange: true

            if (useAllDevices) {
                paragraph "Select all devices from the two lists below. " +
                    "Actuators covers anything controllable (lights, switches, " +
                    "locks, thermostats, etc.). Sensors covers anything that " +
                    "reports data (motion, contact, temperature, etc.). " +
                    "Many devices appear in both — that's normal."
                input "allActuators", "capability.actuator",
                    title: "All Actuators (lights, switches, locks, etc.)",
                    multiple: true, required: false
                input "allSensors", "capability.sensor",
                    title: "All Sensors (motion, contact, temperature, etc.)",
                    multiple: true, required: false
            } else {
                paragraph "Select devices by category."
                input "lights", "capability.switch",
                    title: "Switches & Lights", multiple: true, required: false
                input "dimmers", "capability.switchLevel",
                    title: "Dimmers", multiple: true, required: false
                input "colorLights", "capability.colorControl",
                    title: "Color Lights", multiple: true, required: false
                input "colorTempLights", "capability.colorTemperature",
                    title: "Color Temperature Lights", multiple: true, required: false
                input "motionSensors", "capability.motionSensor",
                    title: "Motion Sensors", multiple: true, required: false
                input "contactSensors", "capability.contactSensor",
                    title: "Contact Sensors", multiple: true, required: false
                input "tempSensors", "capability.temperatureMeasurement",
                    title: "Temperature Sensors", multiple: true, required: false
                input "humiditySensors", "capability.relativeHumidityMeasurement",
                    title: "Humidity Sensors", multiple: true, required: false
                input "illuminanceSensors", "capability.illuminanceMeasurement",
                    title: "Light Level Sensors", multiple: true, required: false
                input "locks", "capability.lock",
                    title: "Locks", multiple: true, required: false
                input "thermostats", "capability.thermostat",
                    title: "Thermostats", multiple: true, required: false
                input "presenceSensors", "capability.presenceSensor",
                    title: "Presence Sensors", multiple: true, required: false
                input "waterSensors", "capability.waterSensor",
                    title: "Water / Leak Sensors", multiple: true, required: false
                input "shades", "capability.windowShade",
                    title: "Window Shades / Blinds", multiple: true, required: false
                input "garageDoors", "capability.garageDoorControl",
                    title: "Garage Doors", multiple: true, required: false
                input "fans", "capability.fanControl",
                    title: "Fans", multiple: true, required: false
                input "alarms", "capability.alarm",
                    title: "Alarms / Sirens", multiple: true, required: false
                input "speakers", "capability.speechSynthesis",
                    title: "Speech Devices", multiple: true, required: false
                input "valves", "capability.valve",
                    title: "Valves", multiple: true, required: false
            }
        }

        section("<b>Notifications</b>") {
            input "notifyDevices", "capability.notification",
                title: "Notification Devices (for error alerts)",
                multiple: true, required: false
        }

        section("<b>Logging</b>") {
            input "logLevel", "enum", title: "Log Level",
                options: ["Info", "Debug", "Trace"],
                defaultValue: "Info", required: true
        }

        section("<b>Automations</b>") {
            app(name: "automations", appName: "Chatomation Automation",
                namespace: "chatomation", title: "Create New Automation",
                multiple: true)
        }
    }
}

// ===========================================================================
//  Lifecycle
// ===========================================================================

def installed() {
    log.info "Chatomation: installed"
    initialize()
}

def updated() {
    log.info "Chatomation: updated"
    initialize()
}

def initialize() {
    log.info "Chatomation: initializing — provider=${aiProvider}, model=${aiModel}"
    // Rebuild all subscriptions for all child rules
    rebuildAllSubscriptions()
}

// ===========================================================================
//  Device helpers
// ===========================================================================

def getAllDevices() {
    def allDevs = []
    if (allActuators) allDevs.addAll(allActuators)
    if (allSensors)   allDevs.addAll(allSensors)

    if (!allDevs) {
        [lights, dimmers, colorLights, colorTempLights,
         motionSensors, contactSensors, tempSensors, humiditySensors,
         illuminanceSensors, locks, thermostats, presenceSensors,
         waterSensors, shades, garageDoors, fans, alarms, speakers, valves
        ].each { devList ->
            if (devList) allDevs.addAll(devList)
        }
    }
    return allDevs.unique { it.id }
}

def getDeviceById(deviceId) {
    def id = deviceId.toString()
    return getAllDevices().find { it.id.toString() == id }
}

def buildDeviceContext() {
    def devices = getAllDevices()
    if (!devices) return "No devices have been selected in the Chatomation parent app.\n"

    def sb = new StringBuilder()
    devices.each { dev ->
        try {
            sb.append("- ${dev.displayName}  (ID: ${dev.id})\n")
            def keyAttrs = ['switch', 'level', 'colorTemperature', 'hue', 'saturation',
                            'colorMode', 'motion', 'contact', 'temperature',
                            'humidity', 'illuminance', 'lock', 'thermostatMode',
                            'heatingSetpoint', 'coolingSetpoint', 'presence',
                            'water', 'windowShade', 'position', 'door',
                            'speed', 'alarm', 'valve', 'battery', 'power', 'energy']
            def stateItems = []
            keyAttrs.each { attr ->
                try {
                    def val = dev.currentValue(attr)
                    if (val != null) stateItems << "${attr}=${val}"
                } catch (ignored) {}
            }
            if (stateItems) sb.append("    State: ${stateItems.join(', ')}\n")
            sb.append("\n")
        } catch (e) {
            sb.append("- (device error)\n\n")
        }
    }
    return sb.toString()
}

// ===========================================================================
//  Hub helpers
// ===========================================================================

def getHubModes() { return location.modes.collect { it.name } }
def getCurrentMode() { return location.mode }

// ===========================================================================
//  AI API
// ===========================================================================

def callAI(String systemPrompt, List messages) {
    if (!apiKey) { log.error "Chatomation: no API key"; return null }
    def clean = messages.collect { [role: it.role, content: it.content] }
    if (aiProvider == "OpenAI") return callOpenAI(systemPrompt, clean)
    if (aiProvider == "Anthropic") return callAnthropic(systemPrompt, clean)
    return null
}

private callOpenAI(String systemPrompt, List messages) {
    def allMessages = [[role: "system", content: systemPrompt]] + messages
    def body = groovy.json.JsonOutput.toJson([
        model: aiModel ?: "gpt-4o", messages: allMessages,
        max_tokens: 4096, temperature: 0.7
    ])
    def params = [
        uri: "https://api.openai.com/v1/chat/completions",
        headers: ["Authorization": "Bearer ${apiKey}", "Content-Type": "application/json"],
        body: body
    ]
    def result = null
    try {
        httpPost(params) { resp ->
            def d = resp.data
            if (d instanceof String) d = new groovy.json.JsonSlurper().parseText(d)
            result = d?.choices?.getAt(0)?.message?.content
        }
    } catch (e) {
        log.error "Chatomation: OpenAI error: ${e.message}"
        try { if (e.response?.data) log.error "Chatomation: ${e.response.data}" } catch (ignored) {}
    }
    return result
}

private callAnthropic(String systemPrompt, List messages) {
    def body = groovy.json.JsonOutput.toJson([
        model: aiModel ?: "claude-sonnet-4-5-20250929", max_tokens: 4096,
        system: systemPrompt, messages: messages
    ])
    def params = [
        uri: "https://api.anthropic.com/v1/messages",
        headers: ["x-api-key": apiKey, "anthropic-version": "2023-06-01", "Content-Type": "application/json"],
        body: body
    ]
    def result = null
    try {
        httpPost(params) { resp ->
            def d = resp.data
            if (d instanceof String) d = new groovy.json.JsonSlurper().parseText(d)
            result = d?.content?.getAt(0)?.text
        }
    } catch (e) {
        log.error "Chatomation: Anthropic error: ${e.message}"
        try { if (e.response?.data) log.error "Chatomation: ${e.response.data}" } catch (ignored) {}
    }
    return result
}

// ===========================================================================
//  RULE REGISTRATION (called by child apps)
//  Child apps register their rules here. The parent handles all execution.
// ===========================================================================

def registerChildRule(String childId, Map rule, boolean enabled) {
    if (!state.childRules) state.childRules = [:]
    state.childRules[childId] = [rule: rule, enabled: enabled]
    log.info "Chatomation: registered rule '${rule.name}' for child ${childId}"
    rebuildAllSubscriptions()
}

def unregisterChildRule(String childId) {
    state.childRules?.remove(childId)
    log.info "Chatomation: unregistered rule for child ${childId}"
    rebuildAllSubscriptions()
}

def setChildRuleEnabled(String childId, boolean enabled) {
    if (state.childRules?.containsKey(childId)) {
        state.childRules[childId].enabled = enabled
        log.info "Chatomation: child ${childId} enabled=${enabled}"
        rebuildAllSubscriptions()
    }
}

// ===========================================================================
//  SUBSCRIPTION MANAGEMENT
//  Parent subscribes to ALL device events needed by ALL child rules.
// ===========================================================================

private rebuildAllSubscriptions() {
    unsubscribe()
    unschedule()

    if (!state.childRules) return

    // Reschedule sun events daily
    def hasSunEvents = false

    state.childRules.each { childId, data ->
        if (!data.enabled) return
        def rule = data.rule
        if (!rule?.actions) return

        rule.actions.each { action ->
            def trigger = action.trigger
            if (!trigger?.type) return

            switch (trigger.type) {
                case "device":
                    def dev = getDeviceById(trigger.deviceId)
                    if (dev) {
                        subscribe(dev, trigger.attribute, "masterDeviceHandler")
                        log.info "Chatomation: subscribed ${dev.displayName}.${trigger.attribute}"
                    } else {
                        log.warn "Chatomation: device ${trigger.deviceId} not found for subscription"
                    }
                    break
                case "time":
                    if (trigger.time) {
                        def parts = trigger.time.split(":")
                        schedule("0 ${parts[1]} ${parts[0]} ? * *", "masterTimeHandler")
                        log.info "Chatomation: scheduled at ${trigger.time}"
                    }
                    break
                case "sunrise":
                case "sunset":
                    hasSunEvents = true
                    scheduleSunEvent(trigger.type, trigger.offset ?: 0)
                    break
                case "mode":
                    subscribe(location, "mode", "masterModeHandler")
                    break
            }
        }
    }

    if (hasSunEvents) {
        schedule("0 5 0 ? * *", "rescheduleSunEvents")
    }
}

private scheduleSunEvent(String which, int offsetMinutes) {
    def sunTimes = getSunriseAndSunset()
    def base = (which == "sunrise") ? sunTimes.sunrise : sunTimes.sunset
    def target = new Date(base.time + (offsetMinutes * 60000L))
    def handler = (which == "sunrise") ? "masterSunriseHandler" : "masterSunsetHandler"
    if (target.after(new Date())) {
        runOnce(target, handler)
        log.info "Chatomation: ${which} scheduled for ${target}"
    }
}

def rescheduleSunEvents() {
    state.childRules?.each { childId, data ->
        if (!data.enabled) return
        data.rule?.actions?.each { action ->
            def t = action.trigger
            if (t?.type == "sunrise" || t?.type == "sunset") {
                scheduleSunEvent(t.type, t.offset ?: 0)
            }
        }
    }
}

// ===========================================================================
//  MASTER EVENT HANDLERS
//  These run in the parent's context with full device access.
// ===========================================================================

def masterDeviceHandler(evt) {
    state.childRules?.each { childId, data ->
        if (!data.enabled) return
        data.rule?.actions?.each { action ->
            def t = action.trigger
            if (t?.type != "device") return
            if (t.deviceId.toString() != evt.device.id.toString()) return
            if (t.attribute != evt.name) return
            if (t.value.toString() != evt.value.toString()) return
            processAction(childId, data.rule.name, action)
        }
    }
}

def masterTimeHandler() {
    def now = new Date()
    def nowTime = String.format("%02d:%02d", now.hours, now.minutes)
    state.childRules?.each { childId, data ->
        if (!data.enabled) return
        data.rule?.actions?.each { action ->
            if (action.trigger?.type == "time" && action.trigger.time == nowTime) {
                processAction(childId, data.rule.name, action)
            }
        }
    }
}

def masterSunriseHandler() {
    state.childRules?.each { childId, data ->
        if (!data.enabled) return
        data.rule?.actions?.each { action ->
            if (action.trigger?.type == "sunrise") {
                processAction(childId, data.rule.name, action)
            }
        }
    }
}

def masterSunsetHandler() {
    state.childRules?.each { childId, data ->
        if (!data.enabled) return
        data.rule?.actions?.each { action ->
            if (action.trigger?.type == "sunset") {
                processAction(childId, data.rule.name, action)
            }
        }
    }
}

def masterModeHandler(evt) {
    state.childRules?.each { childId, data ->
        if (!data.enabled) return
        data.rule?.actions?.each { action ->
            if (action.trigger?.type == "mode" &&
                action.trigger.value?.toString() == evt.value?.toString()) {
                processAction(childId, data.rule.name, action)
            }
        }
    }
}

// ===========================================================================
//  ACTION PROCESSING & COMMAND EXECUTION
// ===========================================================================

private processAction(String childId, String ruleName, Map action) {
    if (!checkConditions(action.conditions)) return

    if (action.cancelPendingDelay) {
        unschedule("delayedExec_${childId}")
        state.remove("pendingDelay_${childId}")
    }

    def delayMin = action.delay ? (action.delay as int) : 0
    if (delayMin > 0) {
        def delayId = now().toString()
        state["pendingDelay_${childId}"] = [id: delayId, commands: action.commands, ruleName: ruleName]
        runIn(delayMin * 60, "masterDelayedHandler", [data: [childId: childId, delayId: delayId]])
        log.info "Chatomation [${ruleName}]: commands delayed ${delayMin} min"
    } else {
        executeCommands(ruleName, action.commands)
    }
}

def masterDelayedHandler(data) {
    def key = "pendingDelay_${data.childId}"
    def pending = state[key]
    if (pending && pending.id == data.delayId) {
        executeCommands(pending.ruleName, pending.commands)
        state.remove(key)
    }
}

private checkConditions(List conditions) {
    if (!conditions) return true
    return conditions.every { c ->
        switch (c.type) {
            case "time":
                def now = new Date()
                def cur = now.hours * 60 + now.minutes
                Integer aft = null, bef = null
                if (c.after)  { def p = c.after.split(":");  aft = (p[0] as int) * 60 + (p[1] as int) }
                if (c.before) { def p = c.before.split(":"); bef = (p[0] as int) * 60 + (p[1] as int) }
                if (aft != null && bef != null) return (aft > bef) ? (cur >= aft || cur < bef) : (cur >= aft && cur < bef)
                if (aft != null) return cur >= aft
                if (bef != null) return cur < bef
                return true
            case "mode":
                return c.values?.contains(location.mode)
            case "device":
                def dev = getDeviceById(c.deviceId)
                if (!dev) return false
                return dev.currentValue(c.attribute)?.toString() == c.value?.toString()
            case "dayOfWeek":
                def days = ["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"]
                return c.days?.contains(days[new Date().day])
            default:
                return true
        }
    }
}

private executeCommands(String ruleName, List commands) {
    if (!commands) return
    commands.each { cmd ->
        def dev = getDeviceById(cmd.deviceId)
        if (!dev) {
            log.error "Chatomation [${ruleName}]: device ${cmd.deviceId} not found for '${cmd.command}'"
            sendNotification("Chatomation [${ruleName}]: device not found for '${cmd.command}'")
            return
        }
        try {
            if (cmd.args != null && cmd.args.size() > 0) {
                log.info "Chatomation [${ruleName}]: ${dev.displayName}.${cmd.command}(${cmd.args})"
                dev."${cmd.command}"(*cmd.args)
            } else {
                log.info "Chatomation [${ruleName}]: ${dev.displayName}.${cmd.command}()"
                dev."${cmd.command}"()
            }
        } catch (e) {
            log.error "Chatomation [${ruleName}]: ${cmd.command} failed on ${dev.displayName}: ${e.message}"
            sendNotification("Chatomation [${ruleName}]: '${cmd.command}' failed — ${e.message}")
        }
    }
}

// ===========================================================================
//  Notifications
// ===========================================================================

def sendNotification(String message) {
    if (notifyDevices) notifyDevices.each { it.deviceNotification(message) }
    log.info "Chatomation: ${message}"
}

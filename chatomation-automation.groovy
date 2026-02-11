/**
 *  Chatomation Automation - Child App
 *
 *  Each instance represents a single AI-created automation with its own
 *  conversation thread.  The user describes what they want in plain English,
 *  the AI asks clarifying questions if needed, then generates a structured
 *  rule that this app executes locally on the hub.
 */

definition(
    name: "Chatomation Automation",
    namespace: "chatomation",
    author: "Chatomation",
    description: "Individual AI-powered automation",
    category: "Automation",
    parent: "chatomation:Chatomation",
    iconUrl: "",
    iconX2Url: "",
    importUrl: ""
)

preferences {
    page(name: "mainPage")
    page(name: "chatPage")
}

// ===========================================================================
//  UI Pages
// ===========================================================================

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {

        section("<h2>${state.automationName ?: 'New Automation'}</h2>") {
            input "automationEnabled", "bool", title: "Enable Automation",
                defaultValue: true, submitOnChange: true
        }

        if (state.ruleDescription) {
            section("<b>What this automation does</b>") {
                paragraph state.ruleDescription
                def status = (automationEnabled != false) ? "Active" : "Paused"
                paragraph "<i>Status: ${status}</i>"
            }
        }

        section() {
            href "chatPage", title: "Open Chat",
                description: state.ruleDescription ?
                    "Modify this automation through conversation" :
                    "Describe the automation you want to create"
        }

        if (state.currentRule) {
            section("<b>Rule Details</b>") {
                def ruleText = groovy.json.JsonOutput.prettyPrint(
                    groovy.json.JsonOutput.toJson(state.currentRule))
                paragraph "<pre style='font-size:12px;'>${ruleText}</pre>"
            }
        }
    }
}

def chatPage() {
    dynamicPage(name: "chatPage", title: "Chatomation Chat", install: false, uninstall: false) {

        // ---- Conversation history ----
        section() {
            if (state.conversation) {
                state.conversation.each { msg ->
                    def timestamp = msg.ts ? "<span style='color:#999;font-size:11px;float:right;'>${msg.ts}</span>" : ""
                    if (msg.role == "user") {
                        paragraph "<div style='background:#f5f5f5;padding:10px 14px;" +
                            "border-radius:10px;margin:6px 0;border-left:4px solid #2196F3;'>" +
                            "${timestamp}<b>You:</b><br/>${escapeHtml(msg.content)}</div>"
                    } else if (msg.role == "assistant") {
                        // Hide raw JSON blocks from the display
                        def display = msg.content.replaceAll(
                            /(?s)```json\s*\{.*?\}\s*```/,
                            '<i>[automation rule generated]</i>')
                        paragraph "<div style='background:#e3f2fd;padding:10px 14px;" +
                            "border-radius:10px;margin:6px 0;border-left:4px solid #4CAF50;'>" +
                            "${timestamp}<b>Chatomation:</b><br/>${display}</div>"
                    }
                }
            }

            if (state.lastError) {
                paragraph "<div style='background:#ffebee;padding:10px;border-radius:8px;" +
                    "margin:6px 0;border-left:4px solid #f44336;'>" +
                    "<b>Error:</b> ${state.lastError}</div>"
                state.lastError = null
            }
        }

        // ---- Message input + spinner ----
        section() {
            // CSS spinner animation
            paragraph "<style>@keyframes chatomation-spin{0%{transform:rotate(0deg)}100%{transform:rotate(360deg)}}</style>"

            input "userMessage", "text", title: "Your message",
                required: false, submitOnChange: false
            input "sendMessage", "button", title: "Send"

            // Spinner + help text shown via JS when Send is clicked
            paragraph "<div id='chatomation-wait' style='display:none;margin-top:8px;'>" +
                "<span style='display:inline-block;width:18px;height:18px;" +
                "border:3px solid #ddd;border-top:3px solid #2196F3;border-radius:50%;" +
                "animation:chatomation-spin 1s linear infinite;vertical-align:middle;'></span>" +
                " <span style='color:#555;font-size:14px;vertical-align:middle;'>AI is thinking...</span></div>" +
                "<div id='chatomation-hint' style='color:#888;font-size:13px;margin-top:4px;'>" +
                "After clicking Send, please wait for the AI to respond.</div>" +
                "<script>document.querySelector('[name=sendMessage]')?.addEventListener('click',function(){" +
                "var w=document.getElementById('chatomation-wait');" +
                "if(w)w.style.display='block';" +
                "var h=document.getElementById('chatomation-hint');" +
                "if(h)h.style.display='none';});</script>"
        }
    }
}

// ===========================================================================
//  Button handler
// ===========================================================================

def appButtonHandler(String btn) {
    if (btn == "sendMessage") {
        def msg = settings.userMessage?.trim()
        if (msg) {
            processUserMessage(msg)
            app.updateSetting("userMessage", [type: "text", value: ""])
        }
    }
}

// ===========================================================================
//  Conversation + AI
// ===========================================================================

private processUserMessage(String message) {
    if (!state.conversation) state.conversation = []

    // Append user message with timestamp
    def now = new Date().format("yyyy-MM-dd h:mm a")
    state.conversation << [role: "user", content: message, ts: now]

    // Call AI
    def systemPrompt = buildSystemPrompt()
    def response = parent.callAI(systemPrompt, state.conversation)

    if (response) {
        def respTime = new Date().format("yyyy-MM-dd h:mm a")
        state.conversation << [role: "assistant", content: response, ts: respTime]

        // Look for a rule JSON in the response
        def ruleJson = extractRuleJson(response)
        if (ruleJson) {
            activateRule(ruleJson)
        }

        trimConversation()
    } else {
        def errTime = new Date().format("yyyy-MM-dd h:mm a")
        state.conversation << [role: "assistant", ts: errTime,
            content: "Sorry, I could not reach the AI service. " +
                     "Please check your API key in the Chatomation parent app settings " +
                     "and try again. Check Hubitat Logs for detailed error info."]
        state.lastError = "AI API call returned no response. " +
            "Open Hubitat Logs (gear icon → Logs) and look for 'Chatomation' errors."
    }
}

private trimConversation() {
    def max = 40
    if (state.conversation.size() > max) {
        // Keep the first two messages (welcome + first user message) and
        // the most recent messages.
        def keep = max - 2
        state.conversation = state.conversation[0..1] +
            state.conversation[-(keep)..-1]
    }
}

// ===========================================================================
//  System prompt
// ===========================================================================

private buildSystemPrompt() {
    def deviceCtx   = parent.buildDeviceContext()
    def modes       = parent.getHubModes()?.join(', ') ?: 'Default'
    def currentMode = parent.getCurrentMode() ?: 'Default'
    def existingRule = state.currentRule ?
        groovy.json.JsonOutput.toJson(state.currentRule) : "None yet"

    return """\
You are Chatomation, a friendly AI assistant that creates home automations for a Hubitat Elevation smart home hub. Have a natural conversation with the user to understand what they want, then generate a structured rule the hub can execute.

== AVAILABLE DEVICES ==
${deviceCtx}
== HUB MODES ==
Available: ${modes}
Current: ${currentMode}

== EXISTING RULE FOR THIS AUTOMATION ==
${existingRule}

== RULE JSON FORMAT ==
When you have enough information, output the rule inside a fenced code block:

```json
{
  "name": "Short name",
  "description": "Plain-English description of the automation",
  "actions": [
    {
      "trigger": { ... },
      "conditions": [ ... ],
      "commands": [ ... ],
      "delay": null,
      "cancelPendingDelay": false
    }
  ]
}
```

--- Trigger objects ---
Device event   : {"type":"device","deviceId":<id>,"attribute":"<attr>","value":"<val>"}
Scheduled time : {"type":"time","time":"HH:mm"}
Sunrise        : {"type":"sunrise","offset":<minutes>}   (negative = before)
Sunset         : {"type":"sunset","offset":<minutes>}
Mode change    : {"type":"mode","value":"<mode name>"}

--- Condition objects (all must be true) ---
Time window    : {"type":"time","after":"HH:mm","before":"HH:mm"}
Hub mode       : {"type":"mode","values":["Mode1","Mode2"]}
Device state   : {"type":"device","deviceId":<id>,"attribute":"<attr>","value":"<val>"}
Day of week    : {"type":"dayOfWeek","days":["Monday","Friday"]}

--- Command objects ---
{"deviceId":<id>,"command":"<cmd>","args":[<optional arguments>]}

Common commands:
  switch        : on(), off()
  dimmer        : setLevel(0-100), on(), off()
  color light   : setColor(hue 0-100, saturation 0-100, level 0-100), setColorTemperature(kelvin)
  lock          : lock(), unlock()
  thermostat    : setHeatingSetpoint(temp), setCoolingSetpoint(temp), setThermostatMode(mode)
  valve         : open(), close()
  shade         : open(), close(), setPosition(0-100)
  alarm         : strobe(), siren(), both(), off()
  garage door   : open(), close()
  fan           : setSpeed("low"/"medium-low"/"medium"/"medium-high"/"high"/"on"/"off"/"auto"), on(), off()
  speech        : speak("text")

--- Delays & cancellation ---
- "delay": <minutes>  makes the commands execute after a wait.
- "cancelPendingDelay": true  on another action cancels any pending delayed
  commands that target the same devices.
  Example — motion lighting:
    Action 1: motion active  -> lights on, cancelPendingDelay: true
    Action 2: motion inactive -> lights off, delay: 10

== GUIDELINES ==
1. Always reference devices by their numeric ID from the device list.
2. Use display names when talking to the user.
3. If the request is ambiguous, ask ONE clarifying question.
4. After outputting the JSON rule, add a short plain-English summary.
5. To modify an existing rule, output a complete replacement JSON.
6. Keep responses concise and conversational.
"""
}

// ===========================================================================
//  Rule parsing
// ===========================================================================

private extractRuleJson(String text) {
    def matcher = text =~ /(?s)```json\s*(\{.*?\})\s*```/
    if (matcher.find()) {
        try {
            return new groovy.json.JsonSlurper().parseText(matcher.group(1))
        } catch (e) {
            log.error "Chatomation: failed to parse rule JSON: ${e.message}"
            state.lastError = "The AI returned invalid JSON. Please try again."
        }
    }
    return null
}

// ===========================================================================
//  Rule activation
// ===========================================================================

private activateRule(Map rule) {
    log.info "Chatomation: activating rule '${rule.name}'"

    state.currentRule      = rule
    state.automationName   = rule.name
    state.ruleDescription  = rule.description

    app.updateLabel(rule.name ?: "Chatomation Automation")

    // Rebuild subscriptions
    initialize()
}

// ===========================================================================
//  Lifecycle
// ===========================================================================

def installed() {
    log.info "Chatomation Automation installed"
    state.conversation = [
        [role: "assistant",
         ts: new Date().format("yyyy-MM-dd h:mm a"),
         content: "Hi! I'm Chatomation. Describe the automation you'd like " +
                  "to create and I'll set it up for you.\n\n" +
                  "For example:\n" +
                  "- \"Turn on the porch light at sunset and off at sunrise.\"\n" +
                  "- \"When the front door opens, send me a notification.\"\n" +
                  "- \"If there's motion in the kitchen after 10 PM, turn the " +
                  "light on at 20 %. Turn it off after 10 minutes of no motion.\""]
    ]
    state.enabled = true
    initialize()
}

def updated() {
    log.info "Chatomation Automation updated"
    state.enabled = (automationEnabled != false)
    initialize()
}

def uninstalled() {
    unsubscribe()
    unschedule()
    log.info "Chatomation Automation uninstalled"
}

def initialize() {
    unsubscribe()
    unschedule()

    if (state.currentRule && state.enabled) {
        setupSubscriptions()
    }
}

// ===========================================================================
//  Subscription setup
// ===========================================================================

private setupSubscriptions() {
    def rule = state.currentRule
    if (!rule?.actions) return

    // Track which sun events we've already subscribed to
    def subscribedSun = [] as Set

    rule.actions.eachWithIndex { action, idx ->
        def trigger = action.trigger
        if (!trigger?.type) return

        switch (trigger.type) {
            case "device":
                def dev = parent.getDeviceById(trigger.deviceId)
                if (dev) {
                    subscribe(dev, trigger.attribute, "deviceEventHandler")
                    log.info "Chatomation: subscribed ${dev.displayName}.${trigger.attribute}"
                } else {
                    log.warn "Chatomation: device ID ${trigger.deviceId} not found"
                }
                break

            case "time":
                if (trigger.time) {
                    def parts = trigger.time.split(":")
                    def h = parts[0]
                    def m = parts[1]
                    schedule("0 ${m} ${h} ? * *", "scheduledTimeHandler")
                    log.info "Chatomation: scheduled daily at ${trigger.time}"
                }
                break

            case "sunrise":
                if (!subscribedSun.contains("sunrise")) {
                    scheduleSunEvent("sunrise", trigger.offset ?: 0)
                    subscribedSun << "sunrise"
                }
                break

            case "sunset":
                if (!subscribedSun.contains("sunset")) {
                    scheduleSunEvent("sunset", trigger.offset ?: 0)
                    subscribedSun << "sunset"
                }
                break

            case "mode":
                subscribe(location, "mode", "modeChangeHandler")
                log.info "Chatomation: subscribed to hub mode changes"
                break
        }
    }
}

private scheduleSunEvent(String which, int offsetMinutes) {
    def sunTimes = getSunriseAndSunset()
    def base = (which == "sunrise") ? sunTimes.sunrise : sunTimes.sunset
    def target = new Date(base.time + (offsetMinutes * 60000L))
    def handler = (which == "sunrise") ? "sunriseHandler" : "sunsetHandler"

    if (target.after(new Date())) {
        runOnce(target, handler)
        log.info "Chatomation: ${which} event scheduled for ${target}"
    } else {
        log.debug "Chatomation: ${which} event already passed today"
    }

    // Reschedule tomorrow at 00:05
    schedule("0 5 0 ? * *", "rescheduleSunEvents")
}

def rescheduleSunEvents() {
    def rule = state.currentRule
    if (!rule?.actions) return

    rule.actions.each { action ->
        def t = action.trigger
        if (t?.type == "sunrise") scheduleSunEvent("sunrise", t.offset ?: 0)
        if (t?.type == "sunset")  scheduleSunEvent("sunset",  t.offset ?: 0)
    }
}

// ===========================================================================
//  Event handlers
// ===========================================================================

def deviceEventHandler(evt) {
    if (!state.enabled) return
    def rule = state.currentRule
    if (!rule?.actions) return

    logDebug "Event: ${evt.device.displayName}.${evt.name} = ${evt.value}"

    rule.actions.each { action ->
        def t = action.trigger
        if (t?.type != "device") return
        if (t.deviceId.toString() != evt.device.id.toString()) return
        if (t.attribute != evt.name) return
        if (t.value.toString() != evt.value.toString()) return

        processTriggeredAction(action)
    }
}

def scheduledTimeHandler() {
    if (!state.enabled) return
    def rule = state.currentRule
    if (!rule?.actions) return

    def now = new Date()
    def nowTime = String.format("%02d:%02d", now.hours, now.minutes)

    logDebug "Scheduled handler fired at ${nowTime}"

    rule.actions.each { action ->
        if (action.trigger?.type == "time" && action.trigger.time == nowTime) {
            processTriggeredAction(action)
        }
    }
}

def sunriseHandler() {
    if (!state.enabled) return
    def rule = state.currentRule
    if (!rule?.actions) return

    logDebug "Sunrise handler fired"

    rule.actions.each { action ->
        if (action.trigger?.type == "sunrise") {
            processTriggeredAction(action)
        }
    }
}

def sunsetHandler() {
    if (!state.enabled) return
    def rule = state.currentRule
    if (!rule?.actions) return

    logDebug "Sunset handler fired"

    rule.actions.each { action ->
        if (action.trigger?.type == "sunset") {
            processTriggeredAction(action)
        }
    }
}

def modeChangeHandler(evt) {
    if (!state.enabled) return
    def rule = state.currentRule
    if (!rule?.actions) return

    logDebug "Mode changed to ${evt.value}"

    rule.actions.each { action ->
        if (action.trigger?.type == "mode" &&
            action.trigger.value?.toString() == evt.value?.toString()) {
            processTriggeredAction(action)
        }
    }
}

// ===========================================================================
//  Action processing
// ===========================================================================

private processTriggeredAction(Map action) {
    // Evaluate conditions
    if (!evaluateConditions(action.conditions)) {
        logDebug "Conditions not met — skipping action"
        return
    }

    // Cancel pending delayed commands if requested
    if (action.cancelPendingDelay) {
        unschedule("delayedCommandHandler")
        logDebug "Cancelled pending delayed commands"
    }

    def delayMinutes = action.delay ? (action.delay as int) : 0
    if (delayMinutes > 0) {
        def delaySec = delayMinutes * 60
        log.info "Chatomation: scheduling commands in ${delayMinutes} min"
        runIn(delaySec, "delayedCommandHandler",
              [data: [commands: action.commands]])
    } else {
        executeCommands(action.commands)
    }
}

def delayedCommandHandler(data) {
    if (!state.enabled) return
    log.info "Chatomation: executing delayed commands"
    executeCommands(data.commands)
}

// ===========================================================================
//  Condition evaluation
// ===========================================================================

private evaluateConditions(List conditions) {
    if (!conditions) return true
    return conditions.every { cond -> evaluateSingleCondition(cond) }
}

private evaluateSingleCondition(Map cond) {
    switch (cond.type) {
        case "time":      return checkTimeCond(cond)
        case "mode":      return checkModeCond(cond)
        case "device":    return checkDeviceCond(cond)
        case "dayOfWeek": return checkDayOfWeekCond(cond)
        default:
            log.warn "Chatomation: unknown condition type '${cond.type}'"
            return true
    }
}

private checkTimeCond(Map c) {
    def now = new Date()
    def cur = now.hours * 60 + now.minutes

    Integer aft = null
    Integer bef = null
    if (c.after)  { def p = c.after.split(":");  aft = (p[0] as int) * 60 + (p[1] as int) }
    if (c.before) { def p = c.before.split(":"); bef = (p[0] as int) * 60 + (p[1] as int) }

    if (aft != null && bef != null) {
        return (aft > bef) ? (cur >= aft || cur < bef)   // crosses midnight
                           : (cur >= aft && cur < bef)
    }
    if (aft != null) return cur >= aft
    if (bef != null) return cur < bef
    return true
}

private checkModeCond(Map c) {
    return c.values?.contains(location.mode)
}

private checkDeviceCond(Map c) {
    def dev = parent.getDeviceById(c.deviceId)
    if (!dev) {
        log.warn "Chatomation: condition device ${c.deviceId} not found"
        return false
    }
    return dev.currentValue(c.attribute)?.toString() == c.value?.toString()
}

private checkDayOfWeekCond(Map c) {
    def dayNames = ["Sunday","Monday","Tuesday","Wednesday",
                    "Thursday","Friday","Saturday"]
    return c.days?.contains(dayNames[new Date().day])
}

// ===========================================================================
//  Command execution
// ===========================================================================

private executeCommands(List commands) {
    if (!commands) return

    commands.each { cmd ->
        try {
            def dev = parent.getDeviceById(cmd.deviceId)
            if (!dev) {
                log.error "Chatomation: device ${cmd.deviceId} not found for " +
                          "command '${cmd.command}'"
                parent.sendNotification(
                    "Chatomation [${state.automationName}]: device not found " +
                    "for command '${cmd.command}'")
                return
            }

            if (cmd.args) {
                log.info "Chatomation: ${dev.displayName}.${cmd.command}(${cmd.args})"
                dev."${cmd.command}"(*cmd.args)
            } else {
                log.info "Chatomation: ${dev.displayName}.${cmd.command}()"
                dev."${cmd.command}"()
            }
        } catch (e) {
            log.error "Chatomation: command failed — ${cmd.command} on " +
                      "device ${cmd.deviceId}: ${e.message}"
            parent.sendNotification(
                "Chatomation [${state.automationName}]: command " +
                "'${cmd.command}' failed — ${e.message}")
        }
    }
}

// ===========================================================================
//  Helpers
// ===========================================================================

private escapeHtml(String text) {
    if (!text) return ""
    return text.replaceAll("&", "&amp;")
               .replaceAll("<", "&lt;")
               .replaceAll(">", "&gt;")
               .replaceAll("\n", "<br/>")
}

private logDebug(msg) {
    if (parent?.getSetting("logLevel") in ["Debug", "Trace"]) {
        log.debug "Chatomation [${state.automationName ?: app.label}]: ${msg}"
    }
}

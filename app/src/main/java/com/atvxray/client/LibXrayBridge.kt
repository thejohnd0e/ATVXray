package com.atvxray.client

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class LibXrayBridge(private val appContext: Context) {

    fun initControllers(protectFd: (Int) -> Boolean) {
        val candidates = listOf("libXray", "libxray")
        for (pkg in candidates) {
            runCatching {
                val iface = Class.forName("$pkg.DialerController")
                val libCls = Class.forName("$pkg.LibXray")

                val controller = Proxy.newProxyInstance(
                    iface.classLoader,
                    arrayOf(iface)
                ) { _, method, args ->
                    when (method.name) {
                        "ProtectFd", "protectFd" -> {
                            val value = (args?.firstOrNull() as? Number)?.toLong() ?: -1L
                            protectFd(value.toInt())
                        }
                        else -> null
                    }
                }

                findMethod(libCls, listOf("registerDialerController", "RegisterDialerController"), 1)
                    .invoke(null, controller)
                findMethod(libCls, listOf("initDns", "InitDns"), 2)
                    .invoke(null, controller, "1.1.1.1")
                return
            }
        }
    }

    fun resetDns() {
        runCatching { invokeLibMethod("ResetDns") }
    }

    fun prepareRuntimeConfig(vlessLink: String): String {
        setGoContextIfAvailable()
        val base = convertShareLink(vlessLink)
        val runtime = patchMinimalRuntime(base, vlessLink)
        return runtime.toString()
    }

    fun runXray(configJson: String, datDir: String, mphCachePath: String) {
        val req = JSONObject()
            .put("datDir", datDir)
            .put("mphCachePath", mphCachePath)
            .put("configJSON", configJson)

        val encodedReq = Codec.b64Encode(req.toString())
        val encodedResp = invokeLibMethod("RunXrayFromJSON", encodedReq)
        checkCallResponse(encodedResp)
    }

    fun stopXray() {
        val encodedResp = invokeLibMethod("StopXray")
        checkCallResponse(encodedResp)
    }

    private fun convertShareLink(vlessLink: String): JSONObject {
        val encodedReq = Codec.b64Encode(vlessLink.trim())
        val encodedResp = invokeLibMethod("ConvertShareLinksToXrayJson", encodedReq)
        val response = decodeCallResponse(encodedResp)
        return response.getJSONObject("data")
    }

    private fun patchMinimalRuntime(baseConfig: JSONObject, vlessLink: String): JSONObject {
        val inbounds = org.json.JSONArray()
            .put(
                JSONObject()
                    .put("listen", "127.0.0.1")
                    .put("port", 10808)
                    .put("protocol", "socks")
                    .put("tag", "socks-in")
                    .put(
                        "settings",
                        JSONObject()
                            .put("udp", true)
                            .put("auth", "noauth")
                    )
            )

        val outbounds = baseConfig.optJSONArray("outbounds") ?: org.json.JSONArray()
        sanitizeOutbounds(outbounds)
        ensureRealityServerNames(outbounds, vlessLink)
        ensureDirectOutbound(outbounds)

        val routing = baseConfig.optJSONObject("routing") ?: JSONObject()
        val existingRules = routing.optJSONArray("rules") ?: org.json.JSONArray()
        val rules = org.json.JSONArray()
        val bypassRule = JSONObject()
            .put("type", "field")
            .put("ip", org.json.JSONArray(AppConstants.PRIVATE_BYPASS))
            .put("outboundTag", AppConstants.DIRECT_TAG)
        rules.put(bypassRule)
        for (i in 0 until existingRules.length()) {
            rules.put(existingRules.get(i))
        }

        val mergedRouting = routing
            .put("domainStrategy", "IPIfNonMatch")
            .put("rules", rules)

        baseConfig.put("log", JSONObject().put("loglevel", "warning"))
        baseConfig.put("inbounds", inbounds)
        baseConfig.put("outbounds", outbounds)
        baseConfig.put("routing", mergedRouting)
        return baseConfig
    }

    private fun ensureDirectOutbound(outbounds: org.json.JSONArray) {
        var hasDirect = false
        for (i in 0 until outbounds.length()) {
            val item = outbounds.optJSONObject(i) ?: continue
            if (item.optString("tag") == AppConstants.DIRECT_TAG) {
                hasDirect = true
                break
            }
        }
        if (!hasDirect) {
            outbounds.put(
                JSONObject()
                    .put("tag", AppConstants.DIRECT_TAG)
                    .put("protocol", "freedom")
                    .put("settings", JSONObject())
            )
        }
    }

    private fun sanitizeOutbounds(outbounds: org.json.JSONArray) {
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue
            // libXray may store profile name in sendThrough; Xray expects IP/interface there.
            outbound.remove("sendThrough")
        }
    }

    private fun ensureRealityServerNames(outbounds: org.json.JSONArray, vlessLink: String) {
        val uri = runCatching { Uri.parse(vlessLink.trim()) }.getOrNull() ?: return
        val sni = uri.getQueryParameter("sni")?.trim().orEmpty()
        val pbk = uri.getQueryParameter("pbk")?.trim().orEmpty()
        val sid = uri.getQueryParameter("sid")?.trim().orEmpty()
        val fp = uri.getQueryParameter("fp")?.trim().orEmpty()
        val spx = uri.getQueryParameter("spx")?.trim().orEmpty()
        if (sni.isBlank()) return

        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue
            val stream = outbound.optJSONObject("streamSettings") ?: continue
            val security = stream.optString("security")
            if (!security.equals("reality", ignoreCase = true)) continue

            val old = stream.optJSONObject("realitySettings") ?: JSONObject()
            val normalized = JSONObject()
            // Force client mode REALITY settings only.
            normalized.put("serverName", sni)
            normalized.put(
                "fingerprint",
                old.optString("fingerprint").ifBlank { fp.ifBlank { "firefox" } }
            )
            val key = old.optString("publicKey").ifBlank {
                old.optString("password").ifBlank { pbk }
            }
            if (key.isNotBlank()) {
                normalized.put("publicKey", key)
                normalized.put("password", key)
            }
            val shortId = old.optString("shortId").ifBlank { sid }
            if (shortId.isNotBlank()) {
                normalized.put("shortId", shortId)
            }
            val spiderX = old.optString("spiderX").ifBlank { spx.ifBlank { "/" } }
            normalized.put("spiderX", if (spiderX.startsWith("/")) spiderX else "/")
            if (old.optString("mldsa65Verify").isNotBlank()) {
                normalized.put("mldsa65Verify", old.optString("mldsa65Verify"))
            }
            stream.put("realitySettings", normalized)
        }
    }

    private fun setGoContextIfAvailable() {
        runCatching {
            val seqClass = Class.forName("go.Seq")
            val method = seqClass.getMethod("setContext", Context::class.java)
            method.invoke(null, appContext)
        }
    }

    private fun invokeLibMethod(methodName: String, vararg args: String): String {
        val classNames = listOf("libXray.LibXray", "libxray.LibXray")
        var lastError: Throwable? = null
        for (className in classNames) {
            try {
                val cls = Class.forName(className)
                val candidates = listOf(
                    methodName,
                    methodName.replaceFirstChar { it.lowercase() },
                    methodName.replaceFirstChar { it.uppercase() }
                ).distinct()
                val method = findMethod(cls, candidates, args.size)
                return method.invoke(null, *args) as String
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw IllegalStateException("libXray method not found: $methodName", lastError)
    }

    private fun findMethod(cls: Class<*>, names: List<String>, argCount: Int): Method {
        val methods = cls.methods
        return methods.firstOrNull { m ->
            names.any { it.equals(m.name, ignoreCase = true) } && m.parameterTypes.size == argCount
        } ?: throw NoSuchMethodException("${cls.name}::${names.firstOrNull()}($argCount)")
    }

    private fun checkCallResponse(encodedBase64Json: String) {
        val response = decodeCallResponse(encodedBase64Json)
        if (!response.optBoolean("success", false)) {
            throw IllegalStateException(response.optString("error", "libXray call failed"))
        }
    }

    private fun decodeCallResponse(encodedBase64Json: String): JSONObject {
        val decoded = Codec.b64Decode(encodedBase64Json)
        val response = JSONObject(decoded)
        if (!response.optBoolean("success", false)) {
            throw IllegalStateException(response.optString("error", "libXray error"))
        }
        return response
    }
}

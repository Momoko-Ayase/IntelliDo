package moe.momokko.intellido.ui.jcef

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.JBCefJSQuery
import moe.momokko.intellido.browser.CloudflareChallenge
import moe.momokko.intellido.domain.content.LinuxDoMediaHosts
import moe.momokko.intellido.transport.LinuxDoJsonFetcher
import moe.momokko.intellido.transport.LinuxDoMediaLoader
import moe.momokko.intellido.transport.LinuxDoUrls
import moe.momokko.intellido.ui.startup.IntelliDoRuntime
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Timer

/**
 * Guest LINUX DO JSON via JCEF: open the HTML origin once, then `fetch()` APIs
 * in-page so Chromium's JSON viewer does not steal subsequent navigations.
 */
class JcefLinuxDoJsonFetcher : LinuxDoJsonFetcher, LinuxDoMediaLoader, AutoCloseable {
    private val logger = Logger.getInstance(JcefLinuxDoJsonFetcher::class.java)
    private val lock = Any()
    private val gate = JcefCallGate()
    private val memory = BoundedBytesCache()
    private val diskCache = HashedFileCache(
        Path.of(PathManager.getSystemPath(), "intellido-media", "avatars"),
    )
    private val pending = ConcurrentHashMap<Int, CompletableFuture<String>>()
    private val streamHandlers = ConcurrentHashMap<Int, (String) -> Unit>()
    private val originPending = AtomicReference<CompletableFuture<String>?>(null)
    private val originGen = AtomicInteger(0)
    private val jsGen = AtomicInteger(0)
    private var lastFinishedAt: Long = 0
    @Volatile
    private var originReady: Boolean = false
    private val browser: JBCefBrowser
    private val query: JBCefJSQuery
    private val loadHandler: CefLoadHandlerAdapter

    init {
        val client = JBCefApp.getInstance().createClient()
        client.setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 8)
        browser = JBCefBrowser.createBuilder()
            .setClient(client)
            .setOffScreenRendering(true)
            .setCreateImmediately(true)
            .build()
        JcefBrowserGuards.install(browser, pinLinuxDo = true, nativeStaysInCef = true)
        query = JBCefJSQuery.create(browser as JBCefBrowserBase)
        query.addHandler { body ->
            runCatching {
                val frame = parseStreamFrame(body) ?: return@runCatching
                when (frame.kind) {
                    StreamKind.CHUNK -> streamHandlers[frame.gen]?.invoke(frame.data)
                    StreamKind.DONE -> pending.remove(frame.gen)?.complete("")
                    StreamKind.ERROR -> pending.remove(frame.gen)?.complete(
                        if (frame.data.equals("abort", ignoreCase = true)) "" else "ERR ${frame.data}",
                    )
                    StreamKind.BODY -> pending.remove(frame.gen)?.complete(frame.data)
                }
            }
            JBCefJSQuery.Response("")
        }
        loadHandler = object : CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                cefBrowser: CefBrowser,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean,
            ) {
                if (isLoading) {
                    return
                }
                val gen = originGen.get()
                ApplicationManager.getApplication().invokeLater {
                    val timer = Timer(JcefFetchPolicy.ORIGIN_SETTLE_MS) {
                        if (originGen.get() == gen) {
                            originPending.get()?.complete("loaded")
                        }
                    }
                    timer.isRepeats = false
                    timer.start()
                }
            }

            override fun onLoadError(
                cefBrowser: CefBrowser,
                frame: CefFrame,
                errorCode: org.cef.handler.CefLoadHandler.ErrorCode,
                errorText: String,
                failedUrl: String,
            ) {
                if (frame.isMain) {
                    originPending.get()?.completeExceptionally(IllegalStateException(errorText))
                }
            }
        }
        browser.jbCefClient.addLoadHandler(loadHandler, browser.cefBrowser)
    }

    override fun post(path: String, form: Map<String, String>, timeoutSec: Long): String {
        val chunks = mutableListOf<String>()
        postStream(path, form, timeoutSec) { chunk -> chunks += chunk }
        return chunks.joinToString(" | ").ifBlank { "[]" }
    }

    override fun postStream(
        path: String,
        form: Map<String, String>,
        timeoutSec: Long,
        onChunk: (String) -> Unit,
    ): String {
        check(!ApplicationManager.getApplication().isDispatchThread) {
            "LINUX DO JCEF fetches must not run on the EDT"
        }
        prepareOrigin()
        val url = LinuxDoUrls.absolute(path)
        logger.info("Streaming LINUX DO $url")
        val status = runInPagePostStream(url, form, timeoutSec) { raw ->
            val extracted = extractPosted(raw)
            if (extracted.trim().startsWith("[")) {
                onChunk(extracted)
            }
        }
        if (status.startsWith("ERR")) {
            logger.warn("MessageBus stream failed for $url: ${status.take(160)}")
            throw IllegalStateException("无法加载")
        }
        return "[]"
    }

    override fun get(path: String): String {
        check(!ApplicationManager.getApplication().isDispatchThread) {
            "LINUX DO JCEF fetches must not run on the EDT"
        }
        gate.beginJson()
        try {
            prepareOrigin()
            val target = LinuxDoUrls.absolute(path)
            try {
                synchronized(lock) {
                    throttle(JcefFetchPolicy.JSON_GAP_MS)
                    return try {
                        fetchInPage(target)
                    } finally {
                        lastFinishedAt = System.currentTimeMillis()
                    }
                }
            } catch (_: ChallengeRequired) {
                completeViaChallenge()
                prepareOrigin()
                synchronized(lock) {
                    throttle(JcefFetchPolicy.JSON_GAP_MS)
                    return try {
                        fetchInPage(target, retriedChallenge = true)
                    } finally {
                        lastFinishedAt = System.currentTimeMillis()
                    }
                }
            }
        } finally {
            gate.endJson()
        }
    }

    override fun load(urls: List<String>, maxEdge: Int): Map<String, ByteArray> {
        check(!ApplicationManager.getApplication().isDispatchThread) {
            "LINUX DO JCEF media loads must not run on the EDT"
        }
        val loaded = linkedMapOf<String, ByteArray>()
        urls.distinct().forEach { url ->
            if (!isTrustedMediaUrl(url) || maxEdge < 1) {
                return@forEach
            }
            memory.get(url)?.let { bytes ->
                loaded[url] = bytes
                return@forEach
            }
            diskCache.read(url)?.let { bytes ->
                memory.put(url, bytes)
                loaded[url] = bytes
                return@forEach
            }
        }
        val missing = urls.distinct().filter { url ->
            isTrustedMediaUrl(url) && maxEdge >= 1 && url !in loaded
        }
        missing.chunked(JcefMediaBatch.CHUNK).forEach { chunk ->
            gate.yieldToJson()
            prepareOrigin()
            val batch = synchronized(lock) {
                throttle(JcefFetchPolicy.MEDIA_GAP_MS)
                val fetched = fetchMediaBatch(chunk)
                lastFinishedAt = System.currentTimeMillis()
                fetched
            }
            batch.forEach { (url, bytes) ->
                remember(url, bytes)
                loaded[url] = bytes
            }
            chunk.filter { it !in loaded }.forEach { url ->
                gate.yieldToJson()
                val bytes = synchronized(lock) {
                    memory.get(url)?.let { return@synchronized it }
                    throttle(JcefFetchPolicy.MEDIA_GAP_MS)
                    val fetched = fetchMediaBytes(url) ?: fetchCdnBytes(url)
                    lastFinishedAt = System.currentTimeMillis()
                    fetched
                }
                if (bytes == null) {
                    logger.warn("No media bytes for $url")
                    return@forEach
                }
                remember(url, bytes)
                loaded[url] = bytes
            }
        }
        return loaded
    }

    private fun remember(url: String, bytes: ByteArray) {
        memory.put(url, bytes)
        runCatching { diskCache.write(url, bytes) }
        logger.info("Received ${bytes.size} media bytes from $url")
    }

    private fun prepareOrigin() {
        if (originReady) {
            return
        }
        synchronized(lock) {
            if (originReady) {
                return
            }
            loadOrigin()
        }
        if (originReady) {
            return
        }
        completeViaChallenge()
        synchronized(lock) {
            loadOrigin()
            if (!originReady) {
                throw IllegalStateException("无法加载")
            }
        }
    }

    private fun loadOrigin() {
        originReady = false
        originGen.incrementAndGet()
        val future = CompletableFuture<String>()
        originPending.set(future)
        logger.info("Opening LINUX DO origin in JCEF")
        ApplicationManager.getApplication().invokeLater {
            browser.loadURL(LinuxDoUrls.ORIGIN + "/")
        }
        try {
            future.get(25, TimeUnit.SECONDS)
        } catch (error: Exception) {
            logger.warn("LINUX DO origin load did not finish: ${error.message}")
            return
        } finally {
            originPending.set(null)
            originGen.incrementAndGet()
        }
        if (originIsCommunity()) {
            originReady = true
            applyDiscourseLocale()
            logger.info("LINUX DO origin ready")
            return
        }
        Thread.sleep(JcefFetchPolicy.EMPTY_RETRY_MS)
        if (originIsCommunity()) {
            originReady = true
            applyDiscourseLocale()
            logger.info("LINUX DO origin ready after retry")
        } else {
            logger.info("LINUX DO origin is not the community shell")
        }
    }

    private fun applyDiscourseLocale() {
        val locale = JcefFetchPolicy.DISCOURSE_LOCALE
        val js = """
            document.cookie='locale=$locale;path=/;max-age=31536000;SameSite=Lax';
            if(window.I18n){ try{ I18n.locale='$locale'; }catch(e){} }
        """.trimIndent()
        ApplicationManager.getApplication().invokeLater {
            browser.cefBrowser.executeJavaScript(js, LinuxDoUrls.ORIGIN + "/", 0)
        }
    }

    private fun originIsCommunity(): Boolean {
        val gen = jsGen.incrementAndGet()
        val callback = query.inject("payload")
        val js = """
            var href=location.href;
            var chrome=!!document.querySelector('#site-logo,.d-header,#main-outlet,#site-text-logo,.d-header-wrap,.topic-list,.login-button');
            var text=(document.body&&document.body.innerText)?document.body.innerText.slice(0,400):'';
            var flag=chrome?'ready':'wait';
            var payload='$gen|'+flag+'::'+href+'::'+text;
            $callback
        """.trimIndent()
        val probe = CloudflareChallenge.parsePageProbe(awaitJs(js, 6, gen))
        return CloudflareChallenge.isCommunityShell(probe.url, probe.ready, probe.text)
    }

    private fun fetchInPage(url: String, retriedChallenge: Boolean = false): String {
        logger.info("Fetching LINUX DO $url via in-page fetch")
        repeat(JcefFetchPolicy.EMPTY_RETRY + 1) { attempt ->
            val body = runInPageFetch(url)
            val flat = JcefFetchPolicy.flattenJson(body)
            val extracted = runCatching { extractJson(flat) }.getOrDefault(flat)
            if (CloudflareChallenge.isExpectedPayload(url, extracted)) {
                logger.info("Received ${extracted.length} JSON characters from $url")
                return extracted
            }
            val challenge = JcefFetchPolicy.needsChallengeDialog(body) ||
                JcefFetchPolicy.needsChallengeDialog(extracted)
            if ((challenge || extracted.isBlank()) && attempt < JcefFetchPolicy.EMPTY_RETRY) {
                logger.info("Unusable payload on $url (attempt ${attempt + 1}); retrying")
                Thread.sleep(JcefFetchPolicy.EMPTY_RETRY_MS)
            } else if (challenge && !retriedChallenge) {
                logger.info("Challenge on $url; recovering origin")
                originReady = false
                throw ChallengeRequired()
            } else {
                logger.warn("Unexpected LINUX DO payload for $url (${extracted.length} chars): ${extracted.take(160)}")
                throw IllegalStateException("无法加载")
            }
        }
        throw IllegalStateException("无法加载")
    }

    private fun runInPageFetch(url: String): String {
        val gen = jsGen.incrementAndGet()
        val callback = query.inject("payload")
        val target = escapeJs(url)
        val js = """
            fetch('$target',{credentials:'include',headers:{'Accept-Language':'${JcefFetchPolicy.ACCEPT_LANGUAGE}'}})
              .then(function(r){return r.text();})
              .then(function(text){ var payload='$gen|'+String(text).replace(/[\r\n]+/g,' '); $callback })
              .catch(function(){ var payload='$gen|'; $callback });
        """.trimIndent()
        return awaitJs(js, 15, gen, abortBus = false)
    }

    private fun runInPagePostStream(
        url: String,
        form: Map<String, String>,
        timeoutSec: Long,
        onChunk: (String) -> Unit,
    ): String {
        val gen = jsGen.incrementAndGet()
        val callback = query.inject("payload")
        val appends = form.entries.joinToString(";") { (key, value) ->
            "b.append('${escapeJs(key)}','${escapeJs(value)}')"
        }
        val target = escapeJs(url)
        streamHandlers[gen] = onChunk
        val js = """
            (function(){
              if(window.__idmbAbort){try{window.__idmbAbort.abort();}catch(e){}}
              var ac=new AbortController();
              window.__idmbAbort=ac;
              var b=new URLSearchParams();
              $appends;
              function send(kind,text){
                var payload='$gen|'+kind+'|'+String(text).replace(/[\r\n]+/g,' ');
                $callback
              }
              fetch('$target',{method:'POST',mode:'cors',credentials:'include',body:b,signal:ac.signal})
                .then(function(r){
                  if(!r.body||!r.body.getReader){
                    return r.text().then(function(t){ send('C',t); send('D',''); });
                  }
                  var reader=r.body.getReader();
                  var dec=new TextDecoder();
                  var buf='';
                  function pump(){
                    return reader.read().then(function(res){
                      if(res.done){
                        if(buf.trim()) send('C',buf);
                        send('D','');
                        return;
                      }
                      buf+=dec.decode(res.value,{stream:true});
                      buf=buf.replace(/\r\n/g,'\n');
                      var parts=buf.split('\n|\n');
                      buf=parts.pop();
                      for(var i=0;i<parts.length;i++){
                        if(parts[i].trim()) send('C',parts[i]);
                      }
                      return pump();
                    });
                  }
                  return pump();
                })
                .catch(function(e){
                  var name=e&&e.name?e.name:'';
                  send('E', name==='AbortError'?'abort':(e&&e.message?e.message:e));
                });
            })();
        """.trimIndent()
        return try {
            awaitJs(js, timeoutSec, gen, abortBus = true)
        } finally {
            streamHandlers.remove(gen)
        }
    }

    private fun abortMessageBusPoll() {
        ApplicationManager.getApplication().invokeLater {
            browser.cefBrowser.executeJavaScript(
                "if(window.__idmbAbort){try{window.__idmbAbort.abort();}catch(e){}}",
                LinuxDoUrls.ORIGIN + "/",
                0,
            )
        }
    }

    private fun extractPosted(body: String): String {
        val flat = JcefFetchPolicy.flattenJson(body)
        return runCatching { extractJson(flat) }.getOrDefault(flat)
    }

    private fun escapeJs(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")

    private fun fetchMediaBatch(urls: List<String>): Map<String, ByteArray> {
        if (urls.isEmpty()) {
            return emptyMap()
        }
        val list = urls.joinToString(",") { url ->
            "'" + url.replace("\\", "\\\\").replace("'", "\\'") + "'"
        }
        val gen = jsGen.incrementAndGet()
        val callback = query.inject("payload")
        val unit = "String.fromCharCode(0x1F)"
        val rec = "String.fromCharCode(0x1E)"
        val max = JcefMediaBatch.MAX_FILE_BYTES
        val js = """
            (function(){
              var urls=[$list];
              var UNIT=$unit;
              var REC=$rec;
              function one(u){
                return fetch(u,{credentials:'include'}).then(function(r){return r.blob();}).then(function(b){
                  if(b.size<8||b.size>$max){ return ''; }
                  return new Promise(function(res){
                    var fr=new FileReader();
                    fr.onload=function(){ res(encodeURIComponent(u)+UNIT+fr.result); };
                    fr.onerror=function(){ res(''); };
                    fr.readAsDataURL(b);
                  });
                }).catch(function(){ return ''; });
              }
              Promise.all(urls.map(one)).then(function(parts){
                var payload='$gen|'+parts.filter(Boolean).join(REC);
                $callback
              }).catch(function(){ var payload='$gen|'; $callback });
            })();
        """.trimIndent()
        val payload = awaitJs(js, 12, gen)
        val decoded = JcefMediaBatch.decode(payload)
        if (decoded.isNotEmpty()) {
            logger.info("Batch media received ${decoded.size}/${urls.size} files")
        }
        return decoded
    }

    private fun fetchMediaBytes(url: String): ByteArray? {
        logger.info("Fetching LINUX DO media $url")
        val escaped = url.replace("\\", "\\\\").replace("'", "\\'")
        val startGen = jsGen.incrementAndGet()
        val startCb = query.inject("payload")
        val startJs = """
            fetch('$escaped',{credentials:'same-origin'})
              .then(function(r){return r.arrayBuffer();})
              .then(function(buf){
                var u8=new Uint8Array(buf);
                if(u8.length<8||u8.length>2500000){ var payload='$startGen|-1'; $startCb; return; }
                var s='';
                for(var i=0;i<u8.length;i+=8192){
                  s+=String.fromCharCode.apply(null,u8.subarray(i,Math.min(i+8192,u8.length)));
                }
                window.__idm=btoa(s);
                window.__idi=0;
                var payload='$startGen|'+window.__idm.length;
                $startCb;
              })
              .catch(function(){ var payload='$startGen|-1'; $startCb });
        """.trimIndent()
        val total = awaitJs(startJs, 6, startGen).trim().toIntOrNull() ?: return null
        if (total <= 0) {
            return null
        }
        val b64 = StringBuilder(total)
        var steps = 0
        while (b64.length < total && steps < 400) {
            steps++
            val gen = jsGen.incrementAndGet()
            val callback = query.inject("payload")
            val chunkJs = """
                var s=window.__idm||'';
                var i=window.__idi||0;
                var c=s.substring(i,i+8000);
                window.__idi=i+c.length;
                var payload='$gen|'+c;
                $callback
            """.trimIndent()
            val piece = awaitJs(chunkJs, 3, gen)
            if (piece.isEmpty()) {
                break
            }
            b64.append(piece)
        }
        ApplicationManager.getApplication().invokeLater {
            browser.cefBrowser.executeJavaScript("window.__idm='';window.__idi=0;", LinuxDoUrls.ORIGIN + "/", 0)
        }
        return runCatching { java.util.Base64.getDecoder().decode(b64.toString()) }.getOrNull()
    }

    private fun fetchCdnBytes(url: String): ByteArray? {
        logger.info("Fetching LINUX DO CDN media $url")
        val gen = jsGen.incrementAndGet()
        val callback = query.inject("payload")
        val escaped = url.replace("\\", "\\\\").replace("'", "\\'")
        val js = """
            (function(){
              var img=new Image();
              img.crossOrigin='anonymous';
              img.onload=function(){
                try{
                  var max=800;
                  var s=Math.min(1, max/Math.max(img.width,img.height));
                  var c=document.createElement('canvas');
                  c.width=Math.max(1,Math.round(img.width*s));
                  c.height=Math.max(1,Math.round(img.height*s));
                  c.getContext('2d').drawImage(img,0,0,c.width,c.height);
                  var payload='$gen|'+c.toDataURL('image/jpeg',0.82);
                  $callback;
                }catch(e){ var payload='$gen|'; $callback; }
              };
              img.onerror=function(){ var payload='$gen|'; $callback; };
              img.src='$escaped';
            })();
        """.trimIndent()
        val body = awaitJs(js, 6, gen)
        if (!JcefDataUrl.isImage(body)) {
            return null
        }
        return JcefDataUrl.decode(body)
    }

    private fun awaitJs(js: String, timeoutSec: Long, gen: Int, abortBus: Boolean = false): String {
        val future = CompletableFuture<String>()
        pending[gen] = future
        ApplicationManager.getApplication().invokeLater {
            browser.cefBrowser.executeJavaScript(js, LinuxDoUrls.ORIGIN + "/", 0)
        }
        return try {
            future.get(timeoutSec, TimeUnit.SECONDS)
        } catch (error: Exception) {
            logger.info("JCEF script timed out after ${timeoutSec}s: ${error.message}")
            if (abortBus) {
                abortMessageBusPoll()
            }
            future.complete("")
            ""
        } finally {
            pending.remove(gen)
            streamHandlers.remove(gen)
        }
    }

    private fun throttle(gapMs: Long) {
        val wait = lastFinishedAt + gapMs - System.currentTimeMillis()
        if (wait > 0) {
            Thread.sleep(wait)
        }
    }

    private fun isTrustedMediaUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() != "https") {
            return false
        }
        val host = uri.host?.lowercase() ?: return false
        return LinuxDoMediaHosts.isTrusted(host)
    }

    private fun completeViaChallenge() {
        val locale = runCatching { service<IntelliDoRuntime>().locale }.getOrDefault(java.util.Locale.SIMPLIFIED_CHINESE)
        logger.info("Opening Cloudflare challenge dialog")
        try {
            JcefChallengeDialog(browser.jbCefClient, locale).awaitPassed()
            logger.info("Challenge dialog completed")
        } catch (error: Exception) {
            logger.warn("Challenge dialog ended: ${error.message}")
            throw IllegalStateException("无法加载")
        }
        originReady = false
    }

    override fun close() {
        abortMessageBusPoll()
        pending.values.forEach { future -> future.complete("") }
        pending.clear()
        streamHandlers.clear()
        runCatching { browser.jbCefClient.removeLoadHandler(loadHandler, browser.cefBrowser) }
        runCatching { browser.jbCefClient.removeAllHandlers(browser.cefBrowser) }
        query.dispose()
        browser.dispose()
    }

    private class ChallengeRequired : RuntimeException()

    enum class StreamKind { BODY, CHUNK, DONE, ERROR }

    data class StreamFrame(val gen: Int, val kind: StreamKind, val data: String)

    companion object {
        fun parseStreamFrame(body: String): StreamFrame? {
            val sep = body.indexOf('|')
            if (sep <= 0) {
                return null
            }
            val gen = body.substring(0, sep).toIntOrNull() ?: return null
            val rest = body.substring(sep + 1)
            return when {
                rest == "D" || rest.startsWith("D|") -> StreamFrame(gen, StreamKind.DONE, "")
                rest.startsWith("C|") -> StreamFrame(gen, StreamKind.CHUNK, rest.substring(2))
                rest.startsWith("E|") -> StreamFrame(gen, StreamKind.ERROR, rest.substring(2))
                else -> StreamFrame(gen, StreamKind.BODY, rest)
            }
        }

        fun extractJson(source: String): String {
            val trimmed = source.trim()
            val obj = trimmed.indexOf('{')
            val arr = trimmed.indexOf('[')
            if (arr >= 0 && (obj < 0 || arr < obj)) {
                val end = trimmed.lastIndexOf(']')
                require(end > arr) { "LINUX DO did not return JSON" }
                return trimmed.substring(arr, end + 1)
            }
            val start = obj
            val end = trimmed.lastIndexOf('}')
            require(start >= 0 && end > start) { "LINUX DO did not return JSON" }
            return trimmed.substring(start, end + 1)
        }

        fun parseTagged(body: String, expectedGen: Int): String? {
            val sep = body.indexOf('|')
            if (sep <= 0) {
                return body
            }
            val gen = body.substring(0, sep).toIntOrNull() ?: return body
            if (gen != expectedGen) {
                return null
            }
            return body.substring(sep + 1)
        }
    }
}

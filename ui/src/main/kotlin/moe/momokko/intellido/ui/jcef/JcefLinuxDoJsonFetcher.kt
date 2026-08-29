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
import moe.momokko.intellido.ui.session.SignInCoordinator
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
    @Volatile
    private var lastChallengeOkAt: Long = 0

    val cefClient: JBCefClient
        get() = browser.jbCefClient

    fun invalidateOrigin() {
        originReady = false
    }

    init {
        val client = JBCefApp.getInstance().createClient()
        client.setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 16)
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
            override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    settleOrigin(originGen.get())
                }
            }

            override fun onLoadingStateChange(
                cefBrowser: CefBrowser,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean,
            ) {
                if (isLoading) {
                    return
                }
                settleOrigin(originGen.get())
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

    private fun settleOrigin(gen: Int) {
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

    override fun delete(path: String, headers: Map<String, String>): String {
        check(!ApplicationManager.getApplication().isDispatchThread) {
            "LINUX DO JCEF fetches must not run on the EDT"
        }
        gate.beginJson()
        try {
            prepareOrigin()
            val target = LinuxDoUrls.absolute(path)
            synchronized(lock) {
                throttle(JcefFetchPolicy.JSON_GAP_MS)
                return try {
                    runInPageDelete(target, headers)
                } finally {
                    lastFinishedAt = System.currentTimeMillis()
                }
            }
        } finally {
            gate.endJson()
        }
    }

    override fun clearCookies() {
        runCatching {
            org.cef.network.CefCookieManager.getGlobalManager()?.deleteCookies("", "")
        }
        originReady = false
    }

    override fun get(path: String): String {
        check(!ApplicationManager.getApplication().isDispatchThread) {
            "LINUX DO JCEF fetches must not run on the EDT"
        }
        abortMessageBusPoll()
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
            if (originReady) {
                return
            }
        }
        completeViaChallenge()
        synchronized(lock) {
            loadOrigin()
            if (originReady) {
                lastChallengeOkAt = System.currentTimeMillis()
                return
            }
            throw IllegalStateException("无法加载")
        }
    }

    private fun loadOrigin() {
        originReady = false
        originGen.incrementAndGet()
        val future = CompletableFuture<String>()
        originPending.set(future)
        val probeUrl = LinuxDoUrls.absolute(LinuxDoUrls.sessionCsrf())
        logger.info("Opening LINUX DO origin in JCEF at $probeUrl")
        ApplicationManager.getApplication().invokeLater {
            browser.loadURL(probeUrl)
        }
        try {
            future.get(JcefFetchPolicy.ORIGIN_LOAD_TIMEOUT_SEC, TimeUnit.SECONDS)
        } catch (error: Exception) {
            logger.warn("LINUX DO origin load did not finish: ${error.message}")
        } finally {
            originPending.set(null)
            originGen.incrementAndGet()
        }
        repeat(JcefFetchPolicy.ORIGIN_PROBES) { attempt ->
            if (markOriginReadyIfPossible()) {
                logger.info("LINUX DO origin ready after ${attempt + 1} JSON probe(s)")
                return
            }
            Thread.sleep(JcefFetchPolicy.EMPTY_RETRY_MS)
        }
        logger.info("LINUX DO origin is not answering JSON yet")
    }

    /**
     * Ready means the hidden page can fetch structured LINUX DO JSON, not that
     * the Ember chrome has painted. Fluxdo uses the same bar: API success.
     */
    private fun markOriginReadyIfPossible(): Boolean {
        if (originCanFetch()) {
            originReady = true
            applyDiscourseLocale()
            return true
        }
        return false
    }

    private fun originCanFetch(): Boolean {
        val body = runInPageFetch(
            LinuxDoUrls.absolute(LinuxDoUrls.sessionCsrf()),
            timeoutSec = JcefFetchPolicy.ORIGIN_PROBE_TIMEOUT_SEC,
        )
        val flat = JcefFetchPolicy.flattenJson(body)
        val extracted = runCatching { extractJson(flat) }.getOrDefault(flat)
        return CloudflareChallenge.isExpectedPayload("https://linux.do/session/csrf", extracted)
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
            if (challenge) {
                if (!retriedChallenge) {
                    logger.info("Challenge on $url; recovering origin")
                    originReady = false
                    throw ChallengeRequired()
                }
                logger.warn("Challenge HTML still on $url after the dialog")
                throw IllegalStateException("无法加载")
            }
            if (extracted.isBlank() && attempt < JcefFetchPolicy.EMPTY_RETRY) {
                logger.info("Unusable payload on $url (attempt ${attempt + 1}); retrying")
                Thread.sleep(JcefFetchPolicy.EMPTY_RETRY_MS)
            } else {
                logger.warn("Unexpected LINUX DO payload for $url (${extracted.length} chars): ${extracted.take(160)}")
                throw IllegalStateException("无法加载")
            }
        }
        throw IllegalStateException("无法加载")
    }

    private fun runInPageDelete(url: String, headers: Map<String, String>): String {
        val gen = jsGen.incrementAndGet()
        val callback = query.inject("payload")
        val target = escapeJs(url)
        val headerJs = headers.entries.joinToString(",") { (key, value) ->
            "'${escapeJs(key)}':'${escapeJs(value)}'"
        }
        val js = """
            fetch('$target',{
              method:'DELETE',
              credentials:'include',
              headers:{${JcefFetchPolicy.JSON_FETCH_HEADERS_JS}${if (headerJs.isBlank()) "" else ",$headerJs"}}
            })
              .then(function(r){return r.text();})
              .then(function(text){ var payload='$gen|'+String(text).replace(/[\r\n]+/g,' '); $callback })
              .catch(function(){ var payload='$gen|'; $callback });
        """.trimIndent()
        return awaitJs(js, 15, gen, abortBus = false)
    }

    private fun runInPageFetch(url: String, timeoutSec: Long = 15): String {
        val gen = jsGen.incrementAndGet()
        val callback = query.inject("payload")
        val target = escapeJs(url)
        val js = if (JcefFetchPolicy.isSiteJson(url)) {
            compactSiteFetchJs(gen, target, callback)
        } else {
            """
            fetch('$target',{credentials:'include',headers:{${JcefFetchPolicy.JSON_FETCH_HEADERS_JS}}})
              .then(function(r){return r.text();})
              .then(function(text){ var payload='$gen|'+String(text).replace(/[\r\n]+/g,' '); $callback })
              .catch(function(){ var payload='$gen|'; $callback });
            """.trimIndent()
        }
        val wait = if (JcefFetchPolicy.isSiteJson(url)) JcefFetchPolicy.SITE_FETCH_TIMEOUT_SEC else timeoutSec
        return awaitJs(js, wait, gen, abortBus = false)
    }

    /**
     * Full LINUX DO `/site.json` is megabytes (emoji, settings, theme). Pushing it
     * through JBCefJSQuery truncates or fails, so Home fell back to
     * `/categories.json` without trust-gated children. Slim in-page to the
     * catalog fields Fluxdo reads.
     */
    private fun compactSiteFetchJs(gen: Int, target: String, callback: String): String =
        """
        fetch('$target',{credentials:'include',headers:{${JcefFetchPolicy.JSON_FETCH_HEADERS_JS}}})
          .then(function(r){return r.json();})
          .then(function(s){
            function pick(c){
              var cf=c.custom_fields||{};
              return {
                id:c.id,
                name:c.name,
                slug:c.slug,
                color:c.color,
                icon:c.icon,
                topic_count:c.topic_count,
                read_restricted:c.read_restricted,
                parent_category_id:c.parent_category_id,
                description:c.description,
                description_text:c.description_text,
                min_trust_level:c.min_trust_level,
                minimum_trust_level:c.minimum_trust_level,
                required_minimum_trust_level:c.required_minimum_trust_level,
                custom_fields:{
                  min_trust_level:cf.min_trust_level,
                  minimum_trust_level:cf.minimum_trust_level,
                  required_trust_level:cf.required_trust_level
                }
              };
            }
            var slim={
              default_archetype:s.default_archetype,
              long_polling_base_url:s.long_polling_base_url,
              user_fields:s.user_fields||[],
              categories:(s.categories||[]).map(pick)
            };
            var payload='$gen|'+JSON.stringify(slim);
            $callback
          })
          .catch(function(){ var payload='$gen|'; $callback });
        """.trimIndent()

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
        if (SignInCoordinator.isDialogOpen()) {
            logger.info("Deferring challenge dialog; sign-in already shows LINUX DO")
            SignInCoordinator.waitWhileOpen()
            originReady = false
            return
        }
        val now = System.currentTimeMillis()
        if (!JcefChallengePolicy.shouldOpenDialog(false, lastChallengeOkAt, now)) {
            logger.info("Skipping challenge dialog; recently completed")
            originReady = false
            return
        }
        val locale = runCatching { service<IntelliDoRuntime>().locale }.getOrDefault(java.util.Locale.SIMPLIFIED_CHINESE)
        logger.info("Opening Cloudflare challenge dialog")
        try {
            JcefChallengeDialog.awaitPassed(browser.jbCefClient, locale)
            JcefLinuxDoCookies.flush()
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

package com.hikari.app

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class EmbedControls internal constructor(private val web: WebView) {
    fun play() = evaluate("playVideo()", requireVisible = true)
    fun pause() = evaluate("pauseVideo()")
    fun seek(seconds: Float) = evaluate("seekTo($seconds,true)")
    fun speed(value: Float) = evaluate("setPlaybackRate($value)")
    private fun evaluate(script: String, requireVisible: Boolean = false) {
        web.post {
            web.evaluateJavascript(
                "player&&${if (requireVisible) "Hikari.canPlay()&&" else ""}player.$script",
                null
            )
        }
    }
}

/** Runs inside the iframe: cropping alone cannot hide YouTube's central bezel animation. */
internal fun embedChromeScript(custom: Boolean) = """
(function(){
  var custom=$custom;
  function apply(){if(document.documentElement)document.documentElement.classList.toggle('hikari-custom',custom);}
  var style=document.createElement('style');style.textContent=
    '.hikari-custom .ytPlayerControlsContainerHost{display:none!important}.hikari-custom .ytp-bezel,.hikari-custom .ytp-bezel-text-wrapper,.hikari-custom .ytp-large-play-button,.hikari-custom .ytp-pause-overlay,.hikari-custom .ytp-chrome-top,.hikari-custom .ytp-chrome-bottom,.hikari-custom .ytp-gradient-top,.hikari-custom .ytp-gradient-bottom{opacity:0!important;visibility:hidden!important;pointer-events:none!important}';
  var observer=new MutationObserver(function(){if(document.documentElement){document.documentElement.appendChild(style);apply();observer.disconnect();}});
  if(document.documentElement){document.documentElement.appendChild(style);apply();}else observer.observe(document,{childList:true,subtree:true});
  function size(){var v=document.querySelector('video');if(v&&v.videoWidth&&v.videoHeight)parent.postMessage({type:'hikari-size',width:v.videoWidth,height:v.videoHeight},'https://com.hikari.app');}
  document.addEventListener('loadedmetadata',size,true);document.addEventListener('resize',size,true);setInterval(size,1500);
  addEventListener('message',function(e){if(e.origin==='https://com.hikari.app'&&e.data&&e.data.type==='hikari-ui'){custom=!e.data.visible;apply();}});
})();
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun EmbedPlayback(
    video: Video, showYoutubeUi: Boolean = false, enhancedColors: Boolean = false,
    onRestricted: () -> Unit, onError: (Int) -> Unit, onControlsReady: (EmbedControls) -> Unit,
    onStatus: (Boolean, Float, Float) -> Unit, onMetadata: (String, String) -> Unit,
    onAspectRatio: (Float) -> Unit = {}, onEnded: () -> Unit = {}, onBuffering: (Boolean) -> Unit = {}
) {
    val restricted by rememberUpdatedState(onRestricted)
    val error by rememberUpdatedState(onError)
    val controls by rememberUpdatedState(onControlsReady)
    val statusCallback by rememberUpdatedState(onStatus)
    val metadataCallback by rememberUpdatedState(onMetadata)
    val ratioCallback by rememberUpdatedState(onAspectRatio)
    val ended by rememberUpdatedState(onEnded)
    val buffering by rememberUpdatedState(onBuffering)
    val youtubeUi by rememberUpdatedState(showYoutubeUi)
    key(video.id) {
        val applied = remember { arrayOfNulls<String>(1) }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    tag = true
                    setBackgroundColor(android.graphics.Color.BLACK)
                    // AndroidView does not clip the WebView's compositor to its Compose bounds.
                    outlineProvider = android.view.ViewOutlineProvider.BOUNDS
                    clipToOutline = true
                    layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.setSupportMultipleWindows(false)
                    webChromeClient = WebChromeClient()
                    val canHideChrome = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) WebViewCompat.addDocumentStartJavaScript(
                        this,
                        embedChromeScript(!showYoutubeUi),
                        setOf("https://www.youtube.com", "https://www.youtube-nocookie.com")
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            view.evaluateJavascript("setHikariUi($youtubeUi)", null)
                        }
                    }
                    addJavascriptInterface(object {
                        // Read live visibility even if the iframe becomes ready after onStop.
                        @JavascriptInterface
                        fun canPlay(): Boolean = PlaybackPolicy.playerVisible.value

                        @JavascriptInterface
                        fun dimensions(width: Float, height: Float) {
                            if (width > 0 && height > 0) post { if (tag == true) ratioCallback(width / height) }
                        }

                        @JavascriptInterface
                        fun metadata(title: String, author: String) {
                            post { if (tag == true) metadataCallback(title, author) }
                        }

                        @JavascriptInterface
                        fun ready() {
                            post { if (tag == true) controls(EmbedControls(this@apply)) }
                        }

                        @JavascriptInterface
                        fun status(playing: Boolean, current: Float, duration: Float) {
                            post { if (tag == true) statusCallback(playing, current, duration) }
                        }

                        @JavascriptInterface
                        fun state(value: Int) {
                            post {
                                if (tag == true) {
                                    buffering(value == 3); if (value == 0) ended()
                                }
                            }
                        }

                        @JavascriptInterface
                        fun failed(code: Int) {
                            post {
                                if (tag == true) {
                                    if (code == 101 || code == 150) restricted() else error(code)
                                }
                            }
                        }
                    }, "Hikari")
                    loadDataWithBaseURL(
                        "https://${context.packageName}/", """
<!doctype html><html><head><meta name="referrer" content="strict-origin-when-cross-origin"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><style>
*{margin:0;padding:0}html,body,#clip{width:100%;height:100%;overflow:hidden;background:#000}
#yt{position:absolute;left:0;top:0;width:100%;height:100%;pointer-events:none}.youtube-ui #yt{pointer-events:auto}
.enhanced #clip{filter:contrast(1.1) saturate(1.15) brightness(1.02)}</style></head>
<body class="${if (showYoutubeUi) "youtube-ui" else ""} ${if (enhancedColors) "enhanced" else ""}"><div id="clip"><div id="yt"></div></div><script>
addEventListener('message',function(e){if(e.origin==='https://www.youtube.com'&&e.data&&e.data.type==='hikari-size')Hikari.dimensions(Number(e.data.width),Number(e.data.height));});
var player,visible=$showYoutubeUi,canHide=$canHideChrome,ready=false,ticker;
function notifyFrame(){if(player&&player.getIframe)player.getIframe().contentWindow.postMessage({type:'hikari-ui',visible:visible},'https://www.youtube.com');}
function setHikariUi(show){var changed=visible!==show;visible=show;document.body.classList.toggle('youtube-ui',show);
 if(!canHide&&changed&&ready){var time=player.getCurrentTime(),playing=player.getPlayerState()===1;player.destroy();var el=document.createElement('div');el.id='yt';document.getElementById('clip').appendChild(el);createPlayer(time,playing);}else notifyFrame();}
function createPlayer(start,autoplay){ready=false;clearInterval(ticker);player=new YT.Player('yt',{videoId:'${video.id}',width:'100%',height:'100%',playerVars:{autoplay:0,controls:canHide||visible?1:0,disablekb:1,fs:0,rel:0,playsinline:1,enablejsapi:1,origin:'https://${context.packageName}'},events:{
 onReady:function(){ready=true;notifyFrame();Hikari.ready();var d=player.getVideoData();Hikari.metadata(d.title||'',d.author||'');if(start)player.seekTo(start,true);if(autoplay&&Hikari.canPlay())player.playVideo();else player.pauseVideo();ticker=setInterval(function(){Hikari.status(player.getPlayerState()===1,player.getCurrentTime()||0,player.getDuration()||0);},350);},
 onStateChange:function(e){if(e.data===1&&!Hikari.canPlay()){player.pauseVideo();return;}notifyFrame();Hikari.state(e.data);},onError:function(e){Hikari.failed(e.data);}}});}
function onYouTubeIframeAPIReady(){createPlayer(0,true);}
var s=document.createElement('script');s.src='https://www.youtube.com/iframe_api';document.head.appendChild(s);
</script></body></html>
""".trimIndent(), "text/html", "UTF-8", null
                    )
                }
            },
            update = { web ->
                web.importantForAccessibility =
                    if (showYoutubeUi) android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                val value = "$showYoutubeUi:$enhancedColors"
                if (applied[0] != value) {
                    applied[0] = value; web.evaluateJavascript(
                        "if(typeof setHikariUi==='function')setHikariUi($showYoutubeUi);document.body.classList.toggle('enhanced',$enhancedColors)",
                        null
                    )
                }
            },
            onRelease = { web ->
                web.tag = false; web.evaluateJavascript(
                "clearInterval(ticker);if(player)player.destroy()",
                null
            ); web.removeJavascriptInterface("Hikari"); web.stopLoading(); web.destroy()
            })
    }
}

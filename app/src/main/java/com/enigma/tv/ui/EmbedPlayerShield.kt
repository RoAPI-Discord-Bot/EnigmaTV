package com.enigma.tv.ui

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import java.lang.ref.WeakReference

/**
 * In-page shield for embed players: strips click-hijack overlays (especially on pause),
 * blocks window.open popups, and keeps HTML5 video unmuted.
 */
object EmbedPlayerShield {

    private const val SHIELD_JS = """
(function() {
  if (window.__enigmaShieldV2) return;
  window.__enigmaShieldV2 = true;

  var AD_HOST_RE = /doubleclick|googlesyndication|popads|propeller|adsterra|exoclick|clickadu|outbrain|taboola|mgid|revcontent|chaturbate|stripchat/i;

  function isAdUrl(href) {
    if (!href || href.indexOf('http') !== 0) return false;
    try { return AD_HOST_RE.test(href); } catch (e) { return false; }
  }

  function isPlayerNode(el) {
    if (!el || !el.tagName) return false;
    var t = el.tagName.toLowerCase();
    if (t === 'video' || t === 'audio') return true;
    if (t === 'button' || t === 'input' || t === 'select') return true;
    var cls = (el.className && el.className.toString()) || '';
    var id = el.id || '';
    var blob = (cls + ' ' + id).toLowerCase();
    if (/play|pause|volume|mute|seek|progress|control|player|plyr|vjs|jw|video|subtitle|fullscreen|settings|quality|speed/.test(blob)) return true;
    if (el.closest) {
      if (el.closest('video, audio, .plyr, .video-js, .vjs-control-bar, .jwplayer, .jw-controls, [class*="player"], [class*="controls"], [id*="player"]')) return true;
    }
    return false;
  }

  function isHijackOverlay(el) {
    if (!el || el.nodeType !== 1) return false;
    if (isPlayerNode(el)) return false;
    if (el.querySelector && el.querySelector('video')) return false;
    var t = el.tagName.toLowerCase();
    if (t === 'video' || t === 'audio') return false;

    var s = window.getComputedStyle(el);
    if (!s || s.display === 'none' || s.visibility === 'hidden') return false;

    var pos = s.position;
    var z = parseInt(s.zIndex || '0', 10) || 0;
    var pe = s.pointerEvents;
    if (pe === 'none') return false;

    var r = el.getBoundingClientRect();
    var vw = window.innerWidth || 1;
    var vh = window.innerHeight || 1;
    var coverW = r.width / vw;
    var coverH = r.height / vh;
    var large = coverW > 0.45 && coverH > 0.45;
    var opacity = parseFloat(s.opacity || '1');

    var cls = ((el.className && el.className.toString()) + ' ' + (el.id || '')).toLowerCase();
    if (/ad-|ads-|advert|banner|popup|pop-under|clickadu|propeller|overlay-ad|sponsor/.test(cls)) return true;

    if ((pos === 'fixed' || pos === 'absolute') && large && (z > 50 || opacity < 0.25)) return true;
    if ((pos === 'fixed' || pos === 'absolute') && coverW > 0.9 && coverH > 0.9 && z > 10) return true;
    if (t === 'iframe' && large && z > 5) {
      try {
        var src = (el.src || '').toLowerCase();
        if (!src || AD_HOST_RE.test(src)) return true;
        if (src.indexOf('player') < 0 && src.indexOf('embed') < 0 && src.indexOf('video') < 0) return true;
      } catch (e) { return true; }
    }
    if ((t === 'a' || t === 'div' || t === 'ins') && large && (el.onclick || el.getAttribute('onclick'))) return true;
    return false;
  }

  function neuter(el) {
    try {
      el.style.setProperty('pointer-events', 'none', 'important');
      el.style.setProperty('display', 'none', 'important');
      el.style.setProperty('visibility', 'hidden', 'important');
      el.removeAttribute('onclick');
      el.onclick = null;
    } catch (e) {}
  }

  function unmuteVideos() {
    try {
      document.querySelectorAll('video, audio').forEach(function(v) {
        v.muted = false;
        v.defaultMuted = false;
        if (typeof v.volume === 'number' && v.volume < 0.01) v.volume = 1;
      });
    } catch (e) {}
  }

  function protectPlayers() {
    try {
      document.querySelectorAll('video, .plyr, .video-js, .jwplayer, [class*="player"]').forEach(function(el) {
        el.style.setProperty('pointer-events', 'auto', 'important');
        if (el.tagName && el.tagName.toLowerCase() === 'video') {
          el.style.setProperty('z-index', '2147483646', 'important');
        }
      });
    } catch (e) {}
  }

  function sweep() {
    try {
      var nodes = document.querySelectorAll('div, a, iframe, ins, span, section, aside');
      for (var i = 0; i < nodes.length; i++) {
        if (isHijackOverlay(nodes[i])) neuter(nodes[i]);
      }
      protectPlayers();
      unmuteVideos();
    } catch (e) {}
  }

  window.open = function() { return null; };
  try {
    Object.defineProperty(window, 'open', { value: function() { return null; }, writable: false });
  } catch (e) {}

  sweep();
  setInterval(sweep, 1200);

  document.addEventListener('play', function(e) {
    if (e.target && e.target.tagName === 'VIDEO') {
      e.target.muted = false;
      e.target.volume = 1;
      setTimeout(sweep, 0);
    }
  }, true);

  document.addEventListener('pause', function(e) {
    if (e.target && e.target.tagName === 'VIDEO') {
      setTimeout(sweep, 0);
      setTimeout(sweep, 100);
      setTimeout(sweep, 400);
    }
  }, true);

  document.addEventListener('click', function(e) {
    var el = e.target;
    var depth = 0;
    while (el && depth < 12) {
      if (isHijackOverlay(el)) {
        e.preventDefault();
        e.stopImmediatePropagation();
        neuter(el);
        sweep();
        return;
      }
      if (isPlayerNode(el)) return;
      el = el.parentElement;
      depth++;
    }
    try {
      var a = e.target && e.target.closest ? e.target.closest('a') : null;
      if (a && a.href && isAdUrl(a.href)) {
        e.preventDefault();
        e.stopImmediatePropagation();
      }
    } catch (err) {}
  }, true);

  try {
    var obs = new MutationObserver(function() { sweep(); });
    var root = document.body || document.documentElement;
    if (root) obs.observe(root, { childList: true, subtree: true, attributes: true, attributeFilter: ['style', 'class'] });
  } catch (e) {}
})();
"""

    private val mainHandler = Handler(Looper.getMainLooper())
    private var periodicRunnable: Runnable? = null
    private var webViewRef: WeakReference<WebView>? = null

    fun apply(webView: WebView?) {
        webView?.evaluateJavascript(SHIELD_JS, null)
    }

    fun startPeriodic(webView: WebView) {
        stopPeriodic()
        webViewRef = WeakReference(webView)
        val runnable = object : Runnable {
            override fun run() {
                val wv = webViewRef?.get() ?: return
                apply(wv)
                mainHandler.postDelayed(this, 1500L)
            }
        }
        periodicRunnable = runnable
        mainHandler.post(runnable)
    }

    fun stopPeriodic() {
        periodicRunnable?.let { mainHandler.removeCallbacks(it) }
        periodicRunnable = null
        webViewRef = null
    }
}

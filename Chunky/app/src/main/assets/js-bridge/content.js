(function() {
  if (window.top !== window.self) {
    return;
  }

  function connect() {
    var port = browser.runtime.connectNative("browser");

    port.onMessage.addListener(function(msg) {
      if (!msg || !msg.__tvEval) return;
      var response = { id: msg.id };
      try {
        response.result = eval(msg.code);
      } catch (err) {
        response.result = null;
      }
      try { port.postMessage(response); } catch (e) {}
    });

    port.onDisconnect.addListener(function() {
      setTimeout(connect, 500);
    });

    window.__tvPort = port;
  }

  connect();
})();
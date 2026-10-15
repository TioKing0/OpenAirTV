var LegacyHub = (function () {
  var count = 0;
  var last = "Aguardando controle";
  function render() {
    var node = document.getElementById("status");
    if (node) node.innerHTML = "Versão {{VERSION}}<br/>Teclas recebidas: " + count + "<br/>Último evento: " + last;
  }
  function onKey(e) {
    e = e || window.event;
    var key = e.keyCode || e.which;
    count += 1;
    last = "Código " + key;
    render();
    if (e.preventDefault) e.preventDefault();
    e.returnValue = false;
    return false;
  }
  return {
    start: function () {
      document.onkeydown = onKey;
      if (window.widgetAPI && widgetAPI.sendReadyEvent) widgetAPI.sendReadyEvent();
      render();
    }
  };
})();

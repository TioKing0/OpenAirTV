(function() {
  var items = [document.getElementById('tile1'),document.getElementById('tile2'),document.getElementById('tile3')];
  var selected = 0, presses = 0, message = document.getElementById('message');
  function select(n) {
    if (n<0) n=items.length-1;
    if (n>=items.length) n=0;
    selected=n;
    for (var i=0;i<items.length;i++) items[i].className='tile'+(i===selected?' selected':'');
    if (items[n].focus) items[n].focus();
  }
  function choose() {
    if (selected===0) message.innerHTML='Servidor Android conectado pelo navegador. Instalação nativa: NÃO realizada.';
    if (selected===1) message.innerHTML='Teclas recebidas: '+presses+'. Controle remoto detectado pelo navegador.';
    if (selected===2) message.innerHTML='Modo navegador compatível com Wi-Fi, sem root, PC ou pendrive.';
  }
  document.onkeydown=function(e) {
    e=e||window.event;
    presses++;
    var key=e.keyCode||e.which;
    if (key===37||key===38) select(selected-1);
    else if (key===39||key===40) select(selected+1);
    else if (key===13) choose();
    else message.innerHTML='Tecla: '+key+' • eventos: '+presses;
  };
  for (var i=0;i<items.length;i++) {
    (function(index){ items[index].onclick=function(){ select(index); choose(); }; })(i);
  }
  select(0);
})();
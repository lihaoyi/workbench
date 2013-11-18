var socket = (function(){
    var open = false
    var shadowBody = null
    window.onload = function(){
        console.log("Loaded!")
        shadowBody = document.body.cloneNode()
        console.log("Loaded!X")
    }

    var start = function(){
        socket = new WebSocket("ws://<host>:<port>/")
        socket.onopen = function(event){
            open = true
            console.log("scala-js-workbench connected")
        }
        socket.onmessage = function(event){
            var data = JSON.parse(event.data)

            if (data[0] == "reload") {
                console.log("Reloading page...")
                location.reload()
            }
            if (data[0] == "clear"){
                document.body = shadowBody.cloneNode()
                for(var i = 0; i < 99999; i++){
                    clearTimeout(i)
                    clearInterval(i)
                }
            }
            if (data[0] == "run"){
                var tag = document.createElement("script")
                var loaded = false
                tag.setAttribute("src", data[1])
                if (data[2]){
                    tag.onreadystatechange = tag.onload = function() {
                        if (!loaded)  eval(data[2]);
                        loaded = true;
                    };
                }
                document.head.appendChild(tag)
            }
            if (data[0] == "eval"){
                eval(data[1])
            }
            if (data[0] == "print") console[data[1]](data[2])
        }
        socket.onclose = function(event){
            if (open) console.log("scala-js-workbench disconnected")
            open = false
            setTimeout(function(){start()}, 1000)
        }
    }
    start()
    return socket
})()

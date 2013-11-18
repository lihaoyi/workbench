var socket = (function(){
    var open = false
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
